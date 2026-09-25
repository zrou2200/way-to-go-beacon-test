# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.waytogo.**$$serializer { *; }
-keepclassmembers class com.waytogo.** {
    *** Companion;
}
-keepclasseswithmembers class com.waytogo.** {
    kotlinx.serialization.KSerializer serializer(...);
}
