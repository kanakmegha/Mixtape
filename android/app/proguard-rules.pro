
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
-keep class org.apache.commons.compress.** { *; }
