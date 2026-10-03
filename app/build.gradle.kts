import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "wojtoteka.ovh.kajet"
    compileSdk = 36

    defaultConfig {
        applicationId = "wojtoteka.ovh.kajet"
        minSdk = 26
        targetSdk = 36
        versionCode = 32
        versionName = "26.10.02"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Tlumacz Pythona jest kodem natywnym. Budujemy tylko dla arm64,
        // bo wszystkie tablety z ostatnich lat maja taki procesor,
        // a kazda kolejna architektura to kilkadziesiat megabajtow wiecej.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    chaquopy {
        defaultConfig {
            version = "3.11"
        }
    }

    buildTypes {
        release {
            // Bez odchudzania R8. Plik reguł był pustym szablonem, a R8 zmieniał
            // nazwy klas, które kotlinx.serialization i natywny silnik kreski
            // (androidx.ink) odnajdują po nazwie - wydanie release psuło
            // logowanie, synchronizację i pisanie, choć debug działał.
            // Kilkanaście megabajtów więcej to uczciwa cena za działającą całość.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":storage"))
    implementation(project(":ink"))
    implementation(project(":editor"))
    implementation(project(":code"))
    implementation(project(":export"))
    implementation(project(":cloud"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Przechodzenie miedzy biblioteka, edytorem i ustawieniami
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
