# =============================================================================
# QR-Attend ProGuard Rules
# =============================================================================

# Keep line numbers in stack traces for easier debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures (needed by Gson, Firestore, Retrofit, etc.)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# =============================================================================
# FIREBASE — Core
# Prevents Firebase SDK internals from being stripped or renamed.
# =============================================================================
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# =============================================================================
# FIREBASE AUTH
# =============================================================================
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# =============================================================================
# FIREBASE FIRESTORE
# Firestore uses reflection to deserialize documents into POJOs.
# Field names MUST be preserved exactly as declared.
# =============================================================================
-keep class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.firestore.**

# Keep all Firestore POJO data models — field names must not be renamed
-keep class com.qrattend.app.data.model.** { *; }
-keepclassmembers class com.qrattend.app.data.model.** {
    public <init>();          # No-arg constructor required by Firestore
    public <fields>;          # All public fields
    public <methods>;         # All getters/setters
}

# =============================================================================
# FIREBASE CLOUD MESSAGING (FCM)
# =============================================================================
-keep class com.google.firebase.messaging.** { *; }
-dontwarn com.google.firebase.messaging.**

# Keep our custom FCM service
-keep class com.qrattend.app.firebase.FCMService { *; }

# =============================================================================
# FIREBASE CRASHLYTICS
# =============================================================================
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**
-keepattributes *Annotation*

# =============================================================================
# FIREBASE ANALYTICS
# =============================================================================
-keep class com.google.firebase.analytics.** { *; }
-dontwarn com.google.firebase.analytics.**

# =============================================================================
# GOOGLE PLAY SERVICES (used by Firebase internally)
# =============================================================================
-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# =============================================================================
# APP AUTH MANAGER
# Keeps inner classes (SessionInfo, UserRecord, AuthException, TokenManager)
# =============================================================================
-keep class com.qrattend.app.firebase.AuthManager { *; }
-keep class com.qrattend.app.firebase.AuthManager$* { *; }

# =============================================================================
# ZXING (QR Code scanning)
# =============================================================================
-keep class com.journeyapps.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.journeyapps.**
-dontwarn com.google.zxing.**

# =============================================================================
# ML KIT (Barcode scanning)
# =============================================================================
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# =============================================================================
# CAMERAX
# =============================================================================
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# =============================================================================
# ANDROID / ANDROIDX — General safety rules
# =============================================================================
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Keep all Activities, Services, Receivers, Providers (registered in Manifest)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep View constructors (used by XML layouts)
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep R class fields
-keepclassmembers class **.R$* {
    public static <fields>;
}

# =============================================================================
# SUPPRESS COMMON WARNINGS
# =============================================================================
-dontwarn org.xmlpull.v1.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.**