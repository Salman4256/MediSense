# MediSense ProGuard / R8 Optimization & Obfuscation Rules
# R8 is enabled for release builds with standard optimization.

# Preserve SourceFile and LineNumberTable for readable crash stack traces
-keepattributes SourceFile,LineNumberTable

# Preserve Annotations, InnerClasses, EnclosingMethod, and Signature for reflection & generics
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature

# -----------------------------------------------------------------------------
# Kotlin & Kotlinx Serialization
# -----------------------------------------------------------------------------
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements kotlinx.serialization.internal.GeneratedSerializer {
    *;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep @kotlinx.serialization.Serializable class * {
    <fields>;
}

# -----------------------------------------------------------------------------
# Room Persistence Library
# -----------------------------------------------------------------------------
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public void clearAllTables();
    <methods>;
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * {
    <fields>;
}
-keep @androidx.room.Dao interface * {
    <methods>;
}
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}

# -----------------------------------------------------------------------------
# Retrofit, OkHttp & Gson
# -----------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}
-dontwarn okio.**
-dontwarn retrofit2.**

# -----------------------------------------------------------------------------
# Supabase & Ktor Client
# -----------------------------------------------------------------------------
-keep class io.ktor.client.engine.** { *; }
-keep class io.github.jan.supabase.** { *; }

# -----------------------------------------------------------------------------
# TensorFlow Lite & LiteRT Native Interfaces (JNI)
# -----------------------------------------------------------------------------
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# -----------------------------------------------------------------------------
# WorkManager
# -----------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# -----------------------------------------------------------------------------
# MPAndroidChart
# -----------------------------------------------------------------------------
-keep class com.github.mikephil.charting.** { *; }

# -----------------------------------------------------------------------------
# Markwon Markdown Renderer
# -----------------------------------------------------------------------------
-dontwarn io.noties.markwon.**

# -----------------------------------------------------------------------------
# Compile-time annotations and optional library plugins
# -----------------------------------------------------------------------------
-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.auto.value.AutoValue
-dontwarn io.ktor.client.network.sockets.SocketTimeoutException
-dontwarn io.ktor.client.plugins.HttpTimeout$HttpTimeoutCapabilityConfiguration
-dontwarn io.ktor.client.plugins.HttpTimeout$Plugin
-dontwarn io.ktor.client.plugins.HttpTimeout
-dontwarn io.ktor.client.plugins.contentnegotiation.ContentNegotiation$Config
-dontwarn io.ktor.client.plugins.contentnegotiation.ContentNegotiation$Plugin
-dontwarn io.ktor.client.plugins.contentnegotiation.ContentNegotiation
-dontwarn io.ktor.utils.io.CoroutinesKt
