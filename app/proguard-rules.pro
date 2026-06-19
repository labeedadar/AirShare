# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class com.airshare.app.** { *; }
