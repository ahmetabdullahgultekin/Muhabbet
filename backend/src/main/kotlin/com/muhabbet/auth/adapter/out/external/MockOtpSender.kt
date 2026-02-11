package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.OtpSender
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["muhabbet.sms.provider"], havingValue = "mock", matchIfMissing = true)
class MockOtpSender : OtpSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun send(phoneNumber: String, otp: String) {
        log.info("OTP for {}: {}", phoneNumber, otp)
    }
}
