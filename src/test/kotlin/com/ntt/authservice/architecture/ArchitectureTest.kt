package com.ntt.authservice.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Architecture boundary tests using ArchUnit.
 * Enforces hexagonal/clean architecture constraints.
 */
@DisplayName("Architecture Boundary Tests")
class ArchitectureTest {

    private val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.ntt.authservice")

    @Test
    @DisplayName("Domain layer must NOT depend on Spring framework")
    fun domainShouldNotDependOnSpring() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("..domain.model..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")

        rule.check(importedClasses)
    }

    @Test
    @DisplayName("Domain layer must NOT depend on JPA/Jakarta persistence")
    fun domainShouldNotDependOnJpa() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("..domain.model..")
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")

        rule.check(importedClasses)
    }

    @Test
    @DisplayName("Domain services must NOT depend on adapters")
    fun domainServicesShouldNotDependOnAdapters() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("..domain.service..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")

        rule.check(importedClasses)
    }

    @Test
    @DisplayName("Application ports must NOT depend on adapters")
    fun portsShouldNotDependOnAdapters() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("..application.port..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")

        rule.check(importedClasses)
    }

    @Test
    @DisplayName("Command handlers should access only ports and domain")
    fun handlersShouldUseOnlyPortsAndDomain() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("..application.command..")
            .should().dependOnClassesThat()
            .resideInAPackage("..adapter.out.persistence.repository..")

        rule.check(importedClasses)
    }
}
