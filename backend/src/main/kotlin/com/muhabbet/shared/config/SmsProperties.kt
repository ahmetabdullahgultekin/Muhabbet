package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "muhabbet.sms")
data class SmsProperties(
    val provider: String = "mock",
    val twilio: TwilioProperties = TwilioProperties(),
    val netgsm: NetgsmProperties = NetgsmProperties()
)

data class TwilioProperties(
    val accountSid: String = "",
    val authToken: String = "",
    val fromNumber: String = ""
)

data class NetgsmProperties(
    val usercode: String = "",
    val password: String = "",
    val msgheader: String = "MUHABBET",
    val apiUrl: String = "https://api.netgsm.com.tr/sms/send/xml"
)
