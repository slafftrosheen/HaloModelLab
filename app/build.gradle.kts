plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.slafftrosheen.halomodellab"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.slafftrosheen.halomodellab"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-l0"

        // No testInstrumentationRunner yet: HML-L0 ships source-level unit tests only.
    }

    buildTypes {
        release {
            // No signing config is requested or committed. Debug keys only, by the platform.
            isMinifyEnabled = false
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// With AGP 9 built-in Kotlin, jvmTarget follows android.compileOptions.targetCompatibility;
// no separate kotlin{} compiler block is needed.

// HML-L0 intentionally has no network, analytics, inference, storage or export dependencies.
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
