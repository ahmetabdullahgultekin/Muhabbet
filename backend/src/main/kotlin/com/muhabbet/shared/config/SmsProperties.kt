package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * SMS gateway configuration. The active OtpSender adapter is selected by [provider]
 * (`mock` | `netgsm` | `twilio`) via @ConditionalOnProperty on the adapter classes.
 * Secrets are injected from the environment in application.yml — never hardcoded here.
 */
@ConfigurationProperties(prefix = "muhabbet.sms")
data class SmsProperties(
    val provider: String = "mock",
    val twilio: TwilioProperties = TwilioProperties(),
    val netgsm: NetgsmProperties = NetgsmProperties()
)

data class TwilioProperties(
    val accountSid: String = "",
    val authToken: String = "",
    val fromNumber: String = "",
    // Twilio Messages REST API base. {AccountSid} is substituted at send time.
    val apiBaseUrl: String = "https://api.twilio.com/2010-04-01"
)

data class NetgsmProperties(
    val usercode: String = "",
    val password: String = "",
    val msgheader: String = "MUHABBET",
    // Netgsm documented HTTP "GET" send endpoint. Returns a plaintext status code
    // (e.g. "00 <bulkid>" on success, "20"/"30"/"40"/"50"/"51"/"70"/"80"/"85" on error).
    val apiUrl: String = "https://api.netgsm.com.tr/sms/send/get"
)
