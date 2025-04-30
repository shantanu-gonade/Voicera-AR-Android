package com.example.voicera.presentation.recordings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicera.data.model.Recording
import com.example.voicera.data.repository.RecordingRepository
import com.example.voicera.domain.util.FileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Recording List Screen.
 * This class is responsible for handling the business logic for the recordings list screen.
 */
@HiltViewModel
class RecordingListViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val fileManager: FileManager
) : ViewModel() {
    
    private val _state = MutableStateFlow(RecordingListState())
    val state: StateFlow<RecordingListState> = _state.asStateFlow()
    
    init {
        loadRecordings()
    }
    
    /**
     * Process an intent from the UI.
     * @param intent The intent to process
     */
    fun processIntent(intent: RecordingListIntent) {
        when (intent) {
            is RecordingListIntent.LoadRecordings -> loadRecordings()
            is RecordingListIntent.DeleteRecording -> deleteRecording(intent.recordingId)
            is RecordingListIntent.ShowTagEditDialog -> showTagEditDialog(intent.recordingId, intent.currentTag)
            is RecordingListIntent.DismissTagEditDialog -> dismissTagEditDialog()
            is RecordingListIntent.UpdateTagEditInput -> updateTagEditInput(intent.tag)
            is RecordingListIntent.SaveEditedTag -> saveEditedTag()
            is RecordingListIntent.NavigateToVideoScreen -> {
                // Navigation will be handled by the UI
            }
            is RecordingListIntent.PlayRecording -> {
                // Playback will be handled by the UI
            }
            is RecordingListIntent.UpdateSearchQuery -> updateSearchQuery(intent.query)
            is RecordingListIntent.ActivateSearch -> activateSearch()
            is RecordingListIntent.DeactivateSearch -> deactivateSearch()
            is RecordingListIntent.HandleError -> handleError(intent.message)
            is RecordingListIntent.ClearError -> clearError()
        }
    }
    
    /**
     * Load all recordings from the repository.
     */
    private fun loadRecordings() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            if (_state.value.isSearchActive && _state.value.searchQuery.isNotEmpty()) {
                searchRecordings(_state.value.searchQuery)
            } else {
                recordingRepository.getAllRecordings()
                    .catch { e ->
                        _state.update { it.copy(
                            isLoading = false,
                            error = e.message
                        ) }
                    }
                    .collectLatest { recordings ->
                        _state.update { it.copy(
                            recordings = recordings,
                            isLoading = false
                        ) }
                    }
            }
        }
    }
    
    /**
     * Search recordings by tag.
     * @param query The search query
     */
    private fun searchRecordings(query: String) {
        viewModelScope.launch {
            recordingRepository.searchRecordingsByTag(query)
                .catch { e ->
                    _state.update { it.copy(
                        isLoading = false,
                        error = e.message
                    ) }
                }
                .collectLatest { recordings ->
                    _state.update { it.copy(
                        recordings = recordings,
                        isLoading = false
                    ) }
                }
        }
    }
    
    /**
     * Delete a recording.
     * @param recordingId The ID of the recording to delete
     */
    private fun deleteRecording(recordingId: Long) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                
                // Get the recording
                val recording = recordingRepository.getRecordingById(recordingId)
                
                if (recording != null) {
                    // Delete the video file
                    val videoUri = Uri.parse(recording.videoPath)
                    fileManager.deleteFile(videoUri)
                    
                    // Delete the thumbnail file
                    val thumbnailUri = Uri.parse(recording.thumbnailPath)
                    fileManager.deleteFile(thumbnailUri)
                    
                    // Delete from database
                    recordingRepository.deleteRecordingById(recordingId)
                    
                    // Reload recordings
                    loadRecordings()
                } else {
                    _state.update { it.copy(
                        isLoading = false,
                        error = "Recording not found"
                    ) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Failed to delete recording: ${e.message}"
                ) }
            }
        }
    }
    
    /**
     * Show the tag edit dialog.
     * @param recordingId The ID of the recording to edit
     * @param currentTag The current tag of the recording
     */
    private fun showTagEditDialog(recordingId: Long, currentTag: String) {
        _state.update { it.copy(
            editingRecordingId = recordingId,
            currentEditTag = currentTag,
            isTagEditDialogVisible = true
        ) }
    }
    
    /**
     * Dismiss the tag edit dialog.
     */
    private fun dismissTagEditDialog() {
        _state.update { it.copy(
            editingRecordingId = null,
            currentEditTag = "",
            isTagEditDialogVisible = false
        ) }
    }
    
    /**
     * Update the tag input for editing.
     * @param tag The new tag value
     */
    private fun updateTagEditInput(tag: String) {
        _state.update { it.copy(currentEditTag = tag) }
    }
    
    /**
     * Save the edited tag.
     */
    private fun saveEditedTag() {
        val recordingId = _state.value.editingRecordingId ?: return
        val newTag = _state.value.currentEditTag
        
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                
                // Get the recording
                val recording = recordingRepository.getRecordingById(recordingId)
                
                if (recording != null) {
                    // Update the tag
                    val updatedRecording = recording.copy(tag = newTag)
                    recordingRepository.updateRecording(updatedRecording)
                    
                    // Dismiss the dialog
                    dismissTagEditDialog()
                    
                    // Reload recordings
                    loadRecordings()
                } else {
                    _state.update { it.copy(
                        isLoading = false,
                        error = "Recording not found"
                    ) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Failed to update tag: ${e.message}"
                ) }
            }
        }
    }
    
    /**
     * Update the search query.
     * @param query The search query
     */
    private fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        
        if (_state.value.isSearchActive && query.isNotEmpty()) {
            searchRecordings(query)
        } else if (_state.value.isSearchActive && query.isEmpty()) {
            loadRecordings()
        }
    }
    
    /**
     * Activate search mode.
     */
    private fun activateSearch() {
        _state.update { it.copy(isSearchActive = true) }
    }
    
    /**
     * Deactivate search mode.
     */
    private fun deactivateSearch() {
        _state.update { it.copy(
            isSearchActive = false,
            searchQuery = ""
        ) }
        loadRecordings()
    }
    
    /**
     * Handle an error.
     * @param message The error message
     */
    private fun handleError(message: String) {
        _state.update { it.copy(error = message) }
    }
    
    /**
     * Clear the error.
     */
    private fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
