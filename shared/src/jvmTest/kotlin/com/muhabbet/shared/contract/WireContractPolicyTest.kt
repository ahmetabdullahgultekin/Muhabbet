package com.muhabbet.shared.contract

import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.model.LenientEnumSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializerOrNull
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Policies that hold for **every** type in the shared contract, enforced by walking all of them.
 */
class WireContractPolicyTest {

    /** #875: one enum with the default serializer is one enum that breaks old apps when it grows. */
    @Test
    fun `should give every enum in shared a lenient serializer and an UNKNOWN constant`() {
        val enums = SharedClasses.all.filter { it.isEnum }
        assertTrue(enums.size >= 10, "expected the wire enums, found ${enums.map { it.simpleName }}")

        val offenders = enums.filterNot { enumClass ->
            val serializer = serializerOrNull(enumClass)
            val hasUnknown = enumClass.enumConstants.any { (it as Enum<*>).name == "UNKNOWN" }
            serializer is LenientEnumSerializer<*> && hasUnknown
        }

        assertTrue(offenders.isEmpty(), "not lenient or no UNKNOWN: ${offenders.map { it.name }}")
    }

    /**
     * #887: a nullable property without a default throws `MissingFieldException` when the producer
     * omits the key — and Jackson, older servers and third-party clients all omit nulls.
     */
    @Test
    fun `should give every nullable property in the contract a default`() {
        val descriptors = SharedClasses.all
            .filter { it.typeParameters.isEmpty() && !it.isEnum }
            .mapNotNull { serializerOrNull(it)?.descriptor }
            .filter { it.kind == StructureKind.CLASS } +
            listOf(
                ApiResponse.serializer(String.serializer()).descriptor,
                PaginatedResponse.serializer(String.serializer()).descriptor,
            )
        assertTrue(descriptors.size > 100, "walked only ${descriptors.size} classes")

        val offenders = descriptors.flatMap { it.nullablesWithoutDefault() }

        assertTrue(offenders.isEmpty(), "nullable without `= null`: $offenders")
    }

    private fun SerialDescriptor.nullablesWithoutDefault(): List<String> =
        (0 until elementsCount)
            .filter { getElementDescriptor(it).isNullable && !isElementOptional(it) }
            .map { "$serialName.${getElementName(it)}" }
}
