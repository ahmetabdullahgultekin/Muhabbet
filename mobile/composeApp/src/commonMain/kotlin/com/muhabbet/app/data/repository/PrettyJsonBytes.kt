package com.muhabbet.app.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pretty-prints a [JsonElement] straight into UTF-8 bytes (#780 review).
 *
 * The data export used to go `encodeToString(document)` → `encodeToByteArray()`: one full UTF-16
 * `String` of the whole account (two bytes a character) and then a second full copy as bytes, on
 * top of the document tree itself. On an account of a few hundred thousand messages that is several
 * hundred megabytes of transient copies. This walks the tree and appends each token's UTF-8 bytes
 * to one growing buffer, so no `String` of the whole export ever exists — only the UTF-8 bytes,
 * briefly twice while the buffer is trimmed to size at the end.
 *
 * Only structure is written here. Every scalar goes through kotlinx-serialization's own encoder, so
 * string escaping is the library's and not a second implementation of RFC 8259 that could disagree
 * with it. The layout matches `Json { prettyPrint = true }` exactly (four-space indent,
 * `"key": value`, `[]`/`{}` when empty); a test pins the two byte-for-byte.
 *
 * Common code has no streaming file sink in this project's dependencies, so the output is still one
 * array in memory. Writing directly to the destination is the next step, tracked in #780.
 */
internal object PrettyJsonBytes {

    private const val INDENT = "    "
    private val Compact = Json

    fun encode(element: JsonElement): ByteArray {
        val out = ByteSink()
        write(element, out, depth = 0)
        return out.toByteArray()
    }

    private fun write(element: JsonElement, out: ByteSink, depth: Int) {
        when (element) {
            is JsonObject -> writeObject(element, out, depth)
            is JsonArray -> writeArray(element, out, depth)
            is JsonNull -> out.append("null")
            is JsonPrimitive -> out.append(Compact.encodeToString(JsonPrimitive.serializer(), element))
        }
    }

    private fun writeObject(obj: JsonObject, out: ByteSink, depth: Int) {
        if (obj.isEmpty()) {
            out.append("{}")
            return
        }
        out.append("{")
        var first = true
        for ((key, value) in obj) {
            if (!first) out.append(",")
            first = false
            newline(out, depth + 1)
            out.append(Compact.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(key)))
            out.append(": ")
            write(value, out, depth + 1)
        }
        newline(out, depth)
        out.append("}")
    }

    private fun writeArray(array: JsonArray, out: ByteSink, depth: Int) {
        if (array.isEmpty()) {
            out.append("[]")
            return
        }
        out.append("[")
        array.forEachIndexed { index, value ->
            if (index > 0) out.append(",")
            newline(out, depth + 1)
            write(value, out, depth + 1)
        }
        newline(out, depth)
        out.append("]")
    }

    private fun newline(out: ByteSink, depth: Int) {
        out.append("\n")
        repeat(depth) { out.append(INDENT) }
    }
}

/** A growable byte buffer: amortised doubling, one final trim. Common code has no ByteArrayOutputStream. */
private class ByteSink {
    private var buffer = ByteArray(INITIAL_CAPACITY)
    private var size = 0

    fun append(token: String) {
        val bytes = token.encodeToByteArray()
        if (size + bytes.size > buffer.size) {
            var capacity = buffer.size
            while (capacity < size + bytes.size) capacity *= 2
            buffer = buffer.copyOf(capacity)
        }
        bytes.copyInto(buffer, destinationOffset = size)
        size += bytes.size
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private companion object {
        const val INITIAL_CAPACITY = 64 * 1024
    }
}
