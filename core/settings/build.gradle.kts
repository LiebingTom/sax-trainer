plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.saxtrainer.core.settings"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:music"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    // The persistence test runs on a device: SharedPreferences is the thing under test, so a fake
    // would only prove that the fake works.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestRuntimeOnly(libs.androidx.test.runner)
}
