# Muhabbet ProGuard Rules

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.muhabbet.**$$serializer { *; }
-keepclassmembers class com.muhabbet.** {
    *** Companion;
}
-keepclasseswithmembers class com.muhabbet.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Decompose
-keep class com.arkivanov.decompose.** { *; }

# Koin
-keep class org.koin.** { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Coil
-keep class io.coil.** { *; }

# Keep data classes used in API responses
-keep class com.muhabbet.shared.dto.** { *; }
-keep class com.muhabbet.shared.model.** { *; }
-keep class com.muhabbet.shared.protocol.** { *; }

# Release builds write nothing below warning level to logcat. Calls made straight
# to android.util.Log at debug/verbose/info are removed by R8; the common Log wrapper is already
# off when BuildInfo.DEBUG is false. Warnings and errors stay, and must not carry message
# content, phone numbers or tokens.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
