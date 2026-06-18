plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group   = "com.example.testing"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// ── Versions ──────────────────────────────────────────────────────────────────
val mockitoVersion  = "5.15.2"
val assertjVersion  = "3.27.2"
val karateVersion   = "1.5.1"
val jacksonVersion  = "2.18.2"

// ── Dependencies ──────────────────────────────────────────────────────────────
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("io.karatelabs:karate-junit5:$karateVersion")
}

// ── Test source sets ──────────────────────────────────────────────────────────
// Karate .feature files and karate-config.js live under src/test/java.
// Gradle only copies src/test/resources to the test classpath by default,
// so we add the Java source tree as an extra resource directory to match
// the Maven <testResources> block in pom.xml.
sourceSets {
    test {
        resources {
            srcDirs("src/test/resources", "src/test/java")
            include("**/*.feature", "**/karate-config.js", "**/*.json", "**/*.xml", "**/*.properties", "**/*.yml")
        }
    }
}

tasks.named<Copy>("processTestResources") {
    // Both src/test/resources and src/test/java may contain the same JSON/feature files.
    // Keep the first copy encountered (src/test/resources takes precedence).
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// ── Helper: read -P<profile> from the Gradle command line ─────────────────────
// Usage: ./gradlew test -Pprofile=component
//        ./gradlew karateSmoke -Pprofile=smoke -Pservice.base-url=http://localhost:8080
val profile: String = findProperty("profile")?.toString() ?: "component"

// ── Task: component / unit tests (surefire equivalent) ───────────────────────
tasks.named<Test>("test") {
    useJUnitPlatform()
    description = "Run unit and component tests (excludes Karate runners)."
    group       = "verification"

    // Mirror Maven surefire: include all *Test.java, exclude Karate runners
    include("**/*Test.class")
    exclude("**/KarateSmokeTestRunner.class", "**/KarateProdSmokeRunner.class")

    // Forward karate.env / karate.options if supplied on the command line
    systemProperty("karate.env",     findProperty("karate.env")?.toString()     ?: "")
    systemProperty("karate.options", findProperty("karate.options")?.toString() ?: "")

    // Skip when profile=smoke or profile=prod-smoke (matches Maven behaviour)
    onlyIf { profile !in listOf("smoke", "prod-smoke") }
}

// ── Task: Karate smoke tests (failsafe equivalent) ────────────────────────────
val karateSmoke by tasks.registering(Test::class) {
    useJUnitPlatform()
    description = "Run Karate smoke tests against a deployed service (-Pservice.base-url=...)."
    group       = "verification"

    include("**/KarateSmokeTestRunner.class")

    systemProperty("karate.env",      findProperty("karate.env")?.toString()      ?: "qa")
    systemProperty("karate.options",  findProperty("karate.options")?.toString()   ?: "--tags @smoke")
    systemProperty("service.base-url",findProperty("service.base-url")?.toString() ?: "http://localhost:8080")
    systemProperty("auth.token",      findProperty("auth.token")?.toString()        ?: "local-token")

    // Runs after unit/component tests (mirrors Maven verify lifecycle)
    shouldRunAfter(tasks.named("test"))
}

// ── Task: Karate prod-smoke (production-safe read-only tests only) ────────────
val karateProdSmoke by tasks.registering(Test::class) {
    useJUnitPlatform()
    description = "Run @prod-safe Karate tests (read-only, safe against live production)."
    group       = "verification"

    include("**/KarateProdSmokeRunner.class")

    systemProperty("karate.env",       findProperty("karate.env")?.toString()       ?: "production")
    systemProperty("service.base-url", findProperty("service.base-url")?.toString() ?: "http://localhost:8080")
    systemProperty("auth.token",       findProperty("auth.token")?.toString()        ?: "local-token")

    shouldRunAfter(tasks.named("test"))
}

// ── Profile shortcuts ─────────────────────────────────────────────────────────
// These mirror `mvn test -Punit`, `mvn test -Pcomponent`, `mvn verify -Psmoke`, etc.
//
//   ./gradlew unitTests                                    → unit tests only
//   ./gradlew componentTests                               → unit + component tests
//   ./gradlew smokeTests -Pservice.base-url=http://...     → Karate smoke
//   ./gradlew prodSmokeTests -Pservice.base-url=https://.. → Karate prod-smoke
//   ./gradlew allTests -Pservice.base-url=http://...       → everything

val unitTests by tasks.registering {
    description = "Profile alias: run unit tests only (mirrors -Punit)."
    group       = "verification"
    dependsOn(tasks.named("test"))
}

val componentTests by tasks.registering {
    description = "Profile alias: run unit + component tests (mirrors -Pcomponent)."
    group       = "verification"
    dependsOn(tasks.named("test"))
}

val smokeTests by tasks.registering {
    description = "Profile alias: run Karate smoke tests (mirrors -Psmoke)."
    group       = "verification"
    dependsOn(karateSmoke)
}

val prodSmokeTests by tasks.registering {
    description = "Profile alias: run Karate prod-safe tests (mirrors -Pprod-smoke)."
    group       = "verification"
    dependsOn(karateProdSmoke)
}

val allTests by tasks.registering {
    description = "Profile alias: run all tests — component + Karate smoke (mirrors -Pall)."
    group       = "verification"
    dependsOn(tasks.named("test"), karateSmoke)
}
