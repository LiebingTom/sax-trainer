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
        namespace = "com.example.saxtrainer.feature.notequiz"
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
            // Firefox rather than Karma's Chrome default: see :core:music for why. The
            // launcher, its fake microphone and the timeouts are in gradle/karma, shared by
            // every module rather than copied into each one.
            testTask {
                useKarma {
                    useFirefoxHeadless()
                    useConfigDirectory(rootDir.resolve("gradle/karma"))
                }
            }
        }
        // Required by the Compose UI test below, not by the library itself: only an executable
        // binary is bundled by webpack, and without that bundle Skiko never reaches the browser.
        // See :core:designsystem for the longer version.
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            // Deliberately absent: core:audio and core:dsp. This quiz is played with the eyes, and
            // the build is what guarantees it never asks for a microphone. Also absent:
            // core:learning, which no trainer may know about.
            implementation(project(":core:designsystem"))
            implementation(project(":core:fingering"))
            implementation(project(":core:music"))
            implementation(project(":core:settings"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)

            // The JetBrains fork, under the same `androidx.lifecycle` package names: ViewModel,
            // viewModelScope, viewModel {}, collectAsStateWithLifecycle and LifecycleEventEffect
            // all exist on wasmJs through it, so not one import in this module changed.
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)

            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
