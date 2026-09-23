# Add project specific ProGuard rules here.
# Optimized and hardened for maximum security against de-compilation and reverse-engineering.

# ==============================================================================
# 1. AGGRESSIVE OBFUSCATION & SECURITY ENHANCEMENTS
# ==============================================================================

# Flatten package hierarchy and move all obfuscated classes to a single hidden package
# This completely destroys the original package structure, making reverse engineering extremely difficult.
-repackageclasses 'com.ritvyom.yashoraReelgenerator.obf'

# Overload class member names aggressively.
# Reuses the same names (a, b, c...) for fields and methods with different signatures.
# This makes decompiled Java/Kotlin source code highly confusing and unreadable.
-overloadaggressively

# Allow R8 to expand the visibility of classes and members (e.g., from private/package to public).
# This allows R8 to perform much more aggressive code-inlining and optimizations.
-allowaccessmodification

# Remove source file attributes to hide original source filenames (.kt / .java names)
# of classes in stack traces and compiled dex files.
-keepattributes LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute ""

# Avoid letting R8 preserve parameter names in method signatures.
# This ensures compiled methods have generic parameter names like p0, p1, etc.
-keepparameternames

# Strip verbose and debug logging statements in production builds
# This removes debugging strings and sensitive log statements from being decompiled or logged.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# ==============================================================================
# 2. FRAMEWORK & LIBRARY KEEP RULES (Ensuring Stability under Obfuscation)
# ==============================================================================

# Keep Database entities and DAOs intact so Room SQLite reflection and generated classes work correctly
-keep class com.ritvyom.yashoraReelgenerator.data.local.entities.** { *; }
-keep interface com.ritvyom.yashoraReelgenerator.data.local.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep all Domain and Remote models (including those parsed via Moshi/Retrofit)
-keep class com.ritvyom.yashoraReelgenerator.domain.models.** { *; }
-keep class com.ritvyom.yashoraReelgenerator.data.model.** { *; }
-keep class com.ritvyom.yashoraReelgenerator.data.network.** { *; }

# Keep Moshi generated JsonAdapters and annotations
-keep class *JsonAdapter { *; }
-keep class *JsonAdapter$* { *; }
-keep @interface com.squareup.moshi.JsonQualifier
-keep @interface com.squareup.moshi.JsonClass

# Retrofit & OkHttp rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.** <methods>;
}

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Keep Google Play Services Ads classes and attributes
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

