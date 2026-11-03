plugins {
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
