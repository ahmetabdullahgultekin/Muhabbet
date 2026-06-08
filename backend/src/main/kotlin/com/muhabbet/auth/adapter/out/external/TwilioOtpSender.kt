package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.shared.config.SmsProperties
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.util.Base64

/**
 * Real Twilio SMS adapter using Twilio's documented Messages REST API directly
 * (no Twilio SDK dependency — keeps the classpath light and the transport MockK-able).
 *
 * Request: POST {apiBaseUrl}/Accounts/{AccountSid}/Messages.json
 *   Authorization: Basic base64(AccountSid:AuthToken)
 *   Content-Type: application/x-www-form-urlencoded
 *   Body: To=+90..&From=+1..&Body=..
 * Response: 2xx with a JSON payload (contains "sid") on success; 4xx/5xx with a JSON error on failure.
 *
 * Active only when `muhabbet.sms.provider=twilio`.
 */
@Component
@ConditionalOnProperty(name = ["muhabbet.sms.provider"], havingValue = "twilio")
class TwilioOtpSender(
    private val smsProperties: SmsProperties,
    restClientBuilder: RestClient.Builder
) : OtpSender {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient: RestClient = restClientBuilder.build()

    override suspend fun send(phoneNumber: String, otp: String) {
        val twilio = smsProperties.twilio
        val url = "${twilio.apiBaseUrl}/Accounts/${twilio.accountSid}/Messages.json"
        val basicAuth = Base64.getEncoder()
            .encodeToString("${twilio.accountSid}:${twilio.authToken}".toByteArray())

        val form = LinkedMultiValueMap<String, String>().apply {
            add("To", phoneNumber)
            add("From", twilio.fromNumber)
            add("Body", "Muhabbet doğrulama kodunuz: $otp")
        }

        try {
            restClient.post()
                .uri(url)
                .header("Authorization", "Basic $basicAuth")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            // RestClient.retrieve() throws RestClientResponseException on non-2xx by default,
            // and a transport exception otherwise — both mean the send failed.
            log.error("Twilio OTP send failed for {}: {}", phoneNumber.takeLast(4), e.message)
            throw BusinessException(ErrorCode.OTP_SEND_FAILED, cause = e)
        }

        log.info("OTP sent via Twilio to ...{}", phoneNumber.takeLast(4))
    }
}
