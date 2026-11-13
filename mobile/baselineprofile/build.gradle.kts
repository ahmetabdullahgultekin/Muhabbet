plugins {
    id("com.android.test")
    kotlin("android")
}

/*
 * Generates the app's baseline profile and measures cold start (#867).
 *
 * A `com.android.test` module, instrumenting `:mobile:composeApp`'s `benchmark` build type — the
 * release build with R8, signed with the debug key so it installs without the release keystore.
 * The `androidx.baselineprofile` Gradle plugin would automate the copy step below, but it does not
 * support a Kotlin Multiplatform application module on this AGP, so the flow is the plain one the
 * plugin wraps:
 *
 *   ./gradlew :mobile:baselineprofile:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.muhabbet.baselineprofile.BaselineProfileGenerator
 *   adb pull the generated `*-baseline-prof.txt` from the device's additional test output, and
 *   commit it as mobile/composeApp/src/androidMain/baseline-prof.txt.
 *
 * DEVICE-PENDING: both tests need an emulator or a phone (API 28+, rooted or userdebug for
 * profile generation; any API 29+ device for the startup benchmark). The CI host has no KVM, so
 * nothing here runs in CI; it compiles there so it cannot rot.
 */
android {
    namespace = "com.muhabbet.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Must exist here too: a test module only produces variants for build types its target has,
        // and it instruments the minified, non-debuggable build — profiling a debug build measures
        // the wrong code.
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":mobile:composeApp"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}
