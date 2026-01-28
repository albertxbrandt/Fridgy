# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== Firebase Firestore Models =====
# Keep all model classes and their fields intact for Firestore serialization/deserialization
-keep class fyi.goodbye.fridgy.models.** { *; }

# Keep annotations and signatures required by Firebase
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# ===== Kotlin Coroutines =====
# Required for coroutines used throughout the app
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ===== Kotlin Reflection =====
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ===== Parcelable =====
# Keep Parcelable implementations (used by navigation arguments)
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ===== Enums =====
# Keep enum classes and their methods
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== Hilt/Dagger =====
# Keep Hilt-generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keepclassmembers class * extends dagger.hilt.android.lifecycle.HiltViewModel {
    <init>(...);
}

# ===== Jetpack Compose =====
# Keep Compose runtime classes
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**

# ===== Firebase Services =====
# Keep custom Firebase Messaging Service
-keep class * extends com.google.firebase.messaging.FirebaseMessagingService {
    public <init>(...);
    public void onMessageReceived(com.google.firebase.messaging.RemoteMessage);
}

# ===== General Android =====
# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom view constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ===== Timber Logging =====
# Keep Timber classes for debug builds
-keep class timber.log.Timber { *; }
-keep class timber.log.Timber$Tree { *; }
-keep class timber.log.Timber$DebugTree { *; }
-keepclassmembers class timber.log.Timber$Tree {
    protected void log(int, java.lang.String, java.lang.String, java.lang.Throwable);
}
# Preserve method names for automatic tag generation
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable