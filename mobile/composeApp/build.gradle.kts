plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
    id("com.google.gms.google-services")
}

group = "com.muhabbet"
version = "0.1.0-SNAPSHOT"

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "21"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val decompose = "3.2.2"
        val koin = "4.0.0"
        val ktor = "3.0.2"
        val coil = "3.0.4"

        commonMain.dependencies {
            // Shared KMP module
            implementation(project(":shared"))

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
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

            // Image loading — Coil
            implementation("io.coil-kt.coil3:coil-compose:$coil")
            implementation("io.coil-kt.coil3:coil-network-ktor3:$coil")
        }

        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktor")
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.security:security-crypto:1.1.0-alpha06")

            // Firebase Auth (Phone verification)
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:33.7.0"))
            implementation("com.google.firebase:firebase-auth-ktx")
        }

        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktor")
        }
    }
}

android {
    namespace = "com.muhabbet.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.muhabbet.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}
