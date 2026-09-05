import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "com.example.saxtrainer.core.learning"
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
    }

    sourceSets {
        commonMain.dependencies {
            // Progress is observed as a StateFlow; nothing here needs Android or a trainer.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
