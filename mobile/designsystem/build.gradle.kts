plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.library")
}

group = "com.muhabbet"
version = "0.1.0-SNAPSHOT"

/*
 * Muhabbet's visual language, as a library.
 *
 * The point of the module boundary is that it only points one way: designsystem cannot see
 * composeApp, so a component here can never reach a screen, a repository, a navigation component or
 * the app's string resources. That was previously a convention, and the convention lost — 4 shared
 * components against 26 hand-rolled top bars in three different colours.
 *
 * Two rules follow from it, both enforced by `verifyDesignSystem`:
 *   1. No strings. User-visible text is a parameter supplied by the caller.
 *   2. No navigation, no Koin, no repositories, no domain types. Compose and its own tokens only.
 *
 * Deliberately NOT copied from composeApp: com.android.application, google-services, sqldelight,
 * plugin.serialization, applicationId/versionCode/versionName, signingConfigs, the release
 * buildType, the libsignal-specific packaging block, buildConfig, and verifyBuildInfoVersion (it
 * reads android.defaultConfig.versionName, which does not exist on a library).
 */
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    // Must match composeApp's target set exactly, or the dependency will not resolve for iOS.
    // iosX64 is absent there because Compose Multiplatform 1.11.1 no longer publishes for it.
    iosArm64()
    iosSimulatorArm64()

    // No binaries.framework here on purpose. There is no Xcode project in this repo, and
    // composeApp's framework is `isStatic = true`, so everything reachable from it — including this
    // module — is linked into ComposeApp.framework already. `export()` only affects Obj-C/Swift
    // header visibility and would additionally require `api(...)`; nothing Swift consumes these
    // classes. Declaring a second framework would only add link tasks nobody runs.

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)

            // Image loading is part of the visual language: UserAvatar is the most-reused
            // component in the app. The Ktor network layer stays in composeApp.
            implementation("io.coil-kt.coil3:coil-compose:3.5.0")
        }

        androidMain.dependencies {
            // SystemBarsEffect's actual needs LocalActivity (activity-compose) and
            // WindowInsetsControllerCompat (core-ktx). Both reached composeApp transitively; a
            // library must declare what it actually uses.
            implementation("androidx.activity:activity-compose:1.13.0")
            implementation("androidx.core:core-ktx:1.18.0")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.muhabbet.designsystem"
    // 36 to match composeApp. (`shared` is still on 35; that is not a precedent to follow here,
    // since this module is compiled into the app and should see the same platform APIs.)
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
