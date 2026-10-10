
# Preserve source line information for useful crash reports.
-keepattributes SourceFile,LineNumberTable

# Preserve annotations used by Kotlin, dependency injection and reflection.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Preserve native method names and declarations.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
# Preserve Apache Commons Compress classes used by yt-dlp
# to prevent R8 from breaking runtime class discovery.
-keep class org.apache.commons.compress.** { *; }

# Preserve Apache Commons Compress classes used by the yt-dlp dependency.
# The release build previously crashed during application initialization with an
# obfuscated "class ... is not a concrete class" error. Verify this fix on-device.

# Room uses reflection to instantiate generated database implementations.
# Preserve their no-argument constructors for R8 release builds.
-keep class * extends androidx.room.RoomDatabase { void <init>(); }

# Preserve all Gson data transfer objects and serialized fields for Retrofit
-keep class com.example.privatemusiclibrary.data.network.** { *; }
-keepclassmembers class com.example.privatemusiclibrary.data.network.** {
    <fields>;
    <init>(...);
}
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
