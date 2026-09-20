# TipsyBuddy Proguard rules
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**

# Google Mobile Ads (AdMob)
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
