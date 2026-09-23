package com.ritvyom.yashoraReelgenerator.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ritvyom.yashoraReelgenerator.data.local.dao.ExportHistoryDao
import com.ritvyom.yashoraReelgenerator.data.local.dao.ProjectDao
import com.ritvyom.yashoraReelgenerator.data.local.dao.CachedImageDao
import com.ritvyom.yashoraReelgenerator.data.local.dao.ScriptDao
import com.ritvyom.yashoraReelgenerator.data.local.dao.AudioCacheDao
import com.ritvyom.yashoraReelgenerator.data.local.dao.CommunityVideoCacheDao
import com.ritvyom.yashoraReelgenerator.data.local.dao.GeneratedAudioDao
import com.ritvyom.yashoraReelgenerator.data.local.entities.ExportHistoryEntity
import com.ritvyom.yashoraReelgenerator.data.local.entities.ProjectEntity
import com.ritvyom.yashoraReelgenerator.data.local.entities.CachedImageEntity
import com.ritvyom.yashoraReelgenerator.data.local.entities.AudioCache
import com.ritvyom.yashoraReelgenerator.data.local.entities.CommunityVideoCacheEntity
import com.ritvyom.yashoraReelgenerator.data.local.entities.GeneratedAudioEntity
import com.ritvyom.yashoraReelgenerator.data.model.VideoScript

@Database(
    entities = [
        ProjectEntity::class,
        ExportHistoryEntity::class,
        CachedImageEntity::class,
        VideoScript::class,
        AudioCache::class,
        CommunityVideoCacheEntity::class,
        GeneratedAudioEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun exportHistoryDao(): ExportHistoryDao
    abstract fun cachedImageDao(): CachedImageDao
    abstract fun scriptDao(): ScriptDao
    abstract fun audioCacheDao(): AudioCacheDao
    abstract fun communityVideoCacheDao(): CommunityVideoCacheDao
    abstract fun generatedAudioDao(): GeneratedAudioDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yashora_reels_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
