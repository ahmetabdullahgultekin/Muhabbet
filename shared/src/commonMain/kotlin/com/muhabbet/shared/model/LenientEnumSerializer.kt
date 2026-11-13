package com.muhabbet.shared.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The serializer every enum on the wire uses: known names round-trip unchanged, an unknown name
 * decodes to [unknown] instead of throwing (#875).
 *
 * Why this matters more than it looks. The backend and the app are two binaries that are never
 * deployed together — the app on a phone is whatever version the user last installed from Play. A
 * kotlinx enum rejects a name it does not know, so the day the server learned `MemberRole.MODERATOR`
 * every installed app would have failed to decode **the whole conversation-list page** (one
 * participant poisons the list) and silently dropped every `message.new` frame carrying a new
 * content type. The code base had already noticed and was dodging it field by field by typing roles
 * as `String`, which is how stringly-typed fields spread (#877).
 *
 * `coerceInputValues` alone is not the fix: it only rescues a property that is nullable or has a
 * default. `NewMessage.contentType` and `ParticipantResponse.role` have neither, so the tolerance has
 * to live in the enum's own serializer.
 *
 * [wireName] is the exact string the enum has always been written as. It defaults to the constant's
 * `name`; [VisibilityLevel] overrides it because the privacy contract has always used lowercase.
 * Decoding is exact — no case folding — so a value that was never legal on the wire does not become
 * legal by accident.
 *
 * [unknown] must never be *sent*: it is what a reader substitutes for a value it cannot understand,
 * not a value anyone means. Encoding it writes its own [wireName] only so a client-side cache can
 * store what it was given and read it back.
 */
open class LenientEnumSerializer<E : Enum<E>>(
    serialName: String,
    private val values: List<E>,
    private val unknown: E,
    private val wireName: (E) -> String = { it.name },
) : KSerializer<E> {

    private val byWireName: Map<String, E> by lazy { values.associateBy(wireName) }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.muhabbet.shared.model.$serialName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: E) {
        encoder.encodeString(wireName(value))
    }

    override fun deserialize(decoder: Decoder): E = byWireName[decoder.decodeString()] ?: unknown

    /** The value [raw] names on the wire, or [unknown]. For the few places a name arrives outside JSON. */
    fun fromWire(raw: String?): E = raw?.let { byWireName[it] } ?: unknown
}

