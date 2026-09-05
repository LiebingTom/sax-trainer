import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// Two Compose plugins, doing different jobs. `kotlin.plugin.compose` is the compiler. The
// `org.jetbrains.compose` plugin is here for the web target: Compose on Wasm draws through Skiko,
// and this plugin is what puts Skiko's `skiko.mjs` where webpack can find it. Without it the module
// compiles for Wasm and then fails to link ("Can't resolve './skiko.mjs'").
//
// Its `compose.runtime` style dependency accessors are *not* used: they are deprecated with
// DeprecationLevel.ERROR in 1.12.0 in favour of naming the artifacts, which the version catalog
// does. Nor is its resource framework - the icons are ImageVector Kotlin and every string a literal.

kotlin {
    android {
        namespace = "com.example.saxtrainer.core.designsystem"
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
            // Firefox rather than Karma's Chrome default: see :core:music for why.
            testTask { useKarma { useFirefoxHeadless() } }
        }
        // Unusual for a library, and required by the Compose UI tests below: only an executable
        // binary is bundled by webpack, and without that bundle Skiko's runtime never reaches the
        // browser, so a Compose test loads and then finds nothing to draw on. See CMP-4906.
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:music"))
            api(project(":core:fingering"))

            // `api`, not `implementation`: every public composable here takes a Modifier and
            // composes into its caller, so no consumer can use this module without these types.
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)

            // Supplies `androidx.compose.ui.tooling.preview.Preview` for every target, so the
            // preview imports are the same ones the module already had. Not `api`: the previews
            // are this module's own, not part of what it offers.
            implementation(libs.compose.ui.tooling.preview)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        wasmJsTest.dependencies {
            // Composes the design system for real, in a browser. Compiling against it only proves
            // the types resolve; this proves a Wasm Compose target can actually run it.
            implementation(libs.compose.ui.test)
        }
    }
}
