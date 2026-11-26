-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Preserve BuildConfig & Monzi models/entities
-keep class com.krunal.monzi.BuildConfig { *; }
-keep class com.krunal.monzi.data.** { *; }
-keep class com.krunal.monzi.ParsedSms { *; }
-keep class com.krunal.monzi.ParsedCreditCardStatement { *; }
-keep class com.krunal.monzi.SmsInspectionResult { *; }

# Room Database & generated Impl
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# WorkManager Workers
-keep class com.krunal.monzi.SyncWorker { *; }
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Biometric & AppCompat
-keep class androidx.biometric.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
