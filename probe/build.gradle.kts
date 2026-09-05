import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

/**
 * The web audio bench: a page that runs the production `:core:audio` web chain and shows its
 * numbers.
 *
 * It was M3's throwaway probe, with a capture, a worklet and a copy of the listener's decision of
 * its own. M4 promoted all three into `:core:audio` and deleted them from here, so what is left is
 * a page over the real thing.
 *
 * It stays because it is the only way to put a real saxophone through the web chain: the browser
 * tests measure the same numbers on every run, but a headless browser has a fake microphone and no
 * instrument. It is a developer page, reached by running `:probe:wasmJsBrowserDevelopmentRun`, and
 * is deliberately not part of the app.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        compilerOptions {
            // window.setInterval takes a JavaScript callback.
            optIn.add("kotlin.js.ExperimentalWasmJsInterop")
        }
        outputModuleName = "probe"
        browser {
            commonWebpackConfig {
                outputFileName = "probe.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:audio"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.browser)
        }
    }
}
