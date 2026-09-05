import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.example.saxtrainer.feature.sequence"
        compileSdk = 37
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTest {}
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // Firefox rather than Karma's Chrome default: see :core:music for why. The prefs that
            // give the headless run a fake microphone are in karma.config.d.
            testTask { useKarma { useFirefoxHeadless() } }
        }
        // Required by the Compose UI test below, not by the library itself: only an executable
        // binary is bundled by webpack, and without that bundle Skiko never reaches the browser.
        // See :core:designsystem for the longer version.
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            // Deliberately absent: core:learning, which this trainer must not know about,
            // and feature:fingering, whose shared pieces now live in core:audio instead.
            implementation(project(":core:audio"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:exercise"))
            implementation(project(":core:music"))
            implementation(project(":core:settings"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)

            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)

            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        wasmJsTest.dependencies {
            // Runs the trainer in a browser: the ViewModel, its state and the screen, together.
            implementation(libs.compose.ui.test)
        }
    }
}
