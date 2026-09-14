import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

/**
 * The web entry point: a `main`, a page to put it on, and the browser's implementations of the
 * three interfaces `AppContainer` cannot fill in for itself. Everything else is in `:shell`.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "webApp"
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
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
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shell"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            // The page background and color-scheme, which follow the app's theme.
            implementation(libs.kotlinx.browser)
        }
        wasmJsTest.dependencies {
            // Boots the page's own wiring in a browser - see WebEntryPointTest.
            implementation(project(":core:testing"))
            implementation(kotlin("test"))
        }
    }
}
