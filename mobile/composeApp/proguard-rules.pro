# Muhabbet R8 rules (#864)
#
# Only what the app itself needs. Every library here — Ktor, Koin, Decompose, Firebase, Coil,
# OkHttp, Sentry, SQLDelight — ships consumer rules inside its AAR/JAR, and R8 applies those
# automatically. The blanket `-keep class <library>.** { *; }` lines that used to sit here turned
# shrinking AND obfuscation off for four large libraries (and one rule named `io.coil.**`, a package
# that does not exist — Coil 3 is `coil3.*`). They were written defensively, never measured.
#
# What protects this file now is `verifyReleaseArtifacts` / `verifyBenchmarkArtifacts` in
# build.gradle.kts, run on pull requests: it fails if R8 removes a class the app reaches only by
# name, and it enforces the APK size budget. A new rule belongs here only with a failure that proves
# it is needed — add the class to that task's list in the same change.

# kotlinx.serialization — the app's own @Serializable classes (shared DTOs, WsMessage, the
# Decompose configurations). The library's bundled rules cover its own runtime; these are the
# documented rules for user classes: keep each generated `$$serializer`, and the `Companion` /
# `serializer()` accessors through which `serializer<T>()` finds it.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.muhabbet.**$$serializer { *; }
-keepclassmembers class com.muhabbet.** {
    *** Companion;
}
-keepclasseswithmembers class com.muhabbet.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp probes for optional TLS providers by name and tolerates their absence.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
