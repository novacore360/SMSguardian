package com.secureguardian.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.secureguardian.app.data.local.dao.*
import com.secureguardian.app.data.local.entities.*
import com.secureguardian.app.domain.model.ThreatLevel

@Database(
    entities = [
        CachedMessageEntity::class,
        FlaggedDomainEntity::class,
        ContactEntity::class,
        BlockedMessageEntity::class,
        SecurityStatsEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(AppConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun flaggedDomainDao(): FlaggedDomainDao
    abstract fun contactDao(): ContactDao
    abstract fun blockedMessageDao(): BlockedMessageDao
    abstract fun statsDao(): StatsDao
}

class AppConverters {
    @TypeConverter
    fun fromThreatLevel(value: ThreatLevel): String = value.name

    @TypeConverter
    fun toThreatLevel(value: String): ThreatLevel = ThreatLevel.valueOf(value)
}
