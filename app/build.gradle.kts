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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    // The application itself lives in :shell. What is left here is the Android entry point, and
    // the four microphone trainers, which are Android-only until M8 and so cannot be registered
    // from a module that also builds for the web.
    implementation(project(":shell"))
    implementation(project(":core:audio"))
    // The launch types a lesson step carries are written in core terms; these are them.
    implementation(project(":core:exercise"))
    implementation(project(":core:learning"))
    implementation(project(":core:music"))
    implementation(project(":core:settings"))
    implementation(project(":feature:fingering"))
    implementation(project(":feature:longtones"))
    implementation(project(":feature:notetrainer"))
    implementation(project(":feature:sequence"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Only so the lesson outcome mapping can be checked against the trainers' own result types.
    testImplementation(project(":feature:notequiz"))
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
