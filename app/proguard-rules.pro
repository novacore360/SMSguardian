# SecureSMS Guardian ProGuard Rules

# Keep Supabase models
-keep class io.github.jan.tennert.supabase.** { *; }
-keepclassmembers class io.github.jan.tennert.supabase.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class ** {
    @kotlinx.serialization.Serializable <methods>;
}
-keep,includedescriptorclasses class com.secureguardian.app.**$$serializer { *; }
-keepclassmembers class com.secureguardian.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.secureguardian.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data models
-keep class com.secureguardian.app.domain.model.** { *; }
-keep class com.secureguardian.app.data.remote.** { *; }
-keep class com.secureguardian.app.data.local.entities.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Timber
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Retrofit / OkHttp (if used)
-dontwarn okhttp3.**
-dontwarn okio.**

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Android SMS
-keep class android.provider.Telephony.** { *; }

# Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# General Android
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
