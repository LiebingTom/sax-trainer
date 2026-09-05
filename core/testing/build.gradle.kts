import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "com.example.saxtrainer.core.testing"
        compileSdk = 37
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // Firefox rather than Karma's Chrome default: see :core:music for why.
            testTask { useKarma { useFirefoxHeadless() } }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Fakes stand in for these, so anyone taking a fake needs the real thing on the
            // classpath too.
            api(project(":core:audio"))
            api(project(":core:settings"))
            implementation(libs.kotlinx.coroutines.core)
        }
        wasmJsMain.dependencies {
            // For awaitInRealTime, which is about the browser's event loop rather than about
            // Compose. Only the wasmJs target has the problem it solves, so only it carries the
            // test framework - see BrowserTime.kt.
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
