plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

ksp {
    // Room schema history (plan §3.5): migrations are tested from day one.
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "se.anders.athenapodcaster"
    compileSdk = 37

    defaultConfig {
        applicationId = "se.anders.athenapodcaster"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Room migration tests read the exported schema history.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    buildFeatures {
        compose = true
        buildConfig = true // the debug-only sample-audio button (T3.5)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    // Settings export/import (FR-065): a small, pure-Kotlin JSON library that is also testable on the JVM.
    implementation(libs.kotlinx.serialization.json)

    // Episode and feed database (plan §1).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Periodic refresh and downloads that survive process death and reboot (plan §3.4).
    implementation(libs.androidx.work.runtime)
    // Playback, MediaSession, notification and lock-screen controls (plan §3.1).
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    // Feed fetching: conditional GET, redirects, gzip (plan §1).
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.kxml2)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.okhttp.mockwebserver3)
}
