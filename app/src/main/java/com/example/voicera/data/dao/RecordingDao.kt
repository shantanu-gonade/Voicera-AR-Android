package com.example.voicera.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.voicera.data.model.Recording
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the Recording entity.
 * Provides methods to interact with the recordings table in the database.
 */
@Dao
interface RecordingDao {
    
    /**
     * Insert a new recording into the database.
     * @param recording The recording to insert
     * @return The ID of the inserted recording
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: Recording): Long
    
    /**
     * Get all recordings from the database, ordered by timestamp (newest first).
     * @return A Flow of List of Recording objects
     */
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<Recording>>
    
    /**
     * Get a recording by its ID.
     * @param id The ID of the recording to get
     * @return The Recording object
     */
    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): Recording?
    
    /**
     * Update an existing recording.
     * @param recording The recording to update
     */
    @Update
    suspend fun updateRecording(recording: Recording)
    
    /**
     * Delete a recording from the database.
     * @param recording The recording to delete
     */
    @Delete
    suspend fun deleteRecording(recording: Recording)
    
    /**
     * Delete a recording by its ID.
     * @param id The ID of the recording to delete
     */
    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)
    
    /**
     * Search recordings by tag.
     * @param query The search query
     * @return A Flow of List of Recording objects that match the query
     */
    @Query("SELECT * FROM recordings WHERE tag LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchRecordingsByTag(query: String): Flow<List<Recording>>
}
