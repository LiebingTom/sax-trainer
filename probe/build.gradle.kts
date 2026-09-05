import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

/**
 * The web audio bench: a page that runs the production `:core:audio` web chain and shows its
 * numbers.
 *
 * It was M3's throwaway probe, with a capture, a worklet and a copy of the listener's decision of
 * its own. M4 promoted all three into `:core:audio` and deleted them from here, so what is left is
 * a page over the real thing. It stays until M9 folds the same readout into an in-app diagnostics
 * screen, because it is the only way to put a real saxophone through the web chain: a headless
 * browser has a fake microphone and no instrument.
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
