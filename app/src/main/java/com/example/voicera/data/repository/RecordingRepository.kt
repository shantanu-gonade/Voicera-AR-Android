package com.example.voicera.data.repository

import com.example.voicera.data.dao.RecordingDao
import com.example.voicera.data.model.Recording
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for Recording data.
 * This defines the operations that can be performed on the recordings.
 */
interface RecordingRepository {
    /**
     * Get all recordings, ordered by timestamp (newest first).
     */
    fun getAllRecordings(): Flow<List<Recording>>
    
    /**
     * Get a recording by its ID.
     */
    suspend fun getRecordingById(id: Long): Recording?
    
    /**
     * Insert a new recording.
     */
    suspend fun insertRecording(recording: Recording): Long
    
    /**
     * Update an existing recording.
     */
    suspend fun updateRecording(recording: Recording)
    
    /**
     * Delete a recording.
     */
    suspend fun deleteRecording(recording: Recording)
    
    /**
     * Delete a recording by its ID.
     */
    suspend fun deleteRecordingById(id: Long)
    
    /**
     * Search recordings by tag.
     */
    fun searchRecordingsByTag(query: String): Flow<List<Recording>>
}

/**
 * Implementation of the RecordingRepository interface.
 * This class is responsible for providing data from the database.
 */
@Singleton
class RecordingRepositoryImpl @Inject constructor(
    private val recordingDao: RecordingDao
) : RecordingRepository {
    
    override fun getAllRecordings(): Flow<List<Recording>> {
        return recordingDao.getAllRecordings()
    }
    
    override suspend fun getRecordingById(id: Long): Recording? {
        return recordingDao.getRecordingById(id)
    }
    
    override suspend fun insertRecording(recording: Recording): Long {
        return recordingDao.insertRecording(recording)
    }
    
    override suspend fun updateRecording(recording: Recording) {
        recordingDao.updateRecording(recording)
    }
    
    override suspend fun deleteRecording(recording: Recording) {
        recordingDao.deleteRecording(recording)
    }
    
    override suspend fun deleteRecordingById(id: Long) {
        recordingDao.deleteRecordingById(id)
    }
    
    override fun searchRecordingsByTag(query: String): Flow<List<Recording>> {
        return recordingDao.searchRecordingsByTag(query)
    }
}
