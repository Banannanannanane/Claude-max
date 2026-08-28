# Le moteur Flutter est référencé par JNI : R8 ne voit pas ces usages.
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }
-dontwarn io.flutter.embedding.**
