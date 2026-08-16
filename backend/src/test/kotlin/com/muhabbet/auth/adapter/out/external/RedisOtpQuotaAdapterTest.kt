package com.muhabbet.auth.adapter.out.external

import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * The budget that stops an uncapped Twilio bill (#440).
 *
 * Runs against a real Redis rather than a mock, because the property under test is that INCR is
 * atomic and that the counters actually persist outside the JVM — neither of which a mock would
 * demonstrate.
 */
@Testcontainers
class RedisOtpQuotaAdapterTest {

    companion object {
        @Container
        @JvmStatic
        val redis = RedisContainer("redis:7-alpine")
    }

    private fun template(): StringRedisTemplate {
        val factory = LettuceConnectionFactory(redis.redisHost, redis.redisPort)
        factory.afterPropertiesSet()
        return StringRedisTemplate(factory)
    }

    private fun adapter(global: Int = 100, perNumber: Int = 3) =
        RedisOtpQuotaAdapter(template(), maxPerHourGlobal = global, maxPerDayPerNumber = perNumber)

    /** Numbers are unique per test so the day-bucket key cannot be shared between them. */
    private fun uniqueNumber() = "+90500" + (1000000 + (0..8999999).random())

    @Test
    fun `a number is allowed up to its daily limit and then refused`() {
        val quota = adapter(perNumber = 3)
        val phone = uniqueNumber()

        repeat(3) { i -> assertTrue(quota.tryConsume(phone), "request ${i + 1} should be allowed") }

        assertFalse(quota.tryConsume(phone), "the fourth request exceeded the daily per-number cap")
    }

    @Test
    fun `one number exhausting its budget does not block another`() {
        val quota = adapter(perNumber = 2)
        val victim = uniqueNumber()
        val other = uniqueNumber()

        repeat(2) { quota.tryConsume(victim) }
        assertFalse(quota.tryConsume(victim))

        assertTrue(quota.tryConsume(other), "an unrelated number was caught by another's cap")
    }

    @Test
    fun `the global hourly ceiling applies across different numbers`() {
        // This is the one that actually bounds the bill: rotating numbers is exactly what an
        // attacker does to get past a per-number cap.
        val quota = adapter(global = 3, perNumber = 100)

        repeat(3) { i -> assertTrue(quota.tryConsume(uniqueNumber()), "request ${i + 1} allowed") }

        assertFalse(quota.tryConsume(uniqueNumber()), "rotating numbers walked past the global cap")
    }

    @Test
    fun `a refused request still spends the number's budget`() {
        // Otherwise a number could be retried for free during an hour already over its global
        // ceiling, and the per-number cap would mean nothing under exactly the conditions that
        // matter most.
        val quota = adapter(global = 0, perNumber = 2)
        val phone = uniqueNumber()

        assertFalse(quota.tryConsume(phone))
        assertFalse(quota.tryConsume(phone))

        val generous = adapter(global = 100, perNumber = 2)
        assertFalse(
            generous.tryConsume(phone),
            "the two globally-refused attempts did not count against the number's daily budget"
        )
    }
}
