package com.example.voicera.presentation.video

import com.example.voicera.domain.model.MustacheModel

/**
 * State class for the Video Screen.
 * This represents the UI state of the video recording screen.
 */
data class VideoScreenState(
    /**
     * Whether camera permission has been granted
     */
    val cameraPermissionGranted: Boolean = false,
    
    /**
     * Whether microphone permission has been granted
     */
    val microphonePermissionGranted: Boolean = false,
    
    /**
     * Whether storage permission has been granted
     */
    val storagePermissionGranted: Boolean = false,
    
    /**
     * Whether the camera is currently recording
     */
    val isRecording: Boolean = false,
    
    /**
     * Current recording duration in milliseconds
     */
    val recordingDuration: Long = 0L,
    
    /**
     * Whether a face is currently detected
     */
    val faceDetected: Boolean = false,
    
    /**
     * Index of the currently selected mustache
     */
    val selectedMustacheIndex: Int = 0,
    
    /**
     * List of available mustache models
     */
    val availableMustaches: List<MustacheModel> = emptyList(),
    
    /**
     * Whether the AR session is initialized
     */
    val arSessionInitialized: Boolean = false,
    
    /**
     * Whether the camera is initialized
     */
    val cameraInitialized: Boolean = false,
    
    /**
     * Error message, if any
     */
    val error: String? = null,
    
    /**
     * Whether the tag input dialog is visible
     */
    val isTagDialogVisible: Boolean = false,
    
    /**
     * Current tag input value
     */
    val currentTag: String = "",
    
    /**
     * Whether the app is currently loading
     */
    val isLoading: Boolean = false
)
