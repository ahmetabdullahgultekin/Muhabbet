package com.muhabbet.shared.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * The **one** JSON configuration for everything that crosses the wire or is stored as wire JSON:
 * WebSocket frames, REST bodies on both sides (the backend's Spring bean and the app's Ktor client),
 * the app's local cache, and JSON embedded in a message body (polls, locations) — #887.
 *
 * There used to be nine. Whether a payload decoded depended on which one read it: the app's REST
 * client was `isLenient` and would accept an unquoted string the WebSocket decoder rejected, and the
 * two embedded-payload readers did not encode defaults, so what they wrote was not what they read.
 * `SingleJsonConfigGuardTest` fails for any new `Json { }` in main sources outside this file.
 *
 * Each setting, and why:
 * - `classDiscriminator = "type"` — the WebSocket protocol's discriminator; part of the contract.
 * - `ignoreUnknownKeys` — a newer peer's extra field is not an error. Mobile and server ship apart.
 * - `encodeDefaults` — "absent" and "false" mean different things to anything that is not this code
 *   base (see the backend's `JsonConfig`, #269). Payloads match the documented contract.
 * - `coerceInputValues` — a `null` for a non-null defaulted property, or an unknown enum name for a
 *   defaulted one, takes the default instead of failing the payload.
 * - `isLenient = false` — non-standard JSON is a contract violation and should be seen as one.
 * - `explicitNulls` stays **true**, deliberately. Omitting nulls when encoding would make the server
 *   drop keys that every released app build still requires: those builds declared some nullable
 *   properties without a default, and kotlinx then throws `MissingFieldException` on an absent key.
 *   Tolerance is achieved on the decoding side instead — every nullable property in the shared
 *   contract now has `= null` (`NullableDefaultsTest` walks every descriptor to keep it so).
 * - A default deserializer for [WsMessage] — an unknown frame `type` decodes to [WsMessage.Unknown]
 *   instead of throwing (#873). This is what lets the server add a frame without old apps logging a
 *   parse error for every one, and what lets the server answer an unknown client frame with a code.
 */
val MuhabbetJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    isLenient = false
    serializersModule = SerializersModule {
        polymorphic(WsMessage::class) {
            defaultDeserializer { type -> UnknownFrameDeserializer(type) }
        }
    }
}

/**
 * Kept as the name the WebSocket code has always used. It is the same instance as [MuhabbetJson] —
 * not a second configuration — so the two can never disagree.
 */
val wsJson: Json get() = MuhabbetJson

/**
 * Reads whatever object an unknown frame was into [WsMessage.Unknown], keeping it whole for logging.
 *
 * Decoding through the tree rather than field by field means nothing about the frame's shape can make
 * this fail: the decoder hands over the object it already parsed, discriminator and all.
 */
private class UnknownFrameDeserializer(private val type: String?) : DeserializationStrategy<WsMessage> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.muhabbet.shared.protocol.WsMessage.Unknown")

    override fun deserialize(decoder: Decoder): WsMessage {
        val raw = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonObject ?: JsonObject(emptyMap())
        return WsMessage.Unknown(type, raw)
    }
}
