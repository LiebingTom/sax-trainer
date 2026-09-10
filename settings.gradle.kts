pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    // PREFER_SETTINGS rather than FAIL_ON_PROJECT_REPOS: the Kotlin/Wasm toolchain adds the
    // Node.js and Yarn download repositories to the project itself, which FAIL_ON_PROJECT_REPOS
    // rejects outright. PREFER_SETTINGS ignores those and uses the ones declared below, so module
    // build files still cannot introduce a repository of their own.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        // Where the Kotlin/Wasm toolchain fetches Node.js and Yarn from to run the browser tests.
        // The artifact patterns are the ones the Kotlin Gradle plugin itself uses.
        exclusiveContent {
            forRepository {
                ivy("https://nodejs.org/dist") {
                    name = "Node.js distributions"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.nodejs", "node") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/yarnpkg/yarn/releases/download") {
                    name = "Yarn distributions"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.yarnpkg", "yarn") }
        }
        // Only the optimised (production) Wasm build needs this one.
        exclusiveContent {
            forRepository {
                ivy("https://github.com/WebAssembly/binaryen/releases/download") {
                    name = "Binaryen distributions"
                    patternLayout {
                        artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]")
                    }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

// Lowercase and hyphenated because the Kotlin/Wasm toolchain writes this name into the
// generated package.json, and npm rejects spaces and capitals. The app is still called
// "Sax Trainer" - that name lives in app/src/main/res/values/strings.xml.
rootProject.name = "sax-trainer"
include(":app")
include(":shell")
include(":webApp")
include(":core:designsystem")
include(":core:music")
include(":core:dsp")
include(":core:exercise")
include(":core:fingering")
include(":core:learning")
include(":core:audio")
include(":core:settings")
include(":core:testing")
include(":feature:metronome")
include(":feature:longtones")
// :feature:fingering is deprecated and deliberately not included: the Fingering Trainer is kept in
// the repository for reference only. See feature/fingering/DEPRECATED.md.
include(":feature:learning")
include(":feature:sequence")
include(":feature:notequiz")
include(":feature:notetrainer")
include(":feature:settings")

// The web audio bench: a developer page over the production :core:audio web chain, for putting a
// real instrument through it. Not part of the app; see probe/build.gradle.kts.
include(":probe")
