plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

group = "com.muhabbet"
version = "0.1.0-SNAPSHOT"

kotlin {
    // Backend (JVM)
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "21"
            }
        }
    }

    // Android
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "21"
            }
        }
    }

    // iOS
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Serialization (JSON protocol shared between backend & mobile)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // DateTime
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain.dependencies {
            // JVM-specific if needed
        }

        androidMain.dependencies {
            // Android-specific if needed
        }

        iosMain.dependencies {
            // iOS-specific if needed
        }
    }
}

android {
    namespace = "com.muhabbet.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
