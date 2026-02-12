package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.shared.config.SmsProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
@ConditionalOnProperty(name = ["muhabbet.sms.provider"], havingValue = "netgsm")
class NetgsmOtpSender(
    private val smsProperties: SmsProperties
) : OtpSender {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.create()

    override suspend fun send(phoneNumber: String, otp: String) {
        val netgsm = smsProperties.netgsm

        // Strip leading '+' — Netgsm expects numbers like 905XXXXXXXXX
        val gsmNumber = phoneNumber.removePrefix("+")
        val message = "Muhabbet dogrulama kodunuz: $otp"

        val xmlBody = """
            <?xml version="1.0" encoding="UTF-8"?>
            <mainbody>
                <header>
                    <company dession="1">Netgsm</company>
                    <usercode>${netgsm.usercode}</usercode>
                    <password>${netgsm.password}</password>
                    <type>1:n</type>
                    <msgheader>${netgsm.msgheader}</msgheader>
                </header>
                <body>
                    <msg><![CDATA[$message]]></msg>
                    <no>$gsmNumber</no>
                </body>
            </mainbody>
        """.trimIndent()

        try {
            val response = restClient.post()
                .uri(netgsm.apiUrl)
                .header("Content-Type", "application/xml")
                .body(xmlBody)
                .retrieve()
                .body(String::class.java)

            // Netgsm returns codes like "00" for success, "20" etc for errors
            if (response != null && response.startsWith("00")) {
                log.info("OTP sent via Netgsm to {}", gsmNumber)
            } else {
                log.error("Netgsm OTP send failed: response={}", response)
                throw RuntimeException("Netgsm SMS failed: $response")
            }
        } catch (e: Exception) {
            log.error("Netgsm OTP send error for {}: {}", gsmNumber, e.message)
            throw e
        }
    }
}
