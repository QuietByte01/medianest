# ProGuard & R8 Rules for MediaNest Release Builds

# Automatically strip verbose, debug, and info logs in Release builds
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Preserve line numbers and source file attributes for Crashlytics/Stacktrace de-obfuscation
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature,*Annotation*

# Retain C++ JNI native method names
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep MediaNest data models, Room entities, DataStore, and ViewModels
-keep class com.medianest.data.db.** { *; }
-keep class com.medianest.data.model.** { *; }
-keep class com.medianest.data.settings.** { *; }
-keep class com.medianest.ui.** { *; }
-keepclassmembers class com.medianest.** { *; }

# Room Database rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3 & ExoPlayer rules
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Haze, Coil, Moshi, Retrofit
-keep class dev.chrisbanes.haze.** { *; }
-keep class io.coilkt.** { *; }
-keep class com.squareup.moshi.** { *; }
-keep class com.squareup.retrofit2.** { *; }

# Firebase & AdMob
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
