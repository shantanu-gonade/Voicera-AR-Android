package com.example.voicera.domain.ar

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.SharedCamera
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages AR session lifecycle and configuration.
 * This class is responsible for creating, configuring, and managing the AR session.
 */
@Singleton
class ARSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) : DefaultLifecycleObserver {
    companion object {
        private const val TAG = "ARSessionManager"
    }
    
    // AR session states
    sealed class SessionState {
        object Inactive : SessionState()
        object Initializing : SessionState()
        object Active : SessionState()
        object Paused : SessionState()
        object Resuming : SessionState()
        data class Error(val message: String) : SessionState()
    }
    
    // State flow for AR session state
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Inactive)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()
    
    // AR session and shared camera
    private var arSession: Session? = null
    private var sharedCamera: SharedCamera? = null
    
    /**
     * Initialize the AR session.
     * This method no longer creates a new session but prepares the manager to receive one.
     * @return Whether initialization was successful
     */
    fun initSession(): Boolean {
        _sessionState.value = SessionState.Initializing
        
        // We don't create a new session here anymore
        // The session will be set externally from VideoScreen's ARScene
        if (arSession != null) {
            _sessionState.value = SessionState.Active
            Log.d(TAG, "AR session already initialized")
            return true
        }
        
        _sessionState.value = SessionState.Inactive
        Log.d(TAG, "AR session initialization deferred to ARScene")
        return true
    }
    
    /**
     * Set the AR session from an external source.
     * @param session The AR session to use
     */
    fun setSession(session: Session) {
        if (arSession != null) {
            // If we already have a session, we need to clean it up first
            Log.d(TAG, "Replacing existing AR session")
            arSession = null
            sharedCamera = null
        }
        
        arSession = session
//        sharedCamera = session.sharedCamera
        _sessionState.value = SessionState.Active
        Log.d(TAG, "External AR session set")
    }
    
    /**
     * Resume the AR session.
     */
    fun resumeSession() {
        // We don't manually resume the session anymore
        // The ARScene composable handles session lifecycle
        if (arSession != null) {
            _sessionState.value = SessionState.Resuming
            _sessionState.value = SessionState.Active
            Log.d(TAG, "AR session resume delegated to ARScene")
        } else {
            Log.w(TAG, "Cannot resume null AR session")
            _sessionState.value = SessionState.Inactive
        }
    }
    
    /**
     * Pause the AR session.
     */
    fun pauseSession() {
        // We don't manually pause the session anymore
        // The ARScene composable handles session lifecycle
        if (arSession != null) {
            _sessionState.value = SessionState.Paused
            Log.d(TAG, "AR session pause delegated to ARScene")
        } else {
            Log.w(TAG, "Cannot pause null AR session")
        }
    }
    
    /**
     * Update the AR surface.
     * @param cameraId The camera ID
     * @param surface The surface for AR processing
     */
    fun updateSurface(cameraId: String, surface: Surface) {
        try {
            // Check if surface is valid
            if (!surface.isValid) {
                Log.e(TAG, "Surface is not valid, skipping setAppSurfaces")
                return
            }
            
            // Check if shared camera is initialized
            if (sharedCamera == null) {
                Log.e(TAG, "Shared camera is null, cannot set app surfaces")
                return
            }
            
            // Update the AR session with the new surface
            sharedCamera?.setAppSurfaces(cameraId, listOf(surface))
            Log.d(TAG, "AR surface updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update AR surface", e)
            _sessionState.value = SessionState.Error("Failed to update AR surface: ${e.message}")
        }
    }
    
    /**
     * Destroy the AR session.
     */
    fun destroySession() {
        // We don't manually destroy the session anymore
        // The ARScene composable handles session lifecycle
        if (arSession != null) {
            // Just clear our references, don't actually close the session
            arSession = null
            sharedCamera = null
            _sessionState.value = SessionState.Inactive
            Log.d(TAG, "AR session destroy delegated to ARScene")
        }
    }
    
    /**
     * Get the current AR session.
     * @return The current AR session
     */
    fun getSession(): Session? = arSession
    
    // Lifecycle observer methods
    
    override fun onResume(owner: LifecycleOwner) {
        resumeSession()
    }
    
    override fun onPause(owner: LifecycleOwner) {
        pauseSession()
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        destroySession()
    }
}
