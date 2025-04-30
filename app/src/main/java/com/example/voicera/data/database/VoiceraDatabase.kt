package com.example.voicera.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.voicera.data.dao.RecordingDao
import com.example.voicera.data.model.Recording

/**
 * Room database for the Voicera app.
 * This is the main access point for the app's persisted data.
 */
@Database(
    entities = [Recording::class],
    version = 1,
    exportSchema = false
)
abstract class VoiceraDatabase : RoomDatabase() {
    
    /**
     * Get the RecordingDao to interact with the recordings table.
     */
    abstract fun recordingDao(): RecordingDao
    
    companion object {
        private const val DATABASE_NAME = "voicera_database"
        
        @Volatile
        private var INSTANCE: VoiceraDatabase? = null
        
        /**
         * Get the singleton instance of the database.
         * @param context The application context
         * @return The database instance
         */
        fun getInstance(context: Context): VoiceraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceraDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
