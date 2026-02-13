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
