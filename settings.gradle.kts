rootProject.name = "muhabbet"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io")
        // Signal's own Maven repo. As of 2026, libsignal (libsignal-android /
        // libsignal-client) is published ONLY here for versions > 0.86.5;
        // Maven Central is frozen at 0.86.5. Required as a prerequisite for any
        // future libsignal upgrade. See CLAUDE.md → "libsignal upgrade (BLOCKED)".
        maven("https://build-artifacts.signal.org/libraries/maven/")
    }
}

include(":backend")
include(":shared")
if (System.getenv("SKIP_MOBILE") != "true") {
    include(":mobile:composeApp")
}
