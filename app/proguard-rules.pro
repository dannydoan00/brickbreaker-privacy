# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep main application class
-keep public class com.boltgame.brickbreakerroguelite.BrickBreakerApp

# Game Engine Performance Optimizations
-keep class com.boltgame.brickbreakerroguelite.game.engine.** { *; }
-keep class com.boltgame.brickbreakerroguelite.game.physics.** { *; }

# Keep data models for serialization
-keep class com.boltgame.brickbreakerroguelite.data.model.** { *; }

# Monetization - Keep billing and ads classes
-keep class com.android.billingclient.api.** { *; }
-keep class com.google.android.gms.ads.** { *; }
-keep class com.boltgame.brickbreakerroguelite.monetization.** { *; }

# Firebase optimization
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Koin DI optimization
-keep class org.koin.** { *; }
-keep class com.boltgame.brickbreakerroguelite.di.** { *; }

# Coroutines optimization
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Physics engine optimization
-keep class org.dyn4j.** { *; }
-dontwarn org.dyn4j.**

# Supabase/Ktor optimization
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Glide optimization
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# Performance: Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Performance: Optimize method calls
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep View constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep Activity methods
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ViewBinding optimization
-keep class com.boltgame.brickbreakerroguelite.databinding.** { *; }

# Remove debug and test code in release
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

# Game-specific optimizations
# Keep ball physics calculations but allow optimization of internal methods
-keep class com.boltgame.brickbreakerroguelite.data.model.Ball {
    public *;
}
-keep class com.boltgame.brickbreakerroguelite.data.model.Brick {
    public *;
}
-keep class com.boltgame.brickbreakerroguelite.data.model.Paddle {
    public *;
}

# Keep game state classes for proper state management
-keep class com.boltgame.brickbreakerroguelite.game.engine.GameState { *; }
-keep class com.boltgame.brickbreakerroguelite.game.physics.CollisionResult { *; }

# Aggressive optimization settings for maximum performance
-repackageclasses ''
-flattenpackagehierarchy
-mergeinterfacesaggressively

# Remove unused code aggressively
-dontshrink
-dontoptimize 