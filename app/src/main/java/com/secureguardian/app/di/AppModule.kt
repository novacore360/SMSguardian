package com.secureguardian.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.secureguardian.app.BuildConfig
import com.secureguardian.app.data.local.AppDatabase
import com.secureguardian.app.data.local.dao.*
import com.secureguardian.app.data.remote.SupabaseDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Supabase ─────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }

    @Provides
    @Singleton
    fun provideSupabaseDataSource(client: SupabaseClient) =
        SupabaseDataSource(client)

    // ── Room ─────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "secure_guardian_db"
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides fun provideFlaggedDomainDao(db: AppDatabase): FlaggedDomainDao = db.flaggedDomainDao()
    @Provides fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()
    @Provides fun provideBlockedMessageDao(db: AppDatabase): BlockedMessageDao = db.blockedMessageDao()
    @Provides fun provideStatsDao(db: AppDatabase): StatsDao = db.statsDao()

    // ── WorkManager ──────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
