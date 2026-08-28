package com.muhabbet.shared.config

import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.MediaAttachmentPolicyPort
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import org.springframework.stereotype.Component

/**
 * The four cross-module seams `MessageService` asks questions through, collected so the `@Bean`
 * method that builds it stays a list of arguments a person can read.
 *
 * Each is a port with an adapter on the far side, and they are the complete set of "somebody else's
 * module has to answer this before the message goes out": who the participants are
 * ([UserDirectoryPort], auth), may a READ be published ([ReadReceiptPolicyPort], auth), has the
 * recipient blocked the sender ([BlockPolicyPort], moderation), and may this message carry this
 * media ([MediaAttachmentPolicyPort], media — #679). Adding the fourth is what pushed the bean
 * method past detekt's parameter limit, and grouping them is the answer detekt is actually asking
 * for rather than a larger number in the config.
 *
 * A wiring type, deliberately: `MessageService` still takes the four ports individually, so the
 * domain never sees this class and a test can hand it four fakes without knowing it exists.
 */
@Component
class MessageServicePorts(
    val userDirectory: UserDirectoryPort,
    val readReceiptPolicy: ReadReceiptPolicyPort,
    val blockPolicy: BlockPolicyPort,
    val mediaAttachmentPolicy: MediaAttachmentPolicyPort
)
