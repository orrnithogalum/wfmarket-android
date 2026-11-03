plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.wfmarket.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wfmarket.app"
        // Android 12 introduced Material You dynamic color.
        minSdk = 31
        targetSdk = 36
        // Bumped together for each release. The release workflow checks the tag matches versionName.
        versionCode = 1
        versionName = "0.0.0"

        // Where the app looks for new versions. A fork points this at its own repository.
        buildConfigField("String", "UPDATE_REPOSITORY", "\"orrnithogalum/wfmarket-android\"")
    }

    signingConfigs {
        // The release key, from ~/.gradle/gradle.properties (wfmarket.signing.*) on a computer, or
        // from the WFM_SIGNING_* variables in CI. Updates only install over an app signed with the
        // same key, so every published release has to use this one.
        create("release") {
            fun value(property: String, variable: String) =
                providers.gradleProperty("wfmarket.signing.$property").orElse(providers.environmentVariable("WFM_SIGNING_$variable")).orNull
            value("storeFile", "STORE_FILE")?.let { storeFile = file(it) }
            storePassword = value("storePassword", "STORE_PASSWORD")
            keyAlias = value("keyAlias", "KEY_ALIAS")
            keyPassword = value("keyPassword", "KEY_PASSWORD")
        }
    }

    buildTypes {
        // Installs beside the release build, so it can be tested from a clean state.
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Without the release key, as when someone else builds the app, it falls back to the
            // debug key, so a release build still installs on a test phone.
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile?.exists() == true) release else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":api"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work)
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
}
