package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.OtpVerifier
import com.muhabbet.shared.config.SmsProperties
import com.twilio.Twilio
import com.twilio.exception.ApiException
import com.twilio.rest.verify.v2.service.Verification
import com.twilio.rest.verify.v2.service.VerificationCheck
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Twilio Verify. Unlike the Messages API adapter this needs no purchased sender number — Twilio
 * generates, delivers and checks the code against a Verify service.
 */
@Component
@ConditionalOnProperty(name = ["muhabbet.sms.provider"], havingValue = "twilio-verify")
class TwilioVerifyOtpVerifier(
    private val smsProperties: SmsProperties
) : OtpVerifier {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        require(smsProperties.twilio.accountSid.isNotBlank()) { "muhabbet.sms.twilio.account-sid is required" }
        require(smsProperties.twilio.authToken.isNotBlank()) { "muhabbet.sms.twilio.auth-token is required" }
        require(smsProperties.twilio.verifyServiceSid.isNotBlank()) { "muhabbet.sms.twilio.verify-service-sid is required" }
        Twilio.init(smsProperties.twilio.accountSid, smsProperties.twilio.authToken)
        log.info("Twilio Verify OTP verifier initialized")
    }

    override fun start(phoneNumber: String) {
        val verification = Verification
            .creator(smsProperties.twilio.verifyServiceSid, phoneNumber, CHANNEL_SMS)
            .create()
        log.info("Verification started: phone={}, status={}", phoneNumber.takeLast(4), verification.status)
    }

    override fun check(phoneNumber: String, code: String): Boolean {
        val status = try {
            VerificationCheck
                .creator(smsProperties.twilio.verifyServiceSid)
                .setTo(phoneNumber)
                .setCode(code)
                .create()
                .status
        } catch (e: ApiException) {
            // 20404 means there is no pending verification for this number: it expired, or it was
            // already consumed. That is an ordinary failed attempt.
            //
            // Everything else — a rotated auth token, a typo in the service SID, a 429, a 5xx — is
            // an outage. Returning false for those would tell a user holding the correct code that
            // it is wrong, burn one of their five attempts, and leave nothing above INFO in the
            // logs, so a permanently broken config would be indistinguishable from ordinary typos.
            // Let it propagate: the caller turns it into a 500 that can be alerted on.
            if (e.statusCode == HTTP_NOT_FOUND && e.code == NO_PENDING_VERIFICATION) {
                log.info("No pending verification: phone={}", phoneNumber.takeLast(4))
                return false
            }
            log.error(
                "Verify check failed: phone={}, status={}, code={}",
                phoneNumber.takeLast(4), e.statusCode, e.code, e,
            )
            throw e
        }
        return status == STATUS_APPROVED
    }

    private companion object {
        const val CHANNEL_SMS = "sms"
        const val STATUS_APPROVED = "approved"
        const val HTTP_NOT_FOUND = 404
        /** Twilio: "Verification resource not found" — expired or already consumed. */
        const val NO_PENDING_VERIFICATION = 20404
    }
}
