plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.saxtrainer.core.audio"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Capture and playback on their own need neither, but the metronome keeps musical time and the
    // note listener reports pitches, so both belong to callers of this module as well.
    api(project(":core:music"))
    api(project(":core:dsp"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
