package com.example.voicera.presentation.video

import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicera.data.model.Recording
import com.example.voicera.data.repository.RecordingRepository
import com.example.voicera.domain.ar.ARSessionManager
import com.example.voicera.domain.ar.FaceArManager
import com.example.voicera.domain.camera.Camera2Manager
import com.example.voicera.domain.util.FileManager
import com.example.voicera.domain.util.MustacheProvider
import com.example.voicera.domain.util.PermissionManager
import com.google.ar.core.Frame
import com.google.ar.core.RecordingConfig
import com.google.ar.core.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Video Screen.
 * This class is responsible for handling the business logic for the video recording screen.
 */
@HiltViewModel
class VideoScreenViewModel @Inject constructor(
    private val permissionManager: PermissionManager,
    private val camera2Manager: Camera2Manager,
    private val faceArManager: FaceArManager,
    private val arSessionManager: ARSessionManager,
    private val fileManager: FileManager,
    private val mustacheProvider: MustacheProvider,
    private val recordingRepository: RecordingRepository
) : ViewModel(){
    
    private val _state = MutableStateFlow(VideoScreenState())
    val state: StateFlow<VideoScreenState> = _state.asStateFlow()
    
    private var currentVideoUri: Uri? = null
    private var currentSurface: Surface? = null
    
    init {
        // Load available mustaches
        _state.update { it.copy(availableMustaches = mustacheProvider.getAvailableMustaches()) }
        
        // Set up face detection listener
        faceArManager.setFaceDetectionListener { faceDetected ->
            _state.update { it.copy(faceDetected = faceDetected) }
        }
        
        // Observe camera state
        viewModelScope.launch {
            camera2Manager.cameraState.collect { cameraState ->
                when (cameraState) {
                    is Camera2Manager.Camera2State.Preview -> {
                        _state.update { it.copy(isRecording = false) }
                    }
                    is Camera2Manager.Camera2State.Recording -> {
                        _state.update { it.copy(isRecording = true) }
                        currentVideoUri = cameraState.outputUri
                    }
                    is Camera2Manager.Camera2State.Error -> {
                        _state.update { it.copy(
                            isRecording = false,
                            error = cameraState.message
                        ) }
                    }
                    else -> {
                        // Other states don't need UI updates
                    }
                }
            }
        }

        // Observe camera events
        viewModelScope.launch {
            camera2Manager.cameraEvents.collect { event ->
                when (event) {
                    is Camera2Manager.CameraEvent.RecordingCompleted -> {
                        _state.update { it.copy(isRecording = false, isTagDialogVisible = true) }
                        currentVideoUri = event.outputUri
                    }
                    is Camera2Manager.CameraEvent.Error -> {
                        _state.update { it.copy(error = event.message) }
                    }
                    else -> {
                        // Other events don't need UI updates
                    }
                }
            }
        }

        // Observe recording duration
        viewModelScope.launch {
            camera2Manager.recordingDuration.collect { duration ->
                _state.update { it.copy(recordingDuration = duration) }
            }
        }

        // Observe AR session state
        viewModelScope.launch {
            arSessionManager.sessionState.collect { sessionState ->
                when (sessionState) {
                    is ARSessionManager.SessionState.Active -> {
                        Log.d("VideoScreenViewModel", "AR session is active")
                        _state.update { it.copy(arSessionInitialized = true) }
                    }
                    is ARSessionManager.SessionState.Error -> {
                        Log.e("VideoScreenViewModel", "AR session error: ${sessionState.message}")
                        _state.update { it.copy(
                            error = sessionState.message,
                            arSessionInitialized = false
                        ) }
                    }
                    else -> {
                        // Other states don't need UI updates
                    }
                }
            }
        }
        
        // Observe face detection state
        viewModelScope.launch {
            faceArManager.faceDetectionState.collect { faceState ->
                when (faceState) {
                    is FaceArManager.FaceDetectionState.FaceDetected -> {
                        _state.update { it.copy(faceDetected = true) }
                    }
                    is FaceArManager.FaceDetectionState.NoFace -> {
                        _state.update { it.copy(faceDetected = false) }
                    }
                }
            }
        }
        
        // Check permissions
        checkPermissions()
    }
    
    /**
     * Process an intent from the UI.
     * @param intent The intent to process
     */
    fun processIntent(intent: VideoScreenIntent) {
        when (intent) {
            is VideoScreenIntent.RequestCameraPermission -> requestCameraPermission()
            is VideoScreenIntent.RequestMicrophonePermission -> requestMicrophonePermission()
            is VideoScreenIntent.RequestStoragePermission -> requestStoragePermission()
            is VideoScreenIntent.InitializeCamera -> initializeCamera()
            is VideoScreenIntent.InitializeArSession -> initializeArSession()
            is VideoScreenIntent.StartRecording -> startRecording()
            is VideoScreenIntent.StopRecording -> stopRecording()
            is VideoScreenIntent.SelectMustache -> selectMustache(intent.index)
            is VideoScreenIntent.SaveRecording -> saveRecording(intent.tag)
            is VideoScreenIntent.NavigateToRecordings -> {
                // Navigation will be handled by the UI
            }
            is VideoScreenIntent.ShowTagDialog -> {
                _state.update { it.copy(isTagDialogVisible = true) }
            }
            is VideoScreenIntent.DismissTagDialog -> {
                _state.update { it.copy(isTagDialogVisible = false, currentTag = "") }
            }
            is VideoScreenIntent.UpdateTagInput -> {
                _state.update { it.copy(currentTag = intent.tag) }
            }
            is VideoScreenIntent.FaceDetectionStatus -> {
                _state.update { it.copy(faceDetected = intent.detected) }
            }
            is VideoScreenIntent.UpdateRecordingDuration -> {
                _state.update { it.copy(recordingDuration = intent.durationMs) }
            }
            is VideoScreenIntent.HandleError -> {
                _state.update { it.copy(error = intent.message) }
            }
            is VideoScreenIntent.ClearError -> {
                _state.update { it.copy(error = null) }
            }
        }
    }
    
    /**
     * Check if the required permissions are granted.
     */
    private fun checkPermissions() {
        _state.update { it.copy(
            cameraPermissionGranted = permissionManager.hasCameraPermission(),
            microphonePermissionGranted = permissionManager.hasMicrophonePermission(),
            storagePermissionGranted = permissionManager.hasStoragePermissions()
        ) }
    }
    
    /**
     * Request camera permission.
     * The actual permission request will be handled by the UI.
     */
    private fun requestCameraPermission() {
        // The UI will handle the actual permission request
        // This method is just a placeholder for the intent handler
    }
    
    /**
     * Request microphone permission.
     * The actual permission request will be handled by the UI.
     */
    private fun requestMicrophonePermission() {
        // The UI will handle the actual permission request
        // This method is just a placeholder for the intent handler
    }
    
    /**
     * Request storage permission.
     * The actual permission request will be handled by the UI.
     */
    private fun requestStoragePermission() {
        // The UI will handle the actual permission request
        // This method is just a placeholder for the intent handler
    }
    
    /**
     * Initialize the camera.
     * This should be called after the camera permission is granted.
     */
    private fun initializeCamera() {
        // The actual camera initialization will be done in the UI
        // when the surface is available
        _state.update { it.copy(cameraInitialized = true) }
    }
    
    /**
     * Initialize the AR session.
     * This should be called after the camera permission is granted.
     */
    private fun initializeArSession() {
        val success = faceArManager.initArSession()
        _state.update { it.copy(arSessionInitialized = success) }
        
        if (success) {
            // Load the initial mustache model
            val initialMustache = _state.value.availableMustaches.getOrNull(_state.value.selectedMustacheIndex)
            if (initialMustache != null) {
                viewModelScope.launch {
                    faceArManager.loadMustacheModel(initialMustache)
                }
            }
        }
    }
    
    // Recording timer job
    private var recordingTimerJob: Job? = null
    private val recordingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    /**
     * Start recording video using ARCore's recording capabilities.
     */
    private fun startRecording() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                
                val outputUri = fileManager.createVideoFile()
                val session = arSessionManager.getSession()
                
                if (session == null) {
                    _state.update { 
                        it.copy(
                            isLoading = false,
                            error = "AR session not available for recording"
                        ) 
                    }
                    return@launch
                }
                
                // Configure AR recording
                val recordingConfig = RecordingConfig(session)
                    .setMp4DatasetFilePath(outputUri.path)
                    .setAutoStopOnPause(true)
                
                // Start recording
                session.startRecording(recordingConfig)
                
                currentVideoUri = outputUri
                _state.update { it.copy(isLoading = false, isRecording = true) }
                
                // Start recording duration timer
                startRecordingDurationTimer()
                
                Log.d("VideoScreenViewModel", "AR recording started at ${outputUri.path}")
            } catch (e: Exception) {
                Log.e("VideoScreenViewModel", "Failed to start AR recording", e)
                _state.update { 
                    it.copy(
                        isLoading = false,
                        error = "Failed to start recording: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * Stop recording video.
     */
    private fun stopRecording() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                
                val session = arSessionManager.getSession()
                
                if (session == null) {
                    _state.update { 
                        it.copy(
                            isLoading = false,
                            error = "AR session not available to stop recording"
                        ) 
                    }
                    return@launch
                }
                
                // Stop recording
                session.stopRecording()
                
                // Stop the duration timer
                stopRecordingDurationTimer()
                
                _state.update { 
                    it.copy(
                        isLoading = false, 
                        isRecording = false,
                        isTagDialogVisible = true
                    ) 
                }
                
                Log.d("VideoScreenViewModel", "AR recording stopped")
            } catch (e: Exception) {
                Log.e("VideoScreenViewModel", "Failed to stop AR recording", e)
                _state.update { 
                    it.copy(
                        isLoading = false,
                        error = "Failed to stop recording: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * Start the recording duration timer.
     */
    private fun startRecordingDurationTimer() {
        // Cancel any existing job
        recordingTimerJob?.cancel()
        
        // Reset duration
        _state.update { it.copy(recordingDuration = 0L) }
        
        // Start a new timer
        recordingTimerJob = recordingScope.launch {
            val startTime = System.currentTimeMillis()
            
            while (true) {
                val currentTime = System.currentTimeMillis()
                _state.update { it.copy(recordingDuration = currentTime - startTime) }
                delay(100) // Update every 100ms
            }
        }
    }
    
    /**
     * Stop the recording duration timer.
     */
    private fun stopRecordingDurationTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
    }
    
    /**
     * Select a mustache.
     * @param index The index of the mustache to select
     */
    private fun selectMustache(index: Int) {
        if (index < 0 || index >= _state.value.availableMustaches.size) {
            return
        }
        
        _state.update { it.copy(selectedMustacheIndex = index) }
        
        val selectedMustache = _state.value.availableMustaches[index]
        viewModelScope.launch {
            faceArManager.loadMustacheModel(selectedMustache)
        }
    }
    
    /**
     * Save a recording with a tag.
     * @param tag The tag for the recording
     */
    private fun saveRecording(tag: String) {
        val videoUri = currentVideoUri ?: return
        
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                
                // Generate thumbnail
                val thumbnailPath = fileManager.generateThumbnail(videoUri)
                
                // Get video metadata
                val duration = fileManager.getVideoDuration(videoUri)
                val fileSize = fileManager.getFileSize(videoUri)
                val resolution = fileManager.getVideoResolution(videoUri)
                
                // Get selected mustache
                val mustacheType = _state.value.availableMustaches
                    .getOrNull(_state.value.selectedMustacheIndex)?.name ?: "Unknown"
                
                // Create recording entity
                val recording = Recording(
                    videoPath = videoUri.toString(),
                    thumbnailPath = thumbnailPath,
                    duration = duration,
                    tag = tag,
                    mustacheType = mustacheType,
                    fileSize = fileSize,
                    resolution = resolution
                )
                
                // Save to database
                recordingRepository.insertRecording(recording)
                
                // Clear the current video URI and tag
                currentVideoUri = null
                _state.update { it.copy(
                    isTagDialogVisible = false,
                    currentTag = "",
                    isLoading = false
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(
                    error = "Failed to save recording: ${e.message}",
                    isLoading = false
                ) }
            }
        }
    }
    
    /**
     * Initialize the camera with a surface.
     * @param lifecycleOwner The lifecycle owner
     * @param surface The surface for the camera preview
     */
    fun initCameraWithSurface(lifecycleOwner: LifecycleOwner, surface: Surface) {
        // Register the ARSessionManager as a lifecycle observer
        lifecycleOwner.lifecycle.addObserver(arSessionManager)

        currentSurface = surface
        val success = camera2Manager.initCamera(lifecycleOwner, surface)
        
        if (success) {
            // Add surface for AR processing
            addArSurface(surface)
        }
        
        _state.update { it.copy(cameraInitialized = success) }
    }
    
    /**
     * Add a surface for AR processing.
     * @param surface The surface for AR processing
     */
    fun addArSurface(surface: Surface) {
        if (surface.isValid) {
            camera2Manager.addArSurface(surface)
        }
    }
    
    /**
     * Handle surface destroyed event.
     */
    fun handleSurfaceDestroyed() {
        currentSurface = null
        Log.d("VideoScreenViewModel", "Surface destroyed")
    }
    
    /**
     * Update the face tracking.
     * @param session The AR session
     * @param frame The current AR frame
     */
    fun updateFaceTracking(session: Session, frame: Frame) {
        faceArManager.updateFaceTracking(session, frame)
    }
    
    /**
     * Handle AR session created event.
     * @param session The AR session
     */
    fun handleArSessionCreated(session: Session) {
        // Log the session creation
        Log.d("VideoScreenViewModel", "AR session created")
    }
    
    /**
     * Set the AR session in the ARSessionManager.
     * @param session The AR session to set
     */
    fun setArSession(session: Session) {
        arSessionManager.setSession(session)
    }
    
    /**
     * Handle AR session error.
     * @param message The error message
     */
    fun handleArSessionError(message: String) {
        _state.update { it.copy(error = message) }
    }
    
    /**
     * Resume the AR session.
     */
    fun resumeArSession() {
        faceArManager.resumeArSession()
    }
    
    /**
     * Pause the AR session.
     */
    fun pauseArSession() {
        faceArManager.pauseArSession()
    }
    
    /**
     * Clean up resources when the ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        camera2Manager.releaseCamera()
        faceArManager.destroyArSession()
    }
}
