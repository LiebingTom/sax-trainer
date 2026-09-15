import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The application itself, minus its entry point: the landing page, the navigation graph, the
 * lesson catalog and the container that holds the app's few singletons.
 *
 * This is the one module allowed to depend on features, and since M8 it depends on all of them.
 * `:app` and `:webApp` add a `main` and the platform's own implementations of the interfaces in
 * `AppContainer`, and nothing else.
 */
kotlin {
    android {
        namespace = "com.example.saxtrainer.shell"
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
            // `api`: an entry point builds an AppContainer, so it needs these types.
            api(project(":core:audio"))
            api(project(":core:learning"))
            api(project(":core:settings"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:exercise"))
            implementation(project(":core:music"))

            // The trainers, all of which the catalog configures by name: a lesson step carries the
            // trainer's own preset type, so these are part of this module's surface.
            api(project(":feature:longtones"))
            api(project(":feature:notequiz"))
            api(project(":feature:notetrainer"))
            api(project(":feature:phrasequiz"))
            api(project(":feature:phrasetrainer"))
            api(project(":feature:sequence"))
            implementation(project(":core:phrase"))
            implementation(project(":feature:learning"))
            implementation(project(":feature:metronome"))
            implementation(project(":feature:settings"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            // `api`: SaxTrainerApp hands the theme's background Color to the entry point.
            api(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)

            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            // Only so the lesson catalog can be checked against the real fingering chart.
            implementation(project(":core:fingering"))
            implementation(kotlin("test"))
        }
        wasmJsTest.dependencies {
            // Runs the whole shell in a browser: the landing page, the graph and the screens.
            implementation(project(":core:testing"))
        }
    }
}
