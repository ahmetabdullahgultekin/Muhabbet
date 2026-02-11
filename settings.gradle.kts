rootProject.name = "muhabbet"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":backend")
include(":shared")
if (System.getenv("SKIP_MOBILE") != "true") {
    include(":mobile:composeApp")
}
