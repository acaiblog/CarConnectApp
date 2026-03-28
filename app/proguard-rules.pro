# Add project specific ProGuard rules here.
-keep class com.carconnect.app.** { *; }
-keep class android.bluetooth.** { *; }
-dontwarn android.bluetooth.**
-keepclassmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
