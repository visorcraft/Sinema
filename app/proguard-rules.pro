# Release shrinking. App types stay un-obfuscated: Gson persists profiles,
# home layout, and the playback session by field name, and the committed
# baseline profile refers to these classes. Dependencies still shrink.
-keep class com.sinema.** { *; }

-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
