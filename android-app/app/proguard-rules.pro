# Retrofit
-keepattributes Signature, InnerClasses, AnnotationDefault, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep @interface retrofit2.http.** { *; }

# OkHttp
-keepattributes Signature
-keepattributes*Annotation*
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

# Your API Model Classes
-keep class com.qrphotoshare.api.** { *; }
-keepclassmembers class com.qrphotoshare.api.** { *; }

# Avoid obfuscating names of fields in data classes used for JSON
-keepclassmembernames class com.qrphotoshare.api.** {
    <fields>;
}
