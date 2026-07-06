# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.virtualworkspace.**$$serializer { *; }
-keepclassmembers class com.virtualworkspace.** {
    *** Companion;
}
-keepclasseswithmembers class com.virtualworkspace.** {
    kotlinx.serialization.KSerializer serializer(...);
}
