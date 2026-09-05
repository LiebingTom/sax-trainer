plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.saxtrainer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.saxtrainer"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // The application itself lives in :shell. What is left here is the Android entry point: an
    // Activity, an Application that builds the container out of Android's own implementations,
    // and the manifest.
    implementation(project(":shell"))
    implementation(project(":core:audio"))
    implementation(project(":core:settings"))
    // setContent and enableEdgeToEdge - the whole of MainActivity. Compose itself arrives with
    // :shell, which is where every screen is; the BOM aligns what activity-compose pulls in with it.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    // The layout inspector and the @Preview renderer, in debug builds only.
    debugImplementation(libs.androidx.compose.ui.tooling)
}
