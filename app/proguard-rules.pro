# Add project specific ProGuard rules here.

-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keep class androidx.hilt.work.** { *; }
