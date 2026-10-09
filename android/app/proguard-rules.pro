
# Preserve source line information for useful crash reports.
-keepattributes SourceFile,LineNumberTable

# Preserve annotations used by Kotlin, dependency injection and reflection.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Preserve native method names and declarations.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
