package com.example.voicera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity class representing a recorded video with AR mustache.
 * This class is used by Room to create the database table.
 */
@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Path to the video file in the device storage
     */
    val videoPath: String,
    
    /**
     * Path to the thumbnail image of the video
     */
    val thumbnailPath: String,
    
    /**
     * Duration of the video in milliseconds
     */
    val duration: Long,
    
    /**
     * User-provided tag for the recording
     */
    val tag: String,
    
    /**
     * Timestamp when the recording was created
     */
    val timestamp: Long = System.currentTimeMillis(),
    
    /**
     * Type of mustache used in the recording
     */
    val mustacheType: String,
    
    /**
     * Size of the video file in bytes
     */
    val fileSize: Long = 0,
    
    /**
     * Resolution of the video (e.g., "1920x1080")
     */
    val resolution: String = ""
)
