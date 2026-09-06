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
        namespace = "com.example.saxtrainer.feature.metronome"
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
        // Required by the Compose UI test below: see :core:designsystem.
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:designsystem"))
            implementation(project(":core:audio"))
            // Only for the shared monotonic reading the tap-tempo estimator measures against.
            implementation(project(":core:exercise"))
            implementation(project(":core:music"))
            // Only for the store that remembers the tempo the metronome was last started at.
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
            implementation(kotlin("test"))
        }
        wasmJsTest.dependencies {
            // Runs the metronome screen in a browser, over a stand-in engine.
            implementation(project(":core:testing"))
        }
    }
}
