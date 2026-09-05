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

    // Scaffolding, not a target this project wants. :core:exercise and :core:fingering are still
    // plain kotlin-jvm modules, and a kotlin-jvm consumer asks for platform.type=jvm, which the
    // android variant does not answer. M2 converts both of them, and this target goes with them.
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                useKarma {
                    // Karma defaults to Chrome, which is not installed here. Firefox is, it has
                    // supported WasmGC since 120, and running the shared code on a second engine is
                    // worth more than running it on the same one the app will use.
                    useFirefoxHeadless()
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
