package com.muhabbet.shared.contract

import com.muhabbet.shared.protocol.WsMessage
import java.io.File

/**
 * Every class compiled from this module's `commonMain`, loaded from the JVM output directory.
 *
 * The contract tests walk this rather than a hand-kept list, so a DTO or enum added tomorrow is
 * checked without anybody remembering to register it — which is the whole failure mode they exist
 * to prevent.
 */
internal object SharedClasses {

    val all: List<Class<*>> by lazy {
        val root = File(WsMessage::class.java.protectionDomain.codeSource.location.toURI())
        require(root.isDirectory) { "expected the shared main output as a directory, got $root" }
        root.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map { it.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.') }
            .filter { it.startsWith("com.muhabbet.shared.") }
            .mapNotNull { name -> runCatching { Class.forName(name, false, WsMessage::class.java.classLoader) }.getOrNull() }
            .toList()
            .also { check(it.size > 50) { "found only ${it.size} classes under $root — the walk is broken" } }
    }

    /** The repository root, from the shared module's working directory (Gradle runs tests there). */
    val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile && File(it, "backend").isDirectory }
    }
}
