import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "com.example.saxtrainer.core.music"
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
            testTask {
                useKarma {
                    // Karma defaults to Chrome, which is not installed here. Firefox is, it has
                    // supported WasmGC since 120, and running the shared code on a second engine is
                    // worth more than running it on the same one the app will use.
                    useFirefoxHeadless()
                    // The launcher's prefs and the timeouts, in one place for every module.
                    useConfigDirectory(rootDir.resolve("gradle/karma"))
                }
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
