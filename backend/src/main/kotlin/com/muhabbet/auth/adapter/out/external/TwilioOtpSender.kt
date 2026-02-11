package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.shared.config.SmsProperties
import com.twilio.Twilio
import com.twilio.rest.api.v2010.account.Message
import com.twilio.type.PhoneNumber
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["muhabbet.sms.provider"], havingValue = "twilio")
class TwilioOtpSender(
    private val smsProperties: SmsProperties
) : OtpSender {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        Twilio.init(smsProperties.twilio.accountSid, smsProperties.twilio.authToken)
        log.info("Twilio OTP sender initialized")
    }

    override suspend fun send(phoneNumber: String, otp: String) {
        val message = Message.creator(
            PhoneNumber(phoneNumber),
            PhoneNumber(smsProperties.twilio.fromNumber),
            "Muhabbet doğrulama kodunuz: $otp"
        ).create()

        log.info("OTP sent via Twilio: phone={}, sid={}", phoneNumber.takeLast(4), message.sid)
    }
}
