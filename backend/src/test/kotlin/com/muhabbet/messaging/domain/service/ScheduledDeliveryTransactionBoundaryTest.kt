package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.adapter.`in`.scheduler.ScheduledMessageJob
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.transaction.annotation.Transactional

/**
 * #560 — the half of the fix that no behavioural test can see.
 *
 * [MessageService.deliverScheduledMessages] isolates failures by giving each message its own
 * transaction through [com.muhabbet.messaging.domain.port.out.TransactionRunner].
 * `SpringTransactionRunner` propagates as `REQUIRED`, so that isolation only exists while there is
 * **no transaction already open around the loop**. Put `@Transactional` back on the method — or on
 * the job that calls it — and every per-message boundary silently joins the outer one: the run is
 * one transaction again, one message's rollback still undoes the whole batch, and #560 is back
 * exactly as it was.
 *
 * Nothing else catches that. `ScheduledDeliveryIsolationTest` injects an `InlineTransactionRunner`
 * directly, so it passes whether the annotation is there or not; a unit test has no transaction
 * manager and therefore cannot tell a real boundary from a joined one. The only durable statement
 * of the invariant is the absence of the annotation, so it is asserted here rather than left as a
 * comment — and it is an easy one to undo by accident, since ten sibling methods on the same class
 * do carry `@Transactional`.
 *
 * If this test fails, do not delete it and do not add the annotation back. The boundary belongs
 * inside the loop; anything the method needs to do transactionally goes in the block passed to
 * `inTransaction`.
 */
class ScheduledDeliveryTransactionBoundaryTest {

    @Test
    fun `should not wrap the whole scheduled run in one transaction`() {
        val method = MessageService::class.java.getDeclaredMethod("deliverScheduledMessages")

        // findMergedAnnotation, not getAnnotation: it resolves meta-annotations and the class-level
        // declaration the same way Spring's transaction interceptor does, so a @Transactional put
        // on MessageService itself is caught too.
        assertNull(
            AnnotatedElementUtils.findMergedAnnotation(method, Transactional::class.java),
            "deliverScheduledMessages must not be @Transactional — an outer transaction makes the " +
                "per-message boundaries join it, and one failure rolls back the whole batch again (#560)"
        )
        assertNull(
            AnnotatedElementUtils.findMergedAnnotation(MessageService::class.java, Transactional::class.java),
            "a class-level @Transactional on MessageService would wrap the scheduled run just the same (#560)"
        )
    }

    @Test
    fun `should not wrap the scheduled job in one transaction either`() {
        // The job is the only caller, so a @Transactional here opens the outer transaction just as
        // effectively as one on the service — and would be easier to miss, being in another file.
        val method = ScheduledMessageJob::class.java.getDeclaredMethod("deliverScheduledMessages")

        assertNull(
            AnnotatedElementUtils.findMergedAnnotation(method, Transactional::class.java),
            "ScheduledMessageJob.deliverScheduledMessages must not be @Transactional (#560)"
        )
        assertNull(
            AnnotatedElementUtils.findMergedAnnotation(ScheduledMessageJob::class.java, Transactional::class.java),
            "a class-level @Transactional on ScheduledMessageJob would wrap the whole run (#560)"
        )
    }
}
