import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    // Only for rememberMicrophoneAccess: asking for permission is something a screen does, and on
    // Android the ask goes through a Compose-scoped launcher, so it has to be a composable. Nothing
    // in this module draws.
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
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

            // For rememberMicrophoneAccess and nothing else. The runtime is the whole of it: there
            // is no drawing here, so neither foundation nor material3 is on this class path.
            implementation(libs.compose.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            // ContextCompat.checkSelfPermission, and nothing else.
            implementation(libs.androidx.core.ktx)
            // LocalContext, and the permission launcher the prompt is delivered to.
            implementation(libs.compose.ui)
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
