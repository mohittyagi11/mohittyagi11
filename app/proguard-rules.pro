# kotlinx.serialization keeps generated serializers; keep them from being stripped.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.azadishashn.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.azadishashn.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
