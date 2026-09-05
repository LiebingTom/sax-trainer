import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "com.example.saxtrainer.core.testing"
        compileSdk = 37
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
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
    }

    sourceSets {
        commonMain.dependencies {
            // Fakes stand in for these, so anyone taking a fake needs the real thing on the
            // classpath too.
            api(project(":core:audio"))
            api(project(":core:settings"))
            implementation(libs.kotlinx.coroutines.core)
        }
        wasmJsMain.dependencies {
            // The browser half of the fixtures: awaitInRealTime, which is about the browser's event
            // loop rather than about Compose, and the scaffolding every screen test puts around a
            // screen. Only the wasmJs target runs Compose UI tests, so only it carries these.
            api(libs.compose.ui.test)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            // runScreenTest hands back the TestResult runComposeUiTest returns, which is
            // kotlinx.coroutines.test's - a browser test cannot block, so it is a promise.
            api(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
