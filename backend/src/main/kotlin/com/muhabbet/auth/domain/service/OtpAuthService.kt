package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.OtpRequest
import com.muhabbet.auth.domain.model.OtpSettings
import com.muhabbet.auth.domain.port.`in`.AuthResult
import com.muhabbet.auth.domain.port.`in`.OtpResult
import com.muhabbet.auth.domain.port.`in`.RequestOtpUseCase
import com.muhabbet.auth.domain.port.`in`.VerifyOtpUseCase
import com.muhabbet.auth.domain.port.out.OtpQuotaPort
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.OtpVerifier
import com.muhabbet.auth.domain.port.out.PasswordHasher
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

/**
 * Signing in with a one-time code sent to the phone: requesting the code and verifying it (#894).
 *
 * This was most of a 484-line `AuthService` that implemented six use cases. Token rotation and
 * logout are [TokenService], push-token registration is [DeviceRegistrationService], the Firebase
 * path is [FirebaseSignInService]; the two-step gate and the sign-in tail that both sign-in paths
 * share are [TwoStepSignInGate] and [PhoneSignIn].
 */
class OtpAuthService(
    private val userRepository: UserRepository,
    private val otpRepository: OtpRepository,
    /** Absent when an [otpVerifier] owns delivery; see [delivery]. */
    otpSender: OtpSender?,
    /**
     * Present only when an external verification provider is configured. When set, it owns code
     * generation and checking; the sender and the local hash comparison are bypassed.
     */
    otpVerifier: OtpVerifier?,
    private val passwordHasher: PasswordHasher,
    /**
     * Budget for verifications *started*, because each one is billed. Required rather than
     * defaulted: a no-op default would silently remove the only ceiling on the provider bill, which
     * is the failure #440 is about.
     */
    private val otpQuotaPort: OtpQuotaPort,
    private val twoStepGate: TwoStepSignInGate,
    private val phoneSignIn: PhoneSignIn,
    private val settings: OtpSettings,
    private val transactions: TransactionRunner
) : RequestOtpUseCase, VerifyOtpUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    /**
     * Exactly one delivery path must exist. Neither means every login silently fails at the first
     * OTP; both means the configuration is ambiguous about who owns the code. Resolving it once,
     * here, stops the context with a message naming the property — and leaves every later branch a
     * `when` over a closed type rather than a pair of nullables asserted non-null (#895).
     */
    private val delivery: OtpDelivery = when {
        otpSender != null && otpVerifier != null -> throw IllegalArgumentException(
            "Both an OtpSender and an OtpVerifier are configured; muhabbet.sms.provider must " +
                "select exactly one"
        )
        otpVerifier != null -> OtpDelivery.External(otpVerifier)
        otpSender != null -> OtpDelivery.Sms(otpSender)
        else -> throw IllegalArgumentException(
            "No OTP delivery configured: muhabbet.sms.provider matched neither an OtpSender " +
                "(mock/netgsm/twilio) nor an OtpVerifier (twilio-verify)"
        )
    }

    private sealed interface OtpDelivery {
        data class Sms(val sender: OtpSender) : OtpDelivery
        data class External(val verifier: OtpVerifier) : OtpDelivery
    }

    init {
        // A test number skips the SMS provider entirely, so anyone able to add one to the config
        // could turn a real person's number into an account they can sign into by reading the log.
        // Restricting the list to +90500 — a range BTK has not allocated, so no handset can ever
        // hold one — makes that impossible rather than merely discouraged. Failing at startup is
        // the point: a typo must stop the deployment, not quietly widen the door.
        settings.testNumbers.forEach { number ->
            require(number.startsWith(TEST_NUMBER_PREFIX)) {
                "muhabbet.otp.test-numbers may only contain unallocated $TEST_NUMBER_PREFIX numbers, " +
                    "so a real subscriber can never be listed; got ${number.takeLast(4)}"
            }
            require(ValidationRules.isValidTurkishPhone(number)) {
                "muhabbet.otp.test-numbers contains a malformed number ending ${number.takeLast(4)}"
            }
        }
        require(settings.testNumbers.size <= MAX_TEST_NUMBERS) {
            "muhabbet.otp.test-numbers holds ${settings.testNumbers.size} entries; at most $MAX_TEST_NUMBERS " +
                "is a test fleet, more is a bypass"
        }
        if (settings.testNumbers.isNotEmpty()) {
            log.warn(
                "OTP bypass active for {} test number(s); their codes are written to this log and " +
                    "never sent by SMS",
                settings.testNumbers.size
            )
        }
    }

    private fun isTestNumber(phoneNumber: String) = phoneNumber in settings.testNumbers

    /**
     * Whether this number's code is generated, hashed and checked here. A test number always takes
     * the local path, whichever provider is configured.
     */
    private fun isLocal(phoneNumber: String) = delivery is OtpDelivery.Sms || isTestNumber(phoneNumber)

    override fun requestOtp(phoneNumber: String): OtpResult = transactions.inTransaction {
        if (!ValidationRules.isValidTurkishPhone(phoneNumber)) {
            throw BusinessException(ErrorCode.AUTH_INVALID_PHONE)
        }
        requireCooldownElapsed(phoneNumber)

        // Checked after the cooldown so a client hammering one number spends its own cooldown
        // rather than the deployment's hourly budget, and before anything is generated or stored so
        // a refusal costs nothing. Test numbers are exempt: they never reach the provider, so they
        // cost nothing and must not be able to exhaust the budget real users share.
        if (!isTestNumber(phoneNumber) && !otpQuotaPort.tryConsume(phoneNumber)) {
            throw BusinessException(ErrorCode.AUTH_OTP_RATE_LIMIT)
        }

        // With an external verifier the code never exists on this side, so there is nothing to
        // generate or hash. The record is still written — cooldown, expiry and attempt limits are
        // ours either way. The stored hash is a sentinel that no input can match, so a mis-wired
        // branch fails closed rather than accepting an unchecked code.
        val otp = if (isLocal(phoneNumber)) generateOtp() else null
        otpRepository.save(
            OtpRequest(
                phoneNumber = phoneNumber,
                otpHash = otp?.let { passwordHasher.hash(it) } ?: EXTERNALLY_VERIFIED,
                expiresAt = Instant.now().plusSeconds(settings.expirySeconds.toLong())
            )
        )
        dispatch(phoneNumber, otp)
        log.info("OTP requested for phone={}", phoneNumber.takeLast(4))

        OtpResult(
            ttlSeconds = settings.expirySeconds,
            retryAfterSeconds = settings.cooldownSeconds,
            mockCode = if (settings.mockEnabled) otp else null
        )
    }

    private fun requireCooldownElapsed(phoneNumber: String) {
        val activeOtp = otpRepository.findActiveByPhoneNumber(phoneNumber) ?: return
        val elapsed = Duration.between(activeOtp.createdAt, Instant.now())
        if (elapsed.seconds < settings.cooldownSeconds) {
            throw BusinessException(ErrorCode.AUTH_OTP_COOLDOWN)
        }
    }

    /** [otp] is the locally generated code, or null when the external verifier owns it. */
    private fun dispatch(phoneNumber: String, otp: String?) {
        if (isTestNumber(phoneNumber)) {
            // Nothing is sent anywhere. The code reaches the log and nowhere else, so signing in as
            // a test account requires server access rather than a handset.
            log.warn("TEST NUMBER {} — OTP not sent, code is {}", phoneNumber, otp)
            return
        }
        when (val channel = delivery) {
            is OtpDelivery.External -> channel.verifier.start(phoneNumber)
            // A local code always exists on this branch: isLocal is true whenever delivery is Sms.
            is OtpDelivery.Sms -> otp?.let { channel.sender.send(phoneNumber, it) }
        }
    }

    override fun verifyOtp(
        phoneNumber: String,
        otp: String,
        deviceName: String,
        platform: String,
        twoStepPin: String?
    ): AuthResult = transactions.inTransaction {
        val activeOtp = otpRepository.findActiveByPhoneNumber(phoneNumber)
            ?: throw BusinessException(ErrorCode.AUTH_OTP_EXPIRED)

        // Claiming an attempt and enforcing the limit are the same statement, so concurrent verifies
        // cannot each read an under-limit count and all be granted a guess.
        if (!otpRepository.claimAttempt(activeOtp, settings.maxAttempts)) {
            throw BusinessException(ErrorCode.AUTH_OTP_MAX_ATTEMPTS)
        }

        // Must mirror requestOtp's branch exactly. Asking the verifier about a test number would
        // always fail — it was never told to start one — and comparing a hash for a normal number
        // would always fail too, since the stored value is the EXTERNALLY_VERIFIED sentinel.
        val channel = delivery
        val accepted = if (channel is OtpDelivery.External && !isTestNumber(phoneNumber)) {
            channel.verifier.check(phoneNumber, otp)
        } else {
            passwordHasher.matches(otp, activeOtp.otpHash)
        }
        if (!accepted) {
            throw BusinessException(ErrorCode.AUTH_OTP_INVALID)
        }

        // The code is right. Knowing it is now only the first of two things the account may require.
        // This read is also the one the sign-in tail uses, so a sign-in is one query, not two.
        val existing = userRepository.findByPhoneNumber(phoneNumber)
        if (existing != null && twoStepGate.isEnabledFor(existing)) {
            // Hand the attempt back before refusing. The code was not guessed wrong — it was
            // correct, and the client is about to present it again alongside the PIN. Charging both
            // halves of one sign-in would spend the SMS budget on the PIN's mistakes and report them
            // as "too many attempts" on the code (#688's shape). The PIN's own counter governs from
            // here.
            otpRepository.refundAttempt(activeOtp)
            twoStepGate.require(existing, twoStepPin)
        }

        otpRepository.markVerified(activeOtp)

        phoneSignIn.complete(phoneNumber, deviceName, platform, "OTP verified", existing)
    }

    private fun generateOtp(): String {
        val bound = Math.pow(10.0, settings.length.toDouble()).toInt()
        val code = secureRandom.nextInt(bound)
        return code.toString().padStart(settings.length, '0')
    }

    companion object {
        /**
         * Stored in place of a hash when an external verifier owns the code. BCrypt never produces
         * this, so [PasswordHasher.matches] can only ever return false against it.
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
    }
}
