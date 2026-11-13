package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.OtpRequest
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.model.normalizeDeviceLocale
import com.muhabbet.auth.domain.port.`in`.AuthResult
import com.muhabbet.auth.domain.port.`in`.FirebaseVerifyUseCase
import com.muhabbet.auth.domain.port.`in`.OtpResult
import com.muhabbet.auth.domain.port.`in`.RegisterPushTokenUseCase
import com.muhabbet.auth.domain.port.`in`.RequestOtpUseCase
import com.muhabbet.auth.domain.port.`in`.VerifyOtpUseCase
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.FirebaseTokenVerifier
import com.muhabbet.auth.domain.port.out.OtpChannel
import com.muhabbet.auth.domain.port.out.OtpQuotaPort
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.OtpVerifier
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.TwoStepAttemptRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Phone sign-in: OTP request and verify, Firebase verify, and push-token registration.
 *
 * **Transaction boundaries (#886).** None of the sign-in methods is `@Transactional`. Each one
 * talks to an outside service — the SMS provider, Twilio Verify, Firebase — that can take seconds,
 * and a method-wide transaction held a pool connection (two, during a verify) for all of it, so a
 * burst of logins against a slow provider could drain the twenty-connection pool and take
 * messaging down with it. The database work now runs in short [TransactionRunner] blocks on either
 * side of the provider call, never around it.
 *
 * Refresh and logout live in [SessionService]; the device/token tail of a sign-in in
 * [SignInSessionIssuer].
 */
open class AuthService(
    private val userRepository: UserRepository,
    private val otpRepository: OtpRepository,
    private val deviceRepository: DeviceRepository,
    private val phoneHashRepository: PhoneHashRepository,
    /** How codes reach a phone; exactly one strategy by construction. See [OtpChannel]. */
    private val otpChannel: OtpChannel,
    private val passwordEncoder: PasswordEncoder,
    private val sessionIssuer: SignInSessionIssuer,
    private val transactions: TransactionRunner,
    private val otpLength: Int = 6,
    private val otpExpirySeconds: Int = 300,
    private val otpCooldownSeconds: Int = 60,
    private val otpMaxAttempts: Int = 5,
    private val mockEnabled: Boolean = false,
    /**
     * Numbers that bypass the SMS provider: the code is generated here and written to the log.
     * See [com.muhabbet.shared.config.OtpProperties.testNumbers] for why this exists and why it is
     * not a fixed code. Empty by default.
     */
    private val testNumbers: Set<String> = emptySet(),
    /**
     * Budget for verifications *started*, because each one is billed. Required rather than
     * defaulted: a no-op default would silently remove the only ceiling on the provider bill, which
     * is the failure #440 is about.
     */
    private val otpQuotaPort: OtpQuotaPort,
    /**
     * Verifies the Firebase ID token on the other sign-in path. Behind a port so the two-step gate
     * on that path can be exercised at all — see [FirebaseTokenVerifier].
     */
    private val firebaseTokenVerifier: FirebaseTokenVerifier,
    /**
     * The guess budget for the second factor (#566). Required rather than defaulted for the same
     * reason [otpQuotaPort] is: a no-op default would leave a six-digit PIN with no ceiling, and the
     * whole point of the gate is that guessing it must be expensive.
     */
    private val twoStepAttemptRepository: TwoStepAttemptRepository,
    private val twoStepMaxAttempts: Int = 5,
    private val twoStepLockSeconds: Long = 900
) : RequestOtpUseCase, VerifyOtpUseCase, RegisterPushTokenUseCase, FirebaseVerifyUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    init {
        // A test number skips the SMS provider entirely, so anyone able to add one to the config
        // could turn a real person's number into an account they can sign into by reading the log.
        // Restricting the list to +90500 — a range BTK has not allocated, so no handset can ever
        // hold one — makes that impossible rather than merely discouraged. Failing at startup is
        // the point: a typo must stop the deployment, not quietly widen the door.
        testNumbers.forEach { number ->
            require(number.startsWith(TEST_NUMBER_PREFIX)) {
                "muhabbet.otp.test-numbers may only contain unallocated $TEST_NUMBER_PREFIX numbers, " +
                    "so a real subscriber can never be listed; got ${number.takeLast(4)}"
            }
            require(ValidationRules.isValidTurkishPhone(number)) {
                "muhabbet.otp.test-numbers contains a malformed number ending ${number.takeLast(4)}"
            }
        }
        require(testNumbers.size <= MAX_TEST_NUMBERS) {
            "muhabbet.otp.test-numbers holds ${testNumbers.size} entries; at most $MAX_TEST_NUMBERS " +
                "is a test fleet, more is a bypass"
        }
        if (testNumbers.isNotEmpty()) {
            log.warn(
                "OTP bypass active for {} test number(s); their codes are written to this log and " +
                    "never sent by SMS",
                testNumbers.size
            )
        }
    }

    private fun isTestNumber(phoneNumber: String) = phoneNumber in testNumbers

    /** How this one request's code will be produced and delivered; decided once, used twice. */
    private sealed interface Delivery {
        /** The code exists here; null when the provider owns it. */
        val localCode: String?

        /** A test number: generated here, written to the log, sent nowhere. */
        data class LoggedTestCode(override val localCode: String) : Delivery
        data class LocalSms(val sender: OtpSender, override val localCode: String) : Delivery
        data class ProviderOwned(val verifier: OtpVerifier) : Delivery {
            override val localCode: String? get() = null
        }
    }

    private fun deliveryFor(phoneNumber: String): Delivery = when {
        // A test number always takes the local path, whichever provider is configured: the code is
        // generated here, hashed here, and checked here.
        isTestNumber(phoneNumber) -> Delivery.LoggedTestCode(generateOtp())
        else -> when (val channel = otpChannel) {
            is OtpChannel.LocalCode -> Delivery.LocalSms(channel.sender, generateOtp())
            is OtpChannel.ExternalVerifier -> Delivery.ProviderOwned(channel.verifier)
        }
    }

    private sealed interface Issue {
        data class Stored(val request: OtpRequest) : Issue
        data object CoolingDown : Issue
    }

    override fun requestOtp(phoneNumber: String): OtpResult {
        if (!ValidationRules.isValidTurkishPhone(phoneNumber)) {
            throw BusinessException(ErrorCode.AUTH_INVALID_PHONE)
        }

        val delivery = deliveryFor(phoneNumber)
        // With an external verifier the code never exists on this side, so there is nothing to
        // hash. The record is still written — cooldown, expiry and attempt limits are ours either
        // way. The stored hash is a sentinel that no input can match, so a mis-wired branch fails
        // closed rather than accepting an unchecked code.
        val otpHash = delivery.localCode?.let { passwordEncoder.encode(it) } ?: EXTERNALLY_VERIFIED
        val candidate = OtpRequest(
            phoneNumber = phoneNumber,
            otpHash = otpHash,
            expiresAt = Instant.now().plusSeconds(otpExpirySeconds.toLong())
        )

        // 1. A short transaction decides the cooldown and stores the request in one serialised
        //    step, so two parallel taps produce one stored request and one SMS, not two.
        val issue = transactions.inTransaction {
            otpRepository.createUnlessCoolingDown(candidate, Duration.ofSeconds(otpCooldownSeconds.toLong()))
                ?.let { Issue.Stored(it) }
                ?: Issue.CoolingDown
        }
        val stored = when (issue) {
            is Issue.Stored -> issue.request
            Issue.CoolingDown -> throw BusinessException(ErrorCode.AUTH_OTP_COOLDOWN)
        }

        // 2. The budget. Checked after the cooldown so a client hammering one number spends its own
        //    cooldown rather than the deployment's hourly budget. Test numbers are exempt: they
        //    never reach the provider, so they cost nothing and must not exhaust the budget real
        //    users share. A refusal retires the stored request, which was never delivered.
        val billable = delivery !is Delivery.LoggedTestCode
        if (billable && !otpQuotaPort.tryConsume(phoneNumber)) {
            otpRepository.retire(stored)
            throw BusinessException(ErrorCode.AUTH_OTP_RATE_LIMIT)
        }

        // 3. The provider call, outside any transaction. If it fails, nothing was delivered: the
        //    request is retired (no unusable active OTP, no cooldown for a code that never arrived)
        //    and the budget unit is handed back.
        try {
            deliver(phoneNumber, delivery)
        } catch (e: RuntimeException) {
            otpRepository.retire(stored)
            if (billable) otpQuotaPort.refund(phoneNumber)
            log.warn("OTP delivery failed for phone={}; request retired and budget refunded", phoneNumber.takeLast(4))
            throw e
        }
        log.info("OTP requested for phone={}", phoneNumber.takeLast(4))

        return OtpResult(
            ttlSeconds = otpExpirySeconds,
            retryAfterSeconds = otpCooldownSeconds,
            mockCode = if (mockEnabled) delivery.localCode else null
        )
    }

    private fun deliver(phoneNumber: String, delivery: Delivery) {
        when (delivery) {
            // Nothing is sent anywhere. The code reaches the log and nowhere else, so signing in as
            // a test account requires server access rather than a handset.
            is Delivery.LoggedTestCode ->
                log.warn("TEST NUMBER {} — OTP not sent, code is {}", phoneNumber, delivery.localCode)
            is Delivery.LocalSms -> delivery.sender.send(phoneNumber, delivery.localCode)
            is Delivery.ProviderOwned -> delivery.verifier.start(phoneNumber)
        }
    }

    override fun verifyOtp(
        phoneNumber: String,
        otp: String,
        deviceName: String,
        platform: String,
        twoStepPin: String?,
        installationId: String?
    ): AuthResult {
        // Validated before an attempt is spent: a malformed client request is not a guess.
        val signIn = DeviceSignIn.parse(deviceName, platform, installationId)

        val activeOtp = otpRepository.findActiveByPhoneNumber(phoneNumber)
            ?: throw BusinessException(ErrorCode.AUTH_OTP_EXPIRED)

        // Claiming an attempt and enforcing the limit are the same statement, so concurrent verifies
        // cannot each read an under-limit count and all be granted a guess. Commits on its own.
        if (!otpRepository.claimAttempt(activeOtp, otpMaxAttempts)) {
            throw BusinessException(ErrorCode.AUTH_OTP_MAX_ATTEMPTS)
        }

        // Outside any transaction: with Twilio Verify this is an HTTP round trip.
        if (!codeMatches(phoneNumber, otp, activeOtp)) {
            throw BusinessException(ErrorCode.AUTH_OTP_INVALID)
        }

        // The code is right. Knowing it is now only the first of two things the account may require.
        // This read is also the one signInAs uses below, so a sign-in is one query, not two.
        val existing = userRepository.findByPhoneNumber(phoneNumber)
        if (existing != null && hasTwoStep(existing)) {
            // Hand the attempt back before refusing. The code was not guessed wrong — it was
            // correct, and the client is about to present it again alongside the PIN. Charging both
            // halves of one sign-in would spend the SMS budget on the PIN's mistakes and report them
            // as "too many attempts" on the code (#688's shape). The PIN's own counter governs from
            // here.
            otpRepository.refundAttempt(activeOtp)
            requireTwoStepPin(existing, twoStepPin)
        }

        return transactions.inTransaction {
            // Conditional, so two concurrent verifies of one correct code cannot both sign in.
            if (!otpRepository.markVerified(activeOtp)) {
                throw BusinessException(ErrorCode.AUTH_OTP_EXPIRED)
            }
            signInAs(phoneNumber, existing, signIn, "OTP verified")
        }
    }

    /**
     * Must mirror [deliveryFor] exactly. Asking the verifier about a test number would always fail —
     * it was never told to start one — and comparing a hash for a provider-owned code would always
     * fail too, since the stored value is the [EXTERNALLY_VERIFIED] sentinel.
     */
    private fun codeMatches(phoneNumber: String, otp: String, activeOtp: OtpRequest): Boolean {
        if (isTestNumber(phoneNumber)) return passwordEncoder.matches(otp, activeOtp.otpHash)
        return when (val channel = otpChannel) {
            is OtpChannel.LocalCode -> passwordEncoder.matches(otp, activeOtp.otpHash)
            is OtpChannel.ExternalVerifier -> channel.verifier.check(phoneNumber, otp)
        }
    }

    override fun verifyFirebaseToken(
        idToken: String,
        deviceName: String,
        platform: String,
        twoStepPin: String?,
        installationId: String?
    ): AuthResult {
        val signIn = DeviceSignIn.parse(deviceName, platform, installationId)

        // Outside any transaction: the Admin SDK may fetch Google's signing certificates.
        val phoneNumber = try {
            firebaseTokenVerifier.phoneNumberOf(idToken)
        } catch (e: IllegalArgumentException) {
            log.warn("Firebase token verification failed: {}", e.message)
            throw BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "Firebase token geçersiz")
        } ?: throw BusinessException(ErrorCode.AUTH_INVALID_PHONE, "Telefon numarası bulunamadı")

        if (!ValidationRules.isValidTurkishPhone(phoneNumber)) {
            throw BusinessException(ErrorCode.AUTH_INVALID_PHONE)
        }

        // Same gate as the OTP path, and it has to be here rather than in signInAs: only the two
        // entry points know which factor they have just checked, and putting it in the shared tail
        // would also gate the new-user creation branch, where there is no PIN to know.
        val existing = userRepository.findByPhoneNumber(phoneNumber)
        if (existing != null && hasTwoStep(existing)) {
            requireTwoStepPin(existing, twoStepPin)
        }

        return transactions.inTransaction { signInAs(phoneNumber, existing, signIn, "Firebase verified") }
    }

    /** Two-step is on only when both halves are present; either alone is a half-written row. */
    private fun hasTwoStep(user: User): Boolean =
        user.twoStepEnabledAt != null && user.twoStepPinHash != null

    /**
     * The gate itself: past this point the caller has proved both factors, or has not got past it.
     *
     * Order matters. The lockout is claimed **before** the PIN is compared, so a locked account
     * answers `AUTH_2FA_LOCKED` without the hash being consulted at all — otherwise the lock would
     * be a message rather than a control, and a client that ignored it would keep guessing.
     *
     * A missing PIN is not a failed guess and costs nothing: the client cannot know an account has a
     * second factor until it is told, and charging it for asking would mean every sign-in started
     * one attempt down.
     */
    private fun requireTwoStepPin(user: User, pin: String?) {
        twoStepRejection(user, pin)?.let { throw BusinessException(it) }
        // A correct PIN ends the window, so five typos spread over a year never add up to a lockout.
        twoStepAttemptRepository.clear(user.id)
    }

    /**
     * Why this sign-in may not proceed, or null when it may. Split out from [requireTwoStepPin] so
     * the decision is a series of guards with one exit rather than four scattered throws.
     */
    private fun twoStepRejection(user: User, pin: String?): ErrorCode? {
        val storedHash = user.twoStepPinHash ?: return ErrorCode.AUTH_2FA_NOT_ENABLED
        if (pin == null) {
            log.info("Two-step PIN required: userId={}", user.id)
            return ErrorCode.AUTH_2FA_PIN_REQUIRED
        }
        val granted = twoStepAttemptRepository.claimAttempt(
            userId = user.id,
            maxAttempts = twoStepMaxAttempts,
            lockFor = Duration.ofSeconds(twoStepLockSeconds)
        )
        if (!granted) {
            log.warn("Two-step PIN locked out: userId={}", user.id)
            return ErrorCode.AUTH_2FA_LOCKED
        }
        if (!passwordEncoder.matches(pin, storedHash)) {
            log.warn("Two-step PIN rejected: userId={}", user.id)
            return ErrorCode.AUTH_2FA_PIN_INVALID
        }
        return null
    }

    /**
     * The shared tail of both sign-in paths, inside their transaction.
     *
     * @param existingUser the account both entry points have already read to decide the two-step
     *   gate. Passed in rather than read again: two reads of the same row would be one query more
     *   than the sign-in needs, and would let the gate and the session be decided about *different*
     *   states of the account.
     */
    private fun signInAs(
        phoneNumber: String,
        existingUser: User?,
        signIn: DeviceSignIn,
        logPrefix: String
    ): AuthResult {
        // A suspended account would be handed tokens that every request and every refresh then
        // refuses (#880) — a sign-in that only looks like one. Refuse it here, plainly.
        if (existingUser != null && existingUser.status != UserStatus.ACTIVE) {
            throw BusinessException(ErrorCode.AUTH_ACCOUNT_SUSPENDED)
        }
        val user = existingUser ?: createUser(phoneNumber)
        val result = sessionIssuer.issue(user, signIn, isNewUser = existingUser == null)
        log.info("{}: userId={}, deviceId={}, isNewUser={}", logPrefix, user.id, result.deviceId, result.isNewUser)
        return result
    }

    private fun createUser(phoneNumber: String): User {
        val user = userRepository.save(
            User(id = UUID.randomUUID(), phoneNumber = phoneNumber, status = UserStatus.ACTIVE)
        )
        // Store phone hash for contact sync
        phoneHashRepository.save(user.id, sha256(phoneNumber))
        log.info("New user created: userId={}", user.id)
        return user
    }

    @Transactional
    override fun registerPushToken(userId: UUID, deviceId: UUID, pushToken: String, locale: String?) {
        val devices = deviceRepository.findByUserId(userId)
        val device = devices.find { it.id == deviceId }
            ?: throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Cihaz bulunamadı")
        // An unusable or absent tag keeps what the device registered last rather than clearing it:
        // the token rotation callback on the client has no language to report, and losing the
        // language every time Firebase rotates a token would put the device back on the fallback.
        val resolvedLocale = normalizeDeviceLocale(locale) ?: device.locale
        deviceRepository.save(device.copy(pushToken = pushToken, locale = resolvedLocale))
        log.info("Push token registered: userId={}, deviceId={}, locale={}", userId, deviceId, resolvedLocale)
    }

    private fun generateOtp(): String {
        val bound = Math.pow(10.0, otpLength.toDouble()).toInt()
        val code = secureRandom.nextInt(bound)
        return code.toString().padStart(otpLength, '0')
    }

    companion object {
        /**
         * Stored in place of a hash when an external verifier owns the code. BCrypt never produces
         * this, so [PasswordEncoder.matches] can only ever return false against it.
         */
        internal const val EXTERNALLY_VERIFIED = "externally-verified"

        /**
         * BTK has not allocated `+90 500`, so no subscriber can hold one. Confining test numbers to
         * this range is what makes the SMS bypass safe: the worst a mistake can do is create an
         * account nobody could have owned anyway.
         */
        internal const val TEST_NUMBER_PREFIX = "+90500"

        /** A handful is a test fleet. A long list is a bypass wearing a test fleet's name. */
        internal const val MAX_TEST_NUMBERS = 10

        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}
