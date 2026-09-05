import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    // Only for rememberMicrophoneAccess in androidMain: asking for permission is something a screen
    // does, and on Android the ask goes through a Compose-scoped launcher. Nothing here draws.
    alias(libs.plugins.kotlin.compose)
}

/**
 * The Compose compiler runs on the Android target only.
 *
 * The one composable here is `rememberMicrophoneAccess`, which is Android's way of asking for a
 * permission and lives in `androidMain`. Left to itself the plugin also processes the Wasm
 * compilation and then fails it for having no Compose runtime on the class path.
 *
 * Revisited in M5, when Compose Multiplatform arrived: the restriction stays, because it is a
 * statement about *this* module rather than about the project. Nothing in `commonMain` or
 * `wasmJsMain` here draws, and lifting the restriction would mean putting Compose on the web
 * audio class path to satisfy a compiler pass over code that has no composables in it. It comes
 * off when the permission abstraction becomes an `expect @Composable` and the module genuinely has
 * shared Compose code - see [com.example.saxtrainer.core.audio.capture.MicrophoneAccess].
 */
composeCompiler {
    targetKotlinPlatforms.set(setOf(KotlinPlatformType.androidJvm))
}

kotlin {
    android {
        namespace = "com.example.saxtrainer.core.audio"
        compileSdk = 37
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTest {}
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        compilerOptions {
            // WebAudioCapture, WebMetronome and the JS surface they share are the whole of the
            // JavaScript interop here; opting in once beats annotating every declaration. The marker
            // does not exist on the Android target, so it is set on this one rather than on both.
            optIn.add("kotlin.js.ExperimentalWasmJsInterop")
        }
        browser {
            // Firefox rather than Karma's Chrome default: see :core:music for why. The prefs that
            // give the headless run a fake microphone are in karma.config.d.
            testTask { useKarma { useFirefoxHeadless() } }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Capture and playback on their own need neither, but the metronome keeps musical time
            // and the note listener reports pitches, so both belong to callers of this module too.
            api(project(":core:music"))
            api(project(":core:dsp"))
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            // ContextCompat.checkSelfPermission, and nothing else.
            implementation(libs.androidx.core.ktx)
            implementation(dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui)
            implementation(libs.androidx.activity.compose)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}

/**
 * Puts the capture worklet's JavaScript into a Kotlin constant.
 *
 * Kotlin/Wasm cannot run inside an AudioWorklet, so this one file has to be JavaScript. Compiling it
 * in rather than shipping it as a resource means `:core:audio` is self-contained: no consumer has to
 * copy a `.js` file into its own resources and keep it in step, and the headless browser test runs
 * exactly the worklet the app runs.
 */
val generateWorkletSource = tasks.register("generateWorkletSource") {
    val worklet = layout.projectDirectory.file("src/wasmJsMain/worklet/capture-worklet.js")
    val output = layout.buildDirectory.dir("generated/worklet")
    inputs.file(worklet)
    outputs.dir(output)
    doLast {
        val source = worklet.asFile.readText()
        val terminator = "\"\"\""
        require(!source.contains(terminator)) { "the worklet cannot contain a raw string terminator" }
        val destination = output.get().asFile
            .resolve("com/example/saxtrainer/core/audio/web/CaptureWorkletSource.kt")
        destination.parentFile.mkdirs()
        destination.writeText(
            buildString {
                appendLine("package com.example.saxtrainer.core.audio.web")
                appendLine()
                appendLine("/** Generated from src/wasmJsMain/worklet/capture-worklet.js. Do not edit. */")
                appendLine("internal const val CAPTURE_WORKLET_SOURCE: String = $terminator")
                append(source.replace("$", "\${'$'}"))
                appendLine(terminator)
            },
        )
    }
}

kotlin.sourceSets.named("wasmJsMain") {
    kotlin.srcDir(generateWorkletSource)
}
