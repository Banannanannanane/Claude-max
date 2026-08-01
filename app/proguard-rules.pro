# kotlinx.serialization keeps generated serializers referenced only by reflection.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class io.github.banannanannanane.mammouth.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.banannanannanane.mammouth.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp ships optional references to Conscrypt / Bouncy Castle providers.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
