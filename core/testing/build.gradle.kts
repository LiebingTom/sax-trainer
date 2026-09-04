plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.saxtrainer.core.testing"
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
    // Fakes stand in for these, so anyone taking a fake needs the real thing on the classpath too.
    api(project(":core:audio"))
    api(project(":core:settings"))
    implementation(libs.kotlinx.coroutines.core)
}
