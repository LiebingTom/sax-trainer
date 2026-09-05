import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

/**
 * M3: the throwaway Web Audio probe.
 *
 * Not production code and not a step towards `:webApp`. It exists to answer one question - can a
 * browser microphone reach the shared DSP fast enough - and it should be deleted once the answer is
 * written down. It deliberately depends on `:core:dsp` and `:core:music` only: `:core:audio` is
 * still Android-only and M3 must not change that.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    compilerOptions {
        // Every line of this module talks to JavaScript; opting in once beats 40 annotations.
        optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "probe"
        browser {
            commonWebpackConfig {
                outputFileName = "probe.js"
            }
            // Firefox rather than Karma's Chrome default: see :core:music for why. The prefs that
            // give the headless run a fake microphone are in karma.config.d.
            testTask { useKarma { useFirefoxHeadless() } }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:dsp"))
            implementation(project(":core:music"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.browser)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

/**
 * Puts the worklet's JavaScript into a Kotlin constant as well as into the served resources.
 *
 * The page loads the file over HTTP; the headless test has no server to load it from, so it makes a
 * blob URL out of this constant instead. Generating it means both run the same worklet rather than
 * a copy that could drift away from it.
 */
val generateWorkletSource by tasks.registering {
    val worklet = layout.projectDirectory.file("src/wasmJsMain/resources/capture-worklet.js")
    val output = layout.buildDirectory.dir("generated/worklet")
    inputs.file(worklet)
    outputs.dir(output)
    doLast {
        val source = worklet.asFile.readText()
        val terminator = "\"\"\""
        require(!source.contains(terminator)) { "the worklet cannot contain a raw string terminator" }
        val destination = output.get().asFile.resolve("com/example/saxtrainer/probe/WorkletSource.kt")
        destination.parentFile.mkdirs()
        destination.writeText(
            buildString {
                appendLine("package com.example.saxtrainer.probe")
                appendLine()
                appendLine("/** Generated from src/wasmJsMain/resources/capture-worklet.js. Do not edit. */")
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
