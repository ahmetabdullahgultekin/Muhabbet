package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.shared.config.SmsProperties
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

/**
 * Real Netgsm SMS adapter (Turkey's gateway) using Netgsm's documented HTTP "GET" send endpoint.
 *
 * Request: GET {apiUrl}?usercode=..&password=..&gsmno=905..&message=..&msgheader=..&dil=TR
 * Response: plaintext. Success begins with "00" (followed by a bulk id, e.g. "00 123456789").
 * Any other leading code is an error (20=bad message, 30=auth, 40=msgheader, 50/51=charset/IYS,
 * 70=bad params, 80=send limit, 85=duplicate). See Netgsm REST API docs.
 *
 * Active only when `muhabbet.sms.provider=netgsm`.
 */
@Component
@ConditionalOnProperty(name = ["muhabbet.sms.provider"], havingValue = "netgsm")
class NetgsmOtpSender(
    private val smsProperties: SmsProperties,
    restClientBuilder: RestClient.Builder
) : OtpSender {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient: RestClient = restClientBuilder.build()

    override suspend fun send(phoneNumber: String, otp: String) {
        val netgsm = smsProperties.netgsm
        // Netgsm expects numbers without the leading '+', e.g. 905XXXXXXXXX.
        val gsmNumber = phoneNumber.removePrefix("+")
        val message = "Muhabbet dogrulama kodunuz: $otp"

        val uri = UriComponentsBuilder.fromUriString(netgsm.apiUrl)
            .queryParam("usercode", netgsm.usercode)
            .queryParam("password", netgsm.password)
            .queryParam("gsmno", gsmNumber)
            .queryParam("message", message)
            .queryParam("msgheader", netgsm.msgheader)
            .queryParam("dil", "TR")
            .build()
            .toUriString()

        val response: String? = try {
            restClient.get()
                .uri(uri)
                .retrieve()
                .body(String::class.java)
        } catch (e: Exception) {
            log.error("Netgsm OTP send transport error for {}: {}", gsmNumber, e.message)
            throw BusinessException(ErrorCode.OTP_SEND_FAILED, cause = e)
        }

        val code = response?.trim()
        if (code != null && code.startsWith("00")) {
            log.info("OTP sent via Netgsm to {}", gsmNumber)
        } else {
            log.error("Netgsm OTP send rejected: response={}", code)
            throw BusinessException(ErrorCode.OTP_SEND_FAILED)
        }
    }
}
