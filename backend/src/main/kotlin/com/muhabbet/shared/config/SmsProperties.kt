package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "muhabbet.sms")
data class SmsProperties(
    val provider: String = "mock",
    val twilio: TwilioProperties = TwilioProperties()
)

data class TwilioProperties(
    val accountSid: String = "",
    val authToken: String = "",
    val fromNumber: String = ""
)
