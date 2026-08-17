package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import com.muhabbet.messaging.domain.service.PushNotificationComposer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Pushes one message to every device of one recipient who is not currently looking at that
 * conversation.
 *
 * The name is a holdover from when this only ran for a recipient with no open socket at all; #618
 * moved the decision from "connected or not" to "viewing this chat or not" (see
 * [com.muhabbet.messaging.adapter.out.external.RedisMessageBroadcaster.broadcastMessage]), so this
 * is now also reached for a recipient who is online and reading a different conversation. What it
 * does here is unchanged — only when its caller decides to call it has moved.
 *
 * Both broadcasters do this and neither should own it. #469 was one bug written twice — the same
 * push block existed in `RedisMessageBroadcaster` and `WebSocketMessageBroadcaster` and had already
 * drifted apart, one having grown per-type bodies while the live one still sent a constant title.
 * `PushNotificationComposer` took the wording; this takes the fan-out, so there is no longer a
 * second copy for the next fix to miss.
 *
 * An adapter, not a domain service: it reads the auth module's device rows, which is exactly the
 * kind of cross-module reach the domain is not allowed. Keeping it here also means that import
 * exists in one file rather than in both broadcasters.
 */
@Component
class OfflinePushSender(
    private val deviceRepository: DeviceRepository,
    private val pushNotificationPort: PushNotificationPort,
    private val pushComposer: PushNotificationComposer
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Composed **once per language**, not once per device: a user with a phone and a tablet that
     * both read Turkish gets one composition and two sends. Grouping is what makes per-device
     * language affordable here — the alternative, composing inside the device loop, would repeat a
     * bundle lookup for every device on the busiest path in the app.
     *
     * Costs exactly the one query it always did. The locale rides on the device row that had to be
     * read anyway to find the push token, so naming the reader's language adds no round trip
     * (#491/#492 are about the cost of this path; this does not add to it).
     *
     * Never throws. A push is a courtesy on top of a message that is already stored and will be
     * delivered on reconnect; letting a failed one abort the loop would cost the *remaining*
     * recipients their notification too.
     */
    fun sendTo(
        recipientId: UUID,
        message: Message,
        senderName: String?,
        conversation: Conversation?
    ) {
        try {
            deviceRepository.findByUserId(recipientId)
                .filter { !it.pushToken.isNullOrBlank() }
                .groupBy { it.locale }
                .forEach { (localeTag, devices) ->
                    val push = pushComposer.compose(
                        message = message,
                        senderName = senderName,
                        conversation = conversation,
                        recipientLocale = PushNotificationComposer.localeOf(localeTag)
                    )
                    devices.forEach { device ->
                        device.pushToken?.let { token -> pushNotificationPort.sendPush(token, push) }
                    }
                }
        } catch (e: Exception) {
            log.warn("Push notification failed for userId={}: {}", recipientId, e.message)
        }
    }
}
