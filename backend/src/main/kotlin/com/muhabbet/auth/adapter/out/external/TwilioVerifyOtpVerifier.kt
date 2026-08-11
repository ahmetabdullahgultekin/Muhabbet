package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.OtpVerifier
import com.muhabbet.shared.config.SmsProperties
import com.twilio.Twilio
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

    override suspend fun start(phoneNumber: String) {
        val verification = Verification
            .creator(smsProperties.twilio.verifyServiceSid, phoneNumber, CHANNEL_SMS)
            .create()
        log.info("Verification started: phone={}, status={}", phoneNumber.takeLast(4), verification.status)
    }

    override suspend fun check(phoneNumber: String, code: String): Boolean {
        // Twilio raises rather than returning a failed check for an unknown/expired verification,
        // which is an ordinary wrong-code outcome here, not an outage.
        val status = runCatching {
            VerificationCheck
                .creator(smsProperties.twilio.verifyServiceSid)
                .setTo(phoneNumber)
                .setCode(code)
                .create()
                .status
        }.getOrElse { e ->
            log.info("Verification check rejected: phone={}, reason={}", phoneNumber.takeLast(4), e.message)
            return false
        }
        return status == STATUS_APPROVED
    }

    private companion object {
        const val CHANNEL_SMS = "sms"
        const val STATUS_APPROVED = "approved"
    }
}
