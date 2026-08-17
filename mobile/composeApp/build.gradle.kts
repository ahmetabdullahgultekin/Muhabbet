plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
    id("com.google.gms.google-services")
    id("app.cash.sqldelight")
}

group = "com.muhabbet"
version = "0.1.0-SNAPSHOT"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    // iosX64 (Intel simulator) dropped: Compose Multiplatform 1.11.1 no longer publishes for it,
    // so commonMain/iosMain/appleMain cannot resolve compose.runtime for that platform. Apple
    // Silicon simulators use iosSimulatorArm64.
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val decompose = "3.5.0"
        val koin = "4.2.2"
        val ktor = "3.5.2"
        val coil = "3.5.0"

        commonMain.dependencies {
            // Shared KMP module
            implementation(project(":shared"))

            // Muhabbet's visual language. One-way dependency: the design system cannot see this
            // module, which is what keeps components from reaching into screens or repositories.
            implementation(project(":mobile:designsystem"))

            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)

            // Navigation — Decompose
            implementation("com.arkivanov.decompose:decompose:$decompose")
            implementation("com.arkivanov.decompose:extensions-compose:$decompose")

            // DI — Koin
            implementation("io.insert-koin:koin-core:$koin")
            implementation("io.insert-koin:koin-compose:$koin")

            // HTTP — Ktor
            implementation("io.ktor:ktor-client-core:$ktor")
            implementation("io.ktor:ktor-client-content-negotiation:$ktor")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
            implementation("io.ktor:ktor-client-logging:$ktor")
            implementation("io.ktor:ktor-client-auth:$ktor")
            implementation("io.ktor:ktor-client-websockets:$ktor")

            // Serialization
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")

            // Image loading — Coil
            implementation("io.coil-kt.coil3:coil-compose:$coil")
            implementation("io.coil-kt.coil3:coil-network-ktor3:$coil")

            // Local DB — SQLDelight
            implementation("app.cash.sqldelight:coroutines-extensions:2.3.2")
        }

        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktor")
            implementation("androidx.activity:activity-compose:1.13.0")
            implementation("androidx.security:security-crypto:1.1.0")
            implementation("app.cash.sqldelight:android-driver:2.3.2")

            // Reads the EXIF Orientation tag before we decode+re-encode photos, so compression
            // doesn't silently discard the rotation a phone recorded (#408). Works below API 24,
            // unlike android.media.ExifInterface.
            implementation("androidx.exifinterface:exifinterface:1.4.2")

            // WorkManager — background sync
            implementation("androidx.work:work-runtime-ktx:2.11.2")

            // Firebase Auth (Phone verification) + Cloud Messaging
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:34.17.0"))
            implementation("com.google.firebase:firebase-auth")
            implementation("com.google.firebase:firebase-messaging")

            // Sentry — crash reporting
            implementation("io.sentry:sentry-android:8.53.0")

            // LiveKit — WebRTC voice/video calls
            implementation("io.livekit:livekit-android:2.28.0")

            // Signal Protocol — E2E encryption (X3DH + Double Ratchet)
            // libsignal is NOT a dependency while E2E is off. Every Signal source file is
            // *.kt.disabled and PlatformModule.android.kt wires NoOpKeyManager/NoOpEncryption, so
            // nothing in the build references it — yet it contributed ~400 MB of native libraries
            // per release, four ABIs of a library the app never calls.
            //
            // RESTORE THIS LINE in the same change that re-enables the *.kt.disabled files. See
            // CLAUDE.md -> "libsignal upgrade (BLOCKED)"; the pinned API also needs a rewrite before
            // those files compile.
            // implementation("org.signal:libsignal-android:0.100.0")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            implementation("io.ktor:ktor-client-mock:$ktor")
            implementation("io.insert-koin:koin-test:$koin")
        }

        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktor")
            implementation("app.cash.sqldelight:native-driver:2.3.2")
        }
    }
}

android {
    namespace = "com.muhabbet.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.muhabbet.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.3.5"

        // Sentry DSN — set via environment variable or local.properties
        manifestPlaceholders["SENTRY_DSN"] = System.getenv("SENTRY_DSN") ?: ""
    }

    signingConfigs {
        create("release") {
            // Set via environment variables or local.properties:
            //   MUHABBET_KEYSTORE_FILE, MUHABBET_KEYSTORE_PASSWORD,
            //   MUHABBET_KEY_ALIAS, MUHABBET_KEY_PASSWORD
            val keystoreFile = System.getenv("MUHABBET_KEYSTORE_FILE")
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("MUHABBET_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MUHABBET_KEY_ALIAS")
                keyPassword = System.getenv("MUHABBET_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystoreFile = System.getenv("MUHABBET_KEYSTORE_FILE")
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        // libsignal-android ships a *testing* variant of its native library alongside the real one,
        // for every ABI. That is ~477 MB of binaries whose only purpose is libsignal's own test
        // suite, and it was going out to users. It also ships macOS .dylib and Windows .dll files
        // that cannot run on Android at all.
        //
        // Together these were the bulk of a 991 MB release APK — for a messenger that does not
        // currently call libsignal, since E2E is off.
        jniLibs {
            excludes += setOf("**/libsignal_jni_testing.so")
        }
        resources {
            excludes += setOf(
                "**/*.dylib",
                "**/*.dll",
                "**/*.so.debug",
            )
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG is the only honest source for the debug flag; without it the
        // release build would keep logging like a debug build.
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}

sqldelight {
    databases {
        create("MuhabbetDatabase") {
            packageName.set("com.muhabbet.app.db")
        }
    }
}

/**
 * BuildInfo.kt duplicates the version so commonMain can show it without depending on the Android
 * BuildConfig. Duplication drifts: Gradle once said 0.1.0 while the settings screen told users
 * 1.0.0. This fails the build instead.
 */
val verifyBuildInfoVersion by tasks.registering {
    val buildInfo = layout.projectDirectory.file("src/commonMain/kotlin/com/muhabbet/app/BuildInfo.kt")
    val expectedName = android.defaultConfig.versionName
    val expectedCode = android.defaultConfig.versionCode
    inputs.file(buildInfo)
    inputs.property("versionName", expectedName ?: "")
    inputs.property("versionCode", expectedCode ?: 0)
    outputs.upToDateWhen { true }
    doLast {
        val text = buildInfo.asFile.readText()
        val name = Regex("""VERSION\s*=\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
        val code = Regex("""VERSION_CODE\s*=\s*(\d+)""").find(text)?.groupValues?.get(1)?.toInt()
        check(name == expectedName) {
            "BuildInfo.VERSION is $name but build.gradle.kts versionName is $expectedName"
        }
        check(code == expectedCode) {
            "BuildInfo.VERSION_CODE is $code but build.gradle.kts versionCode is $expectedCode"
        }
    }
}

tasks.named("check") { dependsOn(verifyBuildInfoVersion) }
