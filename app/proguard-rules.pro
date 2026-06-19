# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class com.airshare.app.** { *; }

# Ktor
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-keep class io.ktor.serialization.** { *; }

# kotlinx.coroutines
-dontwarn kotlinx.coroutines.**

# Okio (Ktor dependency)
-dontwarn okio.**
-keep class okio.** { *; }
