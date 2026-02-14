package com.muhabbet.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HexagonalArchitectureTest {

    companion object {
        private val importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.muhabbet")

        @JvmStatic
        @BeforeAll
        fun setUp() {
            // Warm up class import
        }
    }

    // ─── Domain Independence ─────────────────────────────

    @Nested
    inner class DomainIndependence {

        @Test
        fun `domain models should not depend on Spring framework`() {
            val rule: ArchRule = noClasses()
                .that().resideInAPackage("..domain.model..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")

            rule.check(importedClasses)
        }

        @Test
        fun `domain services should not depend on adapters`() {
            val rule: ArchRule = noClasses()
                .that().resideInAPackage("..domain.service..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")

            rule.check(importedClasses)
        }

        @Test
        fun `domain ports should not depend on adapters`() {
            val rule: ArchRule = noClasses()
                .that().resideInAPackage("..domain.port..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")

            rule.check(importedClasses)
        }

        @Test
        fun `domain models should not depend on JPA`() {
            val rule: ArchRule = noClasses()
                .that().resideInAPackage("..domain.model..")
                .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")

            rule.check(importedClasses)
        }
    }

    // ─── Adapter Rules ───────────────────────────────────

    @Nested
    inner class AdapterRules {

        @Test
        fun `controllers should not depend on persistence adapters`() {
            val rule: ArchRule = noClasses()
                .that().resideInAPackage("..adapter.in.web..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.out.persistence.entity..")

            rule.check(importedClasses)
        }

        @Test
        fun `controllers should be annotated with RestController`() {
            val rule: ArchRule = classes()
                .that().resideInAPackage("..adapter.in.web..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(org.springframework.web.bind.annotation.RestController::class.java)

            rule.check(importedClasses)
        }
    }

    // ─── Module Boundaries ───────────────────────────────

    @Nested
    inner class ModuleBoundaries {

        @Test
        fun `messaging module should not directly depend on auth domain services`() {
            val rule: ArchRule = noClasses()
                .that().resideInAPackage("com.muhabbet.messaging..")
                .should().dependOnClassesThat().resideInAPackage("com.muhabbet.auth.domain.service..")

            rule.check(importedClasses)
        }

        @Test
        fun `moderation module should not directly depend on messaging domain services`() {
            val rule: ArchRule = noClasses()
                .that().resideInAPackage("com.muhabbet.moderation..")
                .should().dependOnClassesThat().resideInAPackage("com.muhabbet.messaging.domain.service..")

            rule.check(importedClasses)
        }

        @Test
        fun `media module should not directly depend on messaging domain services`() {
            val rule: ArchRule = noClasses()
                .that().resideInAPackage("com.muhabbet.media..")
                .should().dependOnClassesThat().resideInAPackage("com.muhabbet.messaging.domain.service..")

            rule.check(importedClasses)
        }
    }

    // ─── Naming Conventions ──────────────────────────────

    @Nested
    inner class NamingConventions {

        @Test
        fun `JPA entities should end with JpaEntity`() {
            val rule: ArchRule = classes()
                .that().resideInAPackage("..adapter.out.persistence.entity..")
                .and().areAnnotatedWith(jakarta.persistence.Entity::class.java)
                .should().haveSimpleNameEndingWith("JpaEntity")

            rule.check(importedClasses)
        }

        @Test
        fun `use case interfaces should end with UseCase`() {
            val rule: ArchRule = classes()
                .that().resideInAPackage("..domain.port.in..")
                .should().haveSimpleNameEndingWith("UseCase")

            rule.check(importedClasses)
        }
    }

    // ─── No Spring in Domain ─────────────────────────────

    @Nested
    inner class NoSpringInDomain {

        @Test
        fun `domain services should not use Spring Service annotation`() {
            val rule: ArchRule = noClasses()
                .that().resideInAPackage("..domain.service..")
                .should().beAnnotatedWith(org.springframework.stereotype.Service::class.java)

            rule.check(importedClasses)
        }

        @Test
        fun `domain services should not use Spring Component annotation`() {
            val rule: ArchRule = noClasses()
                .that().resideInAPackage("..domain.service..")
                .should().beAnnotatedWith(org.springframework.stereotype.Component::class.java)

            rule.check(importedClasses)
        }

        @Test
        fun `domain models should not use Spring annotations`() {
            val rule: ArchRule = noClasses()
                .that().resideInAPackage("..domain.model..")
                .should().beAnnotatedWith(org.springframework.stereotype.Component::class.java)
                .orShould().beAnnotatedWith(org.springframework.stereotype.Service::class.java)

            rule.check(importedClasses)
        }
    }
}
