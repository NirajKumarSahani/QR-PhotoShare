# Retrofit & OkHttp
-keepattributes Signature, InnerClasses, AnnotationDefault, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep @interface retrofit2.http.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Gson
-keepattributes Signature
-keepattributes EnclosingMethod
-keepclassmembers class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements com.google.gson.TypeAdapterFactory
-keep public class * implements com.google.gson.TypeAdapter
-keep public class * implements com.google.gson.JsonSerializer
-keep public class * implements com.google.gson.JsonDeserializer

# Kotlin Coroutines (Mandatory for suspend functions in Retrofit)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext {
    private final android.os.Handler handler;
}
-keep class kotlin.coroutines.Continuation { *; }
-dontwarn kotlinx.coroutines.**

# Your API Model Classes (Aggressive keeping)
-keep,allowobfuscation interface com.qrphotoshare.api.**
-keep class com.qrphotoshare.api.** { *; }
-keepclassmembers class com.qrphotoshare.api.** { *; }

# Prevent R8 from stripping generic signatures
-keepattributes Signature
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }

# Avoid obfuscating names of fields in data classes used for JSON
-keepclassmembernames class com.qrphotoshare.api.** {
    <fields>;
}
