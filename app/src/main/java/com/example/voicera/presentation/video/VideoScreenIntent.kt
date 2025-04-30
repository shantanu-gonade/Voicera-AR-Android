package com.example.voicera.presentation.video

/**
 * Sealed interface representing user intents (actions) for the Video Screen.
 * In MVI architecture, intents represent user actions that trigger state changes.
 */
sealed interface VideoScreenIntent {
    /**
     * Intent to request camera permission
     */
    data object RequestCameraPermission : VideoScreenIntent
    
    /**
     * Intent to request microphone permission
     */
    data object RequestMicrophonePermission : VideoScreenIntent
    
    /**
     * Intent to request storage permission
     */
    data object RequestStoragePermission : VideoScreenIntent
    
    /**
     * Intent to initialize the camera
     */
    data object InitializeCamera : VideoScreenIntent
    
    /**
     * Intent to initialize the AR session
     */
    data object InitializeArSession : VideoScreenIntent
    
    /**
     * Intent to start recording
     */
    data object StartRecording : VideoScreenIntent
    
    /**
     * Intent to stop recording
     */
    data object StopRecording : VideoScreenIntent
    
    /**
     * Intent to select a mustache
     * @param index The index of the selected mustache
     */
    data class SelectMustache(val index: Int) : VideoScreenIntent
    
    /**
     * Intent to save a recording with a tag
     * @param tag The tag for the recording
     */
    data class SaveRecording(val tag: String) : VideoScreenIntent
    
    /**
     * Intent to navigate to the recordings list screen
     */
    data object NavigateToRecordings : VideoScreenIntent
    
    /**
     * Intent to show the tag input dialog
     */
    data object ShowTagDialog : VideoScreenIntent
    
    /**
     * Intent to dismiss the tag input dialog
     */
    data object DismissTagDialog : VideoScreenIntent
    
    /**
     * Intent to update the current tag input
     * @param tag The new tag value
     */
    data class UpdateTagInput(val tag: String) : VideoScreenIntent
    
    /**
     * Intent to handle face detection status
     * @param detected Whether a face is detected
     */
    data class FaceDetectionStatus(val detected: Boolean) : VideoScreenIntent
    
    /**
     * Intent to update the recording duration
     * @param durationMs The current duration in milliseconds
     */
    data class UpdateRecordingDuration(val durationMs: Long) : VideoScreenIntent
    
    /**
     * Intent to handle errors
     * @param message The error message
     */
    data class HandleError(val message: String) : VideoScreenIntent
    
    /**
     * Intent to clear errors
     */
    data object ClearError : VideoScreenIntent
}
