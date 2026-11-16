# Release shrinking. App types stay un-obfuscated: Gson persists profiles,
# home layout, and the playback session by field name, and the committed
# baseline profile refers to these classes. Dependencies still shrink.
-keep class com.sinema.** { *; }

-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# R8 full mode otherwise inlines away the Main dispatcher and renames its
# ServiceLoader entry. The first home-screen coroutine then crashes the process.
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.internal.MainDispatcherLoader { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory

-keep class okhttp3.internal.platform.** { *; }
-keep class okhttp3.internal.publicsuffix.** { *; }

# Glide constructs this from the manifest by reflection. R8 dropped the
# no-arg constructor, so the first home-screen poster crashed the process.
-keep class com.bumptech.glide.integration.okhttp3.OkHttpGlideModule { *; }
-keep class com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule { *; }
-keep class * implements com.bumptech.glide.module.GlideModule { *; }

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
