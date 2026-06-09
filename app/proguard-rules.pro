# Add project specific ProGuard rules here.

-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keep class androidx.hilt.work.** { *; }
