package com.example.voicera.presentation.recordings

import com.example.voicera.data.model.Recording

/**
 * State class for the Recording List Screen.
 * This represents the UI state of the recordings list screen.
 */
data class RecordingListState(
    /**
     * List of recordings to display
     */
    val recordings: List<Recording> = emptyList(),
    
    /**
     * Whether the data is currently loading
     */
    val isLoading: Boolean = false,
    
    /**
     * Error message, if any
     */
    val error: String? = null,
    
    /**
     * ID of the recording being edited, if any
     */
    val editingRecordingId: Long? = null,
    
    /**
     * Current tag input value for editing
     */
    val currentEditTag: String = "",
    
    /**
     * Whether the tag edit dialog is visible
     */
    val isTagEditDialogVisible: Boolean = false,
    
    /**
     * Current search query
     */
    val searchQuery: String = "",
    
    /**
     * Whether the search is active
     */
    val isSearchActive: Boolean = false
)
