package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.OtpSender
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["muhabbet.sms.provider"], havingValue = "mock", matchIfMissing = true)
class MockOtpSender : OtpSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(phoneNumber: String, otp: String) {
        // Dev and test only — OtpModeGuard refuses to boot with this provider anywhere else. The
        // number is masked all the same: a log is not the place for a full phone number.
        log.info("OTP for ...{}: {}", phoneNumber.takeLast(4), otp)
    }
}
