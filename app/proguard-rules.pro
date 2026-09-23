# Add project specific ProGuard rules here.
# Optimized and hardened for maximum security against de-compilation and reverse-engineering,
# while guaranteeing 100% stability for Room, Moshi, Lottie, Media3, OpenGL, and Firebase AI.

# ==============================================================================
# 1. OBFUSCATION & SECURITY ENHANCEMENTS
# ==============================================================================

# Flatten package hierarchy and move all obfuscated classes to a single hidden package
-repackageclasses 'com.ritvyom.yashoraReelgenerator.obf'

# Allow R8 to expand the visibility of classes and members for aggressive code-inlining
-allowaccessmodification

# Preserve essential stacktrace and reflection attributes
-keepattributes LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute ""

# Preserve parameter names for safe Kotlin reflection / default parameters
-keepparameternames

# Keep native JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Enums intact so values() and valueOf() never crash
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable CREATORs
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Strip verbose and debug logging statements in production builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# ==============================================================================
# 2. APPLICATION DATA, DOMAIN & RENDERING ENGINE MODELS
# ==============================================================================

# Keep all Domain, Core and Data models
-keep class com.ritvyom.yashoraReelgenerator.domain.models.** { *; }
-keep class com.ritvyom.yashoraReelgenerator.core.model.** { *; }
-keep class com.ritvyom.yashoraReelgenerator.data.model.** { *; }
-keep class com.ritvyom.yashoraReelgenerator.data.network.** { *; }
-keep class com.ritvyom.yashoraReelgenerator.data.remote.** { *; }
-keep class com.ritvyom.yashoraReelgenerator.data.voxeleven.** { *; }
-keep class com.ritvyom.yashoraReelgenerator.data.ai.** { *; }
-keep class com.ritvyom.yashoraReelgenerator.data.repository.** { *; }

# Keep Video & Audio Engine, OpenGL, EGL, Shaders, Effects and Canvas Layers
-keep class com.ritvyom.yashoraReelgenerator.engine.** { *; }
-keep class com.ritvyom.yashoraReelgenerator.core.rendering.** { *; }

# Keep Room Database entities, DAOs, Type Converters and AppDatabase
-dontwarn androidx.room.**
-keep class com.ritvyom.yashoraReelgenerator.data.local.** { *; }

# ==============================================================================
# 3. JSON SERIALIZATION & PARSING (Moshi)
# ==============================================================================

# Moshi runtime and generated adapters
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep class *JsonAdapter { *; }
-keep class *JsonAdapter$* { *; }
-keep @interface com.squareup.moshi.JsonQualifier
-keep @interface com.squareup.moshi.JsonClass
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# ==============================================================================
# 4. LOTTIE ANIMATIONS (Critical for Release APK stability)
# ==============================================================================

# Lottie uses reflection to read JSON animation properties
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ==============================================================================
# 5. NETWORKING & MEDIA LIBRARIES
# ==============================================================================

# Retrofit & OkHttp
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.** <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Media3 & ExoPlayer, Transformer & OpenGL Effects
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.effect.** { *; }
-keep class androidx.media3.exoplayer.** { *; }

# Coil Image Loading
-dontwarn coil.**
-keep class coil.** { *; }

# ZXing QR / Barcode
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ==============================================================================
# 6. FIREBASE & CLOUD SERVICES
# ==============================================================================

# Firebase Core, Auth, AppCheck, Firestore & Vertex AI
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.vertexai.** { *; }
-keep class com.google.firebase.ai.** { *; }

# Google Play Services & Credentials Manager
-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.auth.api.** { *; }
-dontwarn com.google.android.gms.auth.api.identity.**
-keep class com.google.android.gms.auth.api.identity.** { *; }
-dontwarn com.google.android.gms.auth.api.signin.**
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
-keep class com.google.android.play.core.** { *; }

# Google Mobile Ads (AdMob) & Mediation Networks
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Meta Audience Network Mediation
-dontwarn com.facebook.infer.annotation.**
-dontwarn com.facebook.ads.**
-dontwarn com.facebook.imagepipeline.**
-dontwarn com.facebook.drawee.**
-keep class com.facebook.ads.** { *; }
-keep class com.google.ads.mediation.facebook.** { *; }

# Unity Ads Mediation
-dontwarn com.unity3d.ads.**
-dontwarn com.unity3d.services.**
-dontwarn com.google.ads.mediation.unity.**
-keep class com.unity3d.ads.** { *; }
-keep class com.unity3d.services.** { *; }
-keep class com.google.ads.mediation.unity.** { *; }

# AppLovin Mediation
-dontwarn com.applovin.**
-dontwarn com.google.ads.mediation.applovin.**
-keep class com.applovin.** { *; }
-keep class com.google.ads.mediation.applovin.** { *; }

# ==============================================================================
# 7. JETPACK COMPOSE & COROUTINES
# ==============================================================================

-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn androidx.compose.**
-keep class androidx.compose.material.icons.** { *; }
