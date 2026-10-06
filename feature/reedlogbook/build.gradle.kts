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
        namespace = "com.example.saxtrainer.feature.reedlogbook"
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

    compilerOptions {
        // kotlin.time.Clock/Instant and kotlin.uuid.Uuid, in case either is still marked
        // experimental in the Kotlin version in use - see :core:reed.
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: the routes take the logbook's store and its session logic.
            api(project(":core:reed"))
            implementation(project(":core:designsystem"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)

            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)

            implementation(libs.kotlinx.coroutines.core)
            // A session's day and time as the player's own clock shows them.
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            // Asking for the notification permission, and nothing else.
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        wasmJsTest.dependencies {
            // Runs the logbook's screens in a browser.
            implementation(project(":core:testing"))
        }
    }
}
