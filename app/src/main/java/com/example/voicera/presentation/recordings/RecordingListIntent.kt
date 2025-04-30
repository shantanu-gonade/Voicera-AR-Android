package com.example.voicera.presentation.recordings

/**
 * Sealed interface representing user intents (actions) for the Recording List Screen.
 * In MVI architecture, intents represent user actions that trigger state changes.
 */
sealed interface RecordingListIntent {
    /**
     * Intent to load all recordings
     */
    data object LoadRecordings : RecordingListIntent
    
    /**
     * Intent to delete a recording
     * @param recordingId The ID of the recording to delete
     */
    data class DeleteRecording(val recordingId: Long) : RecordingListIntent
    
    /**
     * Intent to show the tag edit dialog for a recording
     * @param recordingId The ID of the recording to edit
     * @param currentTag The current tag of the recording
     */
    data class ShowTagEditDialog(val recordingId: Long, val currentTag: String) : RecordingListIntent
    
    /**
     * Intent to dismiss the tag edit dialog
     */
    data object DismissTagEditDialog : RecordingListIntent
    
    /**
     * Intent to update the tag input for editing
     * @param tag The new tag value
     */
    data class UpdateTagEditInput(val tag: String) : RecordingListIntent
    
    /**
     * Intent to save the edited tag
     */
    data object SaveEditedTag : RecordingListIntent
    
    /**
     * Intent to navigate to the video screen
     */
    data object NavigateToVideoScreen : RecordingListIntent
    
    /**
     * Intent to play a recording
     * @param recordingId The ID of the recording to play
     */
    data class PlayRecording(val recordingId: Long) : RecordingListIntent
    
    /**
     * Intent to update the search query
     * @param query The search query
     */
    data class UpdateSearchQuery(val query: String) : RecordingListIntent
    
    /**
     * Intent to activate search
     */
    data object ActivateSearch : RecordingListIntent
    
    /**
     * Intent to deactivate search
     */
    data object DeactivateSearch : RecordingListIntent
    
    /**
     * Intent to handle errors
     * @param message The error message
     */
    data class HandleError(val message: String) : RecordingListIntent
    
    /**
     * Intent to clear errors
     */
    data object ClearError : RecordingListIntent
}
