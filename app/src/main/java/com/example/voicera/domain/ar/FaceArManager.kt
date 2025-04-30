package com.example.voicera.domain.ar

import android.util.Log
import android.view.Surface
import com.example.voicera.domain.camera.Camera2Manager
import com.example.voicera.domain.model.MustacheModel
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manager class for AR face tracking and mustache rendering.
 * This class is responsible for tracking faces and rendering 3D mustache models on detected faces.
 * It delegates AR session management to ARSessionManager.
 */
@ViewModelScoped
class FaceArManager @Inject constructor(
    private val arSessionManager: ARSessionManager,
    private val camera2Manager: Camera2Manager
) {
    companion object {
        private const val TAG = "FaceArManager"
    }
    
    // Face detection states
    sealed class FaceDetectionState {
        object NoFace : FaceDetectionState()
        data class FaceDetected(val faceId: Int) : FaceDetectionState()
    }
    
    // State flows
    private val _faceDetectionState = MutableStateFlow<FaceDetectionState>(FaceDetectionState.NoFace)
    val faceDetectionState: StateFlow<FaceDetectionState> = _faceDetectionState.asStateFlow()
    
    // AR state from session manager
    val arState = arSessionManager.sessionState
    
    // Face tracking
    private var currentMustache: MustacheModel? = null
    private var faceDetectionListener: ((Boolean) -> Unit)? = null
    private var isFaceDetected = false
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // We no longer need to observe camera events for AR session management
    // since the ARScene composable handles the camera and session lifecycle
    
    /**
     * Initialize the AR session.
     * @return Whether the initialization was successful
     * 
     * Note: The actual session initialization is now handled by ARSessionManager
     * which gets the session from the ARScene composable.
     */
    fun initArSession(): Boolean {
        // Just delegate to ARSessionManager
        return arSessionManager.initSession()
    }
    
    /**
     * Load a mustache model.
     * @param mustache The mustache model to load
     */
    suspend fun loadMustacheModel(mustache: MustacheModel) {
        try {
            currentMustache = mustache
            Log.d(TAG, "Loaded mustache model: ${mustache.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load mustache model: ${e.message}", e)
        }
    }
    
    /**
     * Update face tracking based on the current AR frame.
     * @param session The AR session
     * @param frame The current AR frame
     */
    fun updateFaceTracking(session: Session, frame: Frame) {
        try {
            // Get updated face trackables
            val faces = frame.getUpdatedTrackables(AugmentedFace::class.java)
            
            if (faces.isNotEmpty()) {
                // Get the first face (we only support one face at a time)
                val face = faces.first()
                
                if (face.trackingState == TrackingState.TRACKING) {
                    // Face is detected
                    if (!isFaceDetected) {
                        isFaceDetected = true
                        faceDetectionListener?.invoke(true)
                        _faceDetectionState.value = FaceDetectionState.FaceDetected(face.hashCode())
                        Log.d(TAG, "Face detected")
                    }
                    
                    // Here you could add code to render the mustache on the face
                    // using the current mustache model and face mesh data
                    
                } else {
                    // Face is not tracking
                    if (isFaceDetected) {
                        isFaceDetected = false
                        faceDetectionListener?.invoke(false)
                        _faceDetectionState.value = FaceDetectionState.NoFace
                        Log.d(TAG, "Face lost tracking")
                    }
                }
            } else {
                // No faces detected
                if (isFaceDetected) {
                    isFaceDetected = false
                    faceDetectionListener?.invoke(false)
                    _faceDetectionState.value = FaceDetectionState.NoFace
                    Log.d(TAG, "No faces detected")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating face tracking: ${e.message}", e)
        }
    }
    
    /**
     * Resume the AR session.
     */
    fun resumeArSession() {
        arSessionManager.resumeSession()
    }
    
    /**
     * Pause the AR session.
     */
    fun pauseArSession() {
        arSessionManager.pauseSession()
    }
    
    /**
     * Get the current AR session from the session manager.
     * @return The current AR session, or null if not available
     */
    fun getSession(): Session? {
        return arSessionManager.getSession()
    }
    
    /**
     * Set a listener for face detection events.
     * @param listener The listener to set
     */
    fun setFaceDetectionListener(listener: (Boolean) -> Unit) {
        faceDetectionListener = listener
    }
    
    /**
     * Destroy the AR session.
     */
    fun destroyArSession() {
        arSessionManager.destroySession()
    }
}
