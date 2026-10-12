# kotlinx.serialization: keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class id.web.agentvaedah.strideway.**$$serializer { *; }
-keepclassmembers class id.web.agentvaedah.strideway.** {
    *** Companion;
}

# MapLibre loads native peers via JNI.
-keep class org.maplibre.** { *; }

# Ktor / OkHttp optional platform classes.
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn java.lang.management.**
