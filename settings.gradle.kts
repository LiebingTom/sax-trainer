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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Sax Trainer"
include(":app")
include(":core:designsystem")
include(":core:music")
include(":core:dsp")
include(":core:exercise")
include(":core:fingering")
include(":core:audio")
include(":core:settings")
include(":feature:metronome")
include(":feature:longtones")
include(":feature:fingering")
include(":feature:settings")
