plugins {
    id("ntt.spring-app-conventions")
    alias(libs.plugins.kotlin.jpa)
    id("me.champeau.jmh") version "0.7.2"
}

// group and version are inherited from gradle.properties (Single Source of Truth)

extra["springCloudVersion"] = libs.versions.spring.cloud.get()

dependencies {
//  - BASE-CORE STARTERS (provides base-core, base-model, common-log transitively)
    implementation(platform("com.ntt:platform:0.0.1-SNAPSHOT"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}"))
    implementation("com.ntt:base-web-starter")
    implementation("com.ntt:base-data-starter")
    implementation("com.ntt:base-security-starter")
    implementation("com.ntt:base-observability-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.retry:spring-retry")
    implementation("com.ntt:common-log")
//  - CQRS / Event Sourcing (Phase 2)
    implementation("com.ntt:eventsourcing-utils:0.0.1-SNAPSHOT")
//  - CACHE (Phase 4 — Caffeine L1 + Redis L2)
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.ntt:base-cache-starter")
//  - MAIN
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // - SECURITY
    implementation(libs.jjwt.api)
    implementation(libs.jjwt.impl)
    implementation(libs.jjwt.jackson)
    // - E2EE (End-to-End Encryption via Google Tink)
    implementation("com.google.crypto.tink:tink:1.15.0")
    implementation("com.google.crypto.tink:tink-awskms:1.11.0")
    // - AUTH CORE FEATURES (MFA, SSO, RS256, Password Policy)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka") // FR-020: Upgraded from compileOnly to runtime
    // FR-019: Resilience4j circuit breaker for external service calls
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
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
    // BouncyCastle — required by Spring Security's Argon2PasswordEncoder (runtime for Argon2id hashing)
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    // WireMock — HTTP latency/fault simulation for SSO + Captcha circuit breaker tests (FR-002)
    testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
    // datasource-proxy — explicit version alignment for SQL query counting (FR-009)
    testImplementation("net.ttddyy:datasource-proxy:1.10")
    // H2 — in-memory database for unit/integration tests without external PostgreSQL
    testRuntimeOnly("com.h2database:h2")
//  - JMH BENCHMARKING (FR-005)
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}



tasks.withType<Test> {
    useJUnitPlatform()
}

// Disable GraalVM AOT processing — auth-service runs as standard JVM app.
// AOT processAot conflicts with BaseEntity dual-@Id inheritance in base-core.
tasks.named("processAot") { enabled = false }
tasks.named("processTestAot") { enabled = false }

// K6 Load Testing Tasks
tasks.register<Exec>("k6Run") {
    group = "Verification"
    description = "Run K6 Load Tests (Auth Flow)"
    workingDir = file("tests/load")
    commandLine("docker", "run", "--rm", "-i", "-v", "${workingDir}:/scripts", "--network", "host", "grafana/k6", "run", "/scripts/auth_flow.js")
}

tasks.register<Exec>("k6ProfileRun") {
    group = "Verification"
    description = "Run K6 Load Tests (Profile Flow)"
    workingDir = file("tests/load")
    commandLine("docker", "run", "--rm", "-i", "-v", "${workingDir}:/scripts", "--network", "host", "grafana/k6", "run", "/scripts/profile_flow.js")
}

tasks.register<Exec>("k6CacheBenchmark") {
    group = "Verification"
    description = "Run K6 Cache Encryption Benchmark (FR-010)"
    workingDir = file("tests/load")
    commandLine("docker", "run", "--rm", "-i", "-v", "${workingDir}:/scripts", "--network", "host", "grafana/k6", "run", "/scripts/cache_benchmark.js")
}

// K6 Performance Tests (FR-013: Prometheus remote write)
val perfWorkingDir = file("tests/perf")
val k6PerfBase = listOf("docker", "run", "--rm", "-i",
    "-v", "${perfWorkingDir}:/scripts",
    "--network", "host",
    "-e", "K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write",
    "grafana/k6", "run", "--out", "experimental-prometheus-rw")

tasks.register<Exec>("k6PerfSingle") {
    group = "Performance"
    description = "Run K6 Performance Single API Tests (auth P0 + P1 + internal + RBAC)"
    workingDir = perfWorkingDir
    commandLine(k6PerfBase + "/scripts/scenarios/single/auth_p0.js")
}

tasks.register<Exec>("k6PerfChains") {
    group = "Performance"
    description = "Run K6 Performance Chain Tests (auth_basic chain)"
    workingDir = perfWorkingDir
    commandLine(k6PerfBase + "/scripts/scenarios/chains/auth_basic.js")
}

tasks.register<Exec>("k6PerfLoad") {
    group = "Performance"
    description = "Run K6 Full Load Test (mixed workload)"
    workingDir = perfWorkingDir
    commandLine(k6PerfBase + "/scripts/scenarios/system/full_load.js")
}

tasks.register<Exec>("k6PerfStress") {
    group = "Performance"
    description = "Run K6 Stress Test (breaking point detection)"
    workingDir = perfWorkingDir
    commandLine(k6PerfBase + "/scripts/scenarios/system/stress.js")
}

tasks.register<Exec>("k6PerfSoak") {
    group = "Performance"
    description = "Run K6 Soak Test (memory leak detection)"
    workingDir = perfWorkingDir
    commandLine(k6PerfBase + "/scripts/scenarios/system/soak.js")
}

tasks.register<Exec>("k6PerfInfra") {
    group = "Performance"
    description = "Run K6 Infrastructure Baseline Test"
    workingDir = perfWorkingDir
    commandLine(k6PerfBase + "/scripts/scenarios/infra_baseline.js")
}

// JMH Benchmark Configuration (FR-005)
jmh {
    fork = 2
    warmupIterations = 5
    iterations = 5
    benchmarkMode = listOf("thrpt")
    resultFormat = "JSON"
    resultsFile = project.file("build/results/jmh/results.json")
    zip64 = true
}

// build.gradle.kts — thêm bootRun JVM args
// tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
//     jvmArgs = listOf(
//         "-XX:+UseZGC",
//         "-XX:+ZGenerational",
//         "-Xms512m",
//         "-Xmx1g",
//         "-Xlog:gc*:file=tests/perf/logs/gc.log:utctime,pid,tags,level:filecount=5,filesize=20m"
//     )
// }
