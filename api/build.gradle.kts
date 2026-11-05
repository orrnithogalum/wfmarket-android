plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    testImplementation(libs.junit)
}

// The Live* tests hit the real warframe.market API, so they only run when asked for with -Plive:
// CI runs them on a schedule, to catch the site changing under the app, and not on every push.
tasks.test {
    useJUnit()
    if (!providers.gradleProperty("live").isPresent) filter { excludeTestsMatching("*Live*") }
    outputs.upToDateWhen { false }
    testLogging { showStandardStreams = true; events("passed", "failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline-${project.name}.xml")
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        sarif.required.set(true)
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}
