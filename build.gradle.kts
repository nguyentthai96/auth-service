plugins {
    id("ntt.spring-app-conventions")
    alias(libs.plugins.kotlin.jpa)
}

// group and version are inherited from gradle.properties (Single Source of Truth)

extra["springCloudVersion"] = libs.versions.spring.cloud.get()

dependencies {
//  - BASE-CORE STARTERS (provides base-core, base-model, common-log transitively)
    implementation(platform("com.ntt:platform:0.0.1-SNAPSHOT"))
    implementation("com.ntt:base-web-starter")
    implementation("com.ntt:base-data-starter")
    implementation("com.ntt:common-log")
//  - CQRS / Event Sourcing (Phase 2)
    implementation("com.ntt:eventsourcing-utils:0.0.1-SNAPSHOT")
//  - CACHE (Phase 4 — Caffeine L1)
    implementation("com.github.ben-manes.caffeine:caffeine")
//  - MAIN
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // - SECURITY
    implementation(libs.jjwt.api)
    implementation(libs.jjwt.impl)
    implementation(libs.jjwt.jackson)
    // - E2EE (End-to-End Encryption via Google Tink)
    implementation("com.google.crypto.tink:tink:1.15.0")
    implementation("com.google.crypto.tink:tink-awskms:1.15.0")
    // - AUTH CORE FEATURES (MFA, SSO, RS256, Password Policy)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka") // FR-020: Upgraded from compileOnly to runtime
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("dev.samstevens.totp:totp:1.7.1")
    implementation("org.passay:passay:1.6.4")
//  - DEVELOPMENT
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")
//  - TESTING
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.ntt:base-testing-starter")
    testImplementation(libs.archunit.junit5)
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    // BouncyCastle — required by Spring Security's Argon2PasswordEncoder
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.80")
    // H2 — in-memory database for unit/integration tests without external PostgreSQL
    testRuntimeOnly("com.h2database:h2")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Disable GraalVM AOT processing — auth-service runs as standard JVM app.
// AOT processAot conflicts with BaseEntity dual-@Id inheritance in base-core.
tasks.named("processAot") { enabled = false }
tasks.named("processTestAot") { enabled = false }