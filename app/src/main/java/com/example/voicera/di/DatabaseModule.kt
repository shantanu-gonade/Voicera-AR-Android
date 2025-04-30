package com.example.voicera.di

import android.content.Context
import com.example.voicera.data.dao.RecordingDao
import com.example.voicera.data.database.VoiceraDatabase
import com.example.voicera.data.repository.RecordingRepository
import com.example.voicera.data.repository.RecordingRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing database-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * Provides the VoiceraDatabase instance.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VoiceraDatabase {
        return VoiceraDatabase.getInstance(context)
    }
    
    /**
     * Provides the RecordingDao instance.
     */
    @Provides
    fun provideRecordingDao(database: VoiceraDatabase): RecordingDao {
        return database.recordingDao()
    }
    
    /**
     * Provides the RecordingRepository implementation.
     */
    @Provides
    @Singleton
    fun provideRecordingRepository(recordingDao: RecordingDao): RecordingRepository {
        return RecordingRepositoryImpl(recordingDao)
    }
}
