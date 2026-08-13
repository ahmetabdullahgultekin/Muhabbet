package com.muhabbet.auth.domain.port.out

/**
 * Blocking by contract. Every implementation calls a blocking HTTP client (RestClient, the Twilio
 * SDK) from a Spring MVC request thread, so marking this `suspend` only forced a `runBlocking` back
 * at the controller without ever freeing that thread.
 */
interface OtpSender {
    fun send(phoneNumber: String, otp: String)
}
