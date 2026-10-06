import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

// The reed logbook: which reeds there are, what became of them, and every session played on one.
// No Compose here, like :core:settings - the rules, the stored format and the export are plain
// Kotlin, so they can be tested without a screen.

kotlin {
    android {
        namespace = "com.example.saxtrainer.core.reed"
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

    compilerOptions {
        // kotlin.time.Clock/Instant and kotlin.uuid.Uuid: the wall clock a session is timed by and
        // the ids a reed is known by. Opted in once for the module, in case either is still marked
        // experimental in the Kotlin version in use.
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            // StateFlow is in ReedLogbookRepository's own signature, so consumers need it too.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            // The edit {} builder, and nothing else.
            implementation(libs.androidx.core.ktx)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}
