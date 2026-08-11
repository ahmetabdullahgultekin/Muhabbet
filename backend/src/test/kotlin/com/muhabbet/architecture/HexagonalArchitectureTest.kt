package com.muhabbet.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
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
                .and().areInterfaces()
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

    // ─── Spring Proxy Safety ─────────────────────────────

    @Nested
    inner class SpringProxySafety {

        /**
         * A @Transactional method forces Spring to wrap the bean in a CGLIB subclass. CGLIB builds
         * that subclass without running a constructor, so its own fields are null, and it can only
         * route a call to the real target by overriding the method. A final method cannot be
         * overridden, so the call runs on the proxy instead — and every dependency it touches is
         * null at runtime.
         *
         * This is silent: the class compiles, the context starts, and the bean's non-final methods
         * work. Only the final ones blow up, and only when first called. StatusService shipped two
         * of them; GET /api/v1/statuses/contacts returned 500 in production for months.
         *
         * In Kotlin a method is final unless it is `open` or `override`, so putting the method on
         * the use-case interface is the usual fix — which the hexagonal rules want anyway.
         */
        @Test
        fun `transactional methods must not be final`() {
            val rule: ArchRule = methods()
                .that().areAnnotatedWith(org.springframework.transaction.annotation.Transactional::class.java)
                .and().arePublic()
                .should().notHaveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
                .because(
                    "Spring cannot route a final method through its CGLIB proxy, so it runs on the " +
                        "proxy instance whose injected fields are all null"
                )

            rule.check(importedClasses)
        }
    }
}
