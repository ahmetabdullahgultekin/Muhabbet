import java.util.zip.ZipFile

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

// ── App version: ONE source (#863) ───────────────────────────────────────────────────────────────
//
// The version used to be hand-kept in three places — `versionName`/`versionCode` below, the
// constants in a hand-written `BuildInfo.kt`, and the release tag — held together by a regex over
// source text and by someone remembering to bump `versionCode` before every Play upload. A tag on a
// commit that still said 0.3.10 published an AAB that reported 0.3.10 in-app under a 0.3.11 release.
//
// Now there is one input, the Gradle property `muhabbet.versionName`:
//   * local and CI builds read the default from the root `gradle.properties`;
//   * `Mobile Release` passes `-Pmuhabbet.versionName=<tag without the v>`, so the tag IS the
//     version and tagging needs no source edit;
//   * `versionCode` is computed from it (see [appVersionCodeOf]) so it rises with every release
//     and nobody bumps it by hand;
//   * `BuildInfo` is GENERATED from it into build/ (see `generateBuildInfo`), so commonMain cannot
//     disagree with the APK manifest — there is no second copy left to drift.

/**
 * MAJOR.MINOR.PATCH, nothing else. A suffix (`-rc1`) is refused: Play needs a distinct versionCode
 * per upload, and `0.3.11-rc1` and `0.3.11` would otherwise compute the same one.
 */
val appVersionPattern = Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d?)\.(0|[1-9]\d?)$""")

/**
 * `major * 10000 + minor * 100 + patch` — monotonic in semantic-version order as long as minor and
 * patch stay below 100, which the pattern enforces. 0.3.10 → 310, which is above the last
 * hand-assigned code (15), so the switch to the formula is itself a valid Play upgrade.
 */
fun appVersionCodeOf(versionName: String): Int {
    val match = appVersionPattern.matchEntire(versionName)
        ?: throw GradleException(
            "muhabbet.versionName='$versionName' is not MAJOR.MINOR.PATCH (minor and patch < 100). " +
                "Release tags must look like v0.3.11."
        )
    val (major, minor, patch) = match.destructured
    val code = major.toLong() * 10_000 + minor.toLong() * 100 + patch.toLong()
    if (code > 2_100_000_000L) throw GradleException("versionCode $code exceeds Play's limit")
    return code.toInt()
}

val appVersionName: String = providers.gradleProperty("muhabbet.versionName").orNull
    ?: throw GradleException("muhabbet.versionName is not set — it lives in the root gradle.properties")
val appVersionCode: Int = appVersionCodeOf(appVersionName)

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

            // Cold start (#867). core-splashscreen back-ports the SplashScreen API to API 26-30 and
            // hands the window to the real theme at the first frame. profileinstaller installs the
            // baseline profiles that Compose, Decompose and the other libraries already ship, plus
            // the app's own once :mobile:baselineprofile generates one — without it a sideloaded or
            // non-Play install runs Compose interpreted/JIT on its first launches.
            implementation("androidx.core:core-splashscreen:1.0.1")
            implementation("androidx.profileinstaller:profileinstaller:1.4.1")

            // App Lock (#378) — BiometricPrompt for fingerprint/face, falling back to the device's
            // own screen-lock PIN/pattern/password when no biometric is enrolled. 1.1.0 is the
            // latest STABLE release; every version after it (up to 1.4.0-alpha07) is still alpha.
            // Pulls in androidx.fragment transitively — BiometricPrompt hosts a headless Fragment
            // internally, which is also why MainActivity now extends FragmentActivity.
            implementation("androidx.biometric:biometric:1.1.0")
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
            // Task.await() — bridges every Play-services Task so a failure throws rather than
            // leaving a hand-written listener that never resumes (#860).
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

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
        // Both derived from muhabbet.versionName — see the top of this file. Never edit here.
        versionCode = appVersionCode
        versionName = appVersionName

        // Crash reporting (#354). The DSN comes from the environment — `Mobile Release` injects the
        // SENTRY_DSN secret; a local or CI debug build has none, so Sentry stays off there. Release
        // is `<package>@<versionName>+<versionCode>`, Sentry's own convention, so a crash names the
        // exact build it came from, from the same single version source as everything else.
        manifestPlaceholders["SENTRY_DSN"] = System.getenv("SENTRY_DSN") ?: ""
        manifestPlaceholders["SENTRY_RELEASE"] = "com.muhabbet.app@$appVersionName+$appVersionCode"
        manifestPlaceholders["SENTRY_ENVIRONMENT"] = "development"
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
            manifestPlaceholders["SENTRY_ENVIRONMENT"] = "production"
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

        // The release build, R8 and all, signed with the DEBUG key (#864, #867). It exists so the
        // minified app can be built and installed WITHOUT the release keystore: by pull-request CI
        // (R8 breakage used to surface only when a tag was cut — the #633 class of failure), by the
        // baseline-profile generator in :mobile:baselineprofile, and on emulator-5554 before a
        // release. Same shrinker rules as release, so what passes here passes there.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            manifestPlaceholders["SENTRY_ENVIRONMENT"] = "benchmark"
        }
    }

    // Lint is a gate, not a report (#865). It used to run with continue-on-error and no baseline,
    // so a NewApi violation — the class of bug that crashed voice transcription on Android 8–11
    // (#381) — was reported and ignored. The findings that existed when the gate went in are frozen
    // in lint-baseline.xml; anything new fails the build. Shrink the baseline, never regenerate it
    // to bury a new finding (the same rule as detekt-baseline.xml). Two of the frozen findings are a
    // live bug, not noise: InlinedApi on MediaRecorder OGG/OPUS (API 29) in AudioRecorder — #849.
    // The gate is what makes the next one of those fail instead of ship.
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkReleaseBuilds = true
        error += setOf("NewApi", "InlinedApi")
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
 * Writes `BuildInfo` for commonMain from the single version input (#863), so the in-app version
 * (About screen, What's New, test-build notice) is the APK manifest's version by construction.
 * Replaces a hand-written copy plus `verifyBuildInfoVersion`, a regex that policed the copy.
 *
 * The platform half — whether this is a debuggable build — stays hand-written as the
 * `isDebugBuild()` expect/actual in `DebugBuild.kt`, because it is a fact of the platform build
 * type, not of the version.
 */
val generateBuildInfo by tasks.registering {
    val name = appVersionName
    val code = appVersionCode
    val outDir = layout.buildDirectory.dir("generated/buildinfo/commonMain/kotlin")
    inputs.property("versionName", name)
    inputs.property("versionCode", code)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/muhabbet/app/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// GENERATED by :mobile:composeApp:generateBuildInfo from muhabbet.versionName — do not edit.
            |// The version's single source is the root gradle.properties (or -Pmuhabbet.versionName on a
            |// release build). See mobile/composeApp/build.gradle.kts, #863.
            |package com.muhabbet.app
            |
            |object BuildInfo {
            |    const val VERSION = "$name"
            |    const val VERSION_CODE = $code
            |
            |    /**
            |     * True only in a debuggable build. Verbose diagnostics — HTTP logging and the stdout
            |     * log sink — hang off this, so a release build must never report true.
            |     */
            |    val DEBUG: Boolean get() = isDebugBuild()
            |}
            |""".trimMargin()
        )
    }
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateBuildInfo)
}

/**
 * Prints the resolved version. `Mobile Release` runs it with the tag's version before building, so a
 * malformed tag fails in seconds rather than after an R8 build, and `Mobile CI` runs it as a dry run
 * of the tag → version derivation.
 */
val printAppVersion by tasks.registering {
    val name = appVersionName
    val code = appVersionCode
    doLast { println("versionName=$name versionCode=$code") }
}

/**
 * Pins the versionCode formula. If it ever stops being monotonic — two releases mapping to the same
 * code, or a later version to a lower one — Play rejects the upload only after a full release
 * build, so the property is checked here instead, on every `check`.
 */
val verifyAppVersionScheme by tasks.registering {
    doLast {
        val ordered = listOf(
            "0.3.9", "0.3.10", "0.3.11", "0.3.99", "0.4.0", "0.10.0", "0.99.99", "1.0.0", "2.5.7"
        )
        val codes = ordered.map(::appVersionCodeOf)
        check(codes.zipWithNext().all { (a, b) -> a < b }) {
            "versionCode is not monotonic: ${ordered.zip(codes)}"
        }
        check(appVersionCodeOf("0.3.10") > 15) {
            "0.3.10 must outrank the last hand-assigned versionCode (15)"
        }
        listOf("0.3", "v0.3.11", "0.3.11-rc1", "0.3.100", "01.2.3", "").forEach { bad ->
            check(runCatching { appVersionCodeOf(bad) }.isFailure) { "'$bad' must be refused as a version" }
        }
    }
}

tasks.named("check") { dependsOn(verifyAppVersionScheme) }

// ── Minified build verification (#864) ───────────────────────────────────────────────────────────
//
// R8 used to run only when a tag was cut, so a missing keep rule surfaced as a release that crashed
// (#633's failure class) — and the blanket library keeps meant to prevent that were never measured.
// These tasks run against a minified variant's own outputs and fail the build if:
//   * a class the app or a library reaches ONLY BY NAME was removed (each entry says how it is
//     reached — a class here is the evidence a keep rule needs, and vice versa), or
//   * the APK exceeds its size budget, so growth is a decision someone makes rather than drift.
// `Mobile CI` runs `assembleBenchmark verifyBenchmarkArtifacts` on pull requests (debug-signed, no
// release keystore needed); `Mobile Release` runs `verifyReleaseArtifacts` on the signed build.

/** Classes that must survive R8, with the mechanism that reaches them by name. */
val classesReachedByName = mapOf(
    // Ktor finds its engine through ServiceLoader (META-INF/services), because ApiClient builds
    // `HttpClient(config)` without naming an engine.
    "io.ktor.client.engine.okhttp.OkHttpEngineContainer" to "ServiceLoader (Ktor engine discovery)",
    // The manifest names these; aapt generates their keep rules, which this checks were applied.
    "com.muhabbet.app.MainActivity" to "AndroidManifest launcher activity",
    "com.muhabbet.app.platform.MuhabbetFirebaseMessagingService" to "AndroidManifest service",
    // WorkManager persists the worker's class NAME and instantiates it reflectively after a restart.
    "com.muhabbet.app.platform.MessageSyncWorker" to "WorkManager reflective instantiation",
    // kotlinx.serialization: `serializer<T>()` finds the generated serializer through the class's
    // companion. One per family the app depends on: the REST envelope and the WebSocket protocol.
    "com.muhabbet.shared.dto.ApiResponse\$\$serializer" to "kotlinx.serialization lookup",
    "com.muhabbet.shared.protocol.WsMessage\$SendMessage\$\$serializer" to "kotlinx.serialization lookup",
)

/**
 * Upper bound for the minified universal APK, in bytes. Set from the size measured when this gate
 * went in (recorded in #864) plus headroom. Raise it only deliberately, saying why in the commit.
 */
val minifiedApkBudgetBytes = 0L

fun registerMinifiedVerification(variant: String) {
    val capitalized = variant.replaceFirstChar { it.uppercase() }
    tasks.register("verify${capitalized}Artifacts") {
        group = "verification"
        description = "Checks the $variant APK's R8 output and size budget (#864)."
        val apkDir = layout.buildDirectory.dir("outputs/apk/$variant")
        val mapping = layout.buildDirectory.file("outputs/mapping/$variant/mapping.txt")
        val required = classesReachedByName
        val budget = minifiedApkBudgetBytes
        mustRunAfter("assemble$capitalized")
        doLast {
            val apk = apkDir.get().asFile.walkTopDown().firstOrNull { it.extension == "apk" }
                ?: throw GradleException("No $variant APK under ${apkDir.get().asFile} — assemble it first")
            val mappingFile = mapping.get().asFile
            if (!mappingFile.isFile) throw GradleException("No R8 mapping at $mappingFile — is minify on?")
            val kept = mappingFile.useLines { lines ->
                lines.filter { !it.startsWith(" ") && !it.startsWith("#") }
                    .map { it.substringBefore(" -> ") }
                    .toHashSet()
            }
            val missing = required.filterKeys { it !in kept }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "R8 removed classes reached by name:\n" +
                        missing.entries.joinToString("\n") { (cls, why) -> "  $cls  ($why)" } +
                        "\nAdd a targeted keep rule to proguard-rules.pro — not a package-wide one."
                )
            }
            ZipFile(apk).use { zip ->
                val service = "META-INF/services/io.ktor.client.HttpClientEngineContainer"
                if (zip.getEntry(service) == null) throw GradleException("$service is missing from $apk")
            }
            val size = apk.length()
            println("$variant APK: ${apk.name} = $size bytes (budget $budget)")
            if (size > budget) {
                throw GradleException("$variant APK is $size bytes, over the $budget-byte budget (#864)")
            }
        }
    }
}

registerMinifiedVerification("release")
registerMinifiedVerification("benchmark")
