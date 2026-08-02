import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "wojtoteka.ovh.kajet.ink"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core"))

    // Silnik kreski: nacisk, pochylenie, wygladzanie i przewidywanie ruchu rysika
    api(libs.androidx.ink.authoring)
    api(libs.androidx.ink.brush)
    api(libs.androidx.ink.geometry)
    api(libs.androidx.ink.rendering)
    api(libs.androidx.ink.strokes)

    // Rysowanie po froncie bufora, potrzebne do niskiego opoznienia kreski
    implementation(libs.androidx.graphics.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
