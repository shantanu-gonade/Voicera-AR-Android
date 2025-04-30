package com.example.voicera.domain.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager class for camera and video recording.
 * This class is responsible for initializing the camera, capturing video, and managing the recording state.
 */
@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CameraManager"
        private const val DURATION_UPDATE_INTERVAL = 100L // Update duration every 100ms
    }

    // Recording state
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    // Recording duration
    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    // Camera components
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    // Recording timer
    private var recordingJob: Job? = null
    private val recordingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Camera executor
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Initialize the camera with a surface.
     * @param lifecycleOwner The lifecycle owner
     * @param surface The surface for the camera preview
     * @return Whether the initialization was successful
     */
    fun initCamera(lifecycleOwner: LifecycleOwner, surface: Surface): Boolean {
        return try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProvider = cameraProviderFuture.get()
            cameraProvider?.unbindAll()

            // Set up the preview use case
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider { request ->
                        provideSurface(request, surface)
                    }
                }

            // Set up the recorder
            val recorder = Recorder.Builder()
                .setQualitySelector(androidx.camera.video.QualitySelector.from(
                    androidx.camera.video.Quality.HIGHEST
                ))
                .build()

            // Set up the video capture use case
            videoCapture = VideoCapture.withOutput(recorder)

            // Select the front camera
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            try {
                // Bind use cases to camera
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )

                Log.d(TAG, "Camera initialized successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                _recordingState.value = RecordingState.Error("Failed to bind camera use cases: ${e.message}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera initialization failed", e)
            _recordingState.value = RecordingState.Error("Camera initialization failed: ${e.message}")
            false
        }
    }

    /**
     * Provide a surface for the camera preview.
     * @param request The surface request
     * @param surface The surface to provide
     */
    private fun provideSurface(request: SurfaceRequest, surface: Surface) {
        // Provide the surface to the camera
        request.provideSurface(
            surface,
            cameraExecutor
        ) { result ->
            if (result.resultCode == SurfaceRequest.Result.RESULT_INVALID_SURFACE || result.resultCode == SurfaceRequest.Result.RESULT_WILL_NOT_PROVIDE_SURFACE || result.resultCode == SurfaceRequest.Result.RESULT_REQUEST_CANCELLED) {
                Log.e(TAG, "Surface provision failed")
                _recordingState.value = RecordingState.Error("Failed to provide surface: ${result.resultCode}")
            }
        }
    }

    /**
     * Start recording video.
     * @param outputUri The URI where the video will be saved
     * @return Whether the recording was started successfully
     */
    fun startRecording(outputUri: Uri): Boolean {
        if (videoCapture == null) {
            Log.e(TAG, "Cannot start recording, VideoCapture not initialized")
            _recordingState.value = RecordingState.Error("Camera not initialized")
            return false
        }

        // Update state to Starting
        _recordingState.value = RecordingState.Starting

        try {
            // Stop any existing recording
            stopRecording()

            // Reset duration
            _recordingDuration.value = 0L

            // Create output options
            val outputOptions = FileOutputOptions.Builder(File(outputUri.path.toString())).build()

            // Start recording
            recording = videoCapture?.output
                ?.prepareRecording(context, outputOptions)
                ?.apply {
                    // Enable audio recording
                    withAudioEnabled()
                }
                ?.start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            // Recording started successfully
                            _recordingState.value = RecordingState.Recording(outputUri)

                            // Start duration timer
                            startDurationTimer()

                            Log.d(TAG, "Recording started")
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (event.hasError()) {
                                // Recording failed
                                Log.e(TAG, "Recording failed: ${event.error}")
                                _recordingState.value = RecordingState.Error("Recording failed: ${event.error}")

                                // Stop duration timer
                                stopDurationTimer()
                            } else {
                                // Recording finished successfully
                                _recordingState.value = RecordingState.Finished(outputUri)

                                // Stop duration timer
                                stopDurationTimer()

                                Log.d(TAG, "Recording finished")
                            }

                            recording = null
                        }
                    }
                }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _recordingState.value = RecordingState.Error("Failed to start recording: ${e.message}")
            return false
        }
    }

    /**
     * Stop recording video.
     * @return Whether the recording was stopped successfully
     */
    fun stopRecording(): Boolean {
        if (recording == null) {
            // No active recording
            return true
        }

        // Update state to Stopping
        _recordingState.value = RecordingState.Stopping

        return try {
            // Stop recording
            recording?.stop()
            recording = null

            Log.d(TAG, "Recording stopped")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            _recordingState.value = RecordingState.Error("Failed to stop recording: ${e.message}")
            false
        }
    }

    /**
     * Start the recording duration timer.
     */
    private fun startDurationTimer() {
        // Cancel any existing job
        recordingJob?.cancel()

        // Reset duration
        _recordingDuration.value = 0L

        // Start a new timer
        recordingJob = recordingScope.launch {
            val startTime = System.currentTimeMillis()

            while (true) {
                val currentTime = System.currentTimeMillis()
                _recordingDuration.value = currentTime - startTime
                delay(DURATION_UPDATE_INTERVAL)
            }
        }
    }

    /**
     * Stop the recording duration timer.
     */
    private fun stopDurationTimer() {
        recordingJob?.cancel()
        recordingJob = null
    }

    /**
     * Release camera resources.
     */
    fun releaseCamera() {
        try {
            // Stop any active recording
            stopRecording()

            // Unbind all use cases
            cameraProvider?.unbindAll()
            cameraProvider = null

            // Shutdown executor
            cameraExecutor.shutdown()

            // Reset state
            _recordingState.value = RecordingState.Idle
            _recordingDuration.value = 0L

            Log.d(TAG, "Camera resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera resources", e)
        }
    }
}

/**
 * Sealed class representing the state of the recording.
 */
sealed class RecordingState {
    /**
     * The camera is idle, not recording.
     */
    data object Idle : RecordingState()

    /**
     * The recording is starting.
     */
    data object Starting : RecordingState()

    /**
     * The camera is recording.
     * @param outputUri The URI where the video is being saved
     */
    data class Recording(val outputUri: Uri) : RecordingState()

    /**
     * The recording is stopping.
     */
    data object Stopping : RecordingState()

    /**
     * The recording has finished.
     * @param outputUri The URI where the video was saved
     */
    data class Finished(val outputUri: Uri) : RecordingState()

    /**
     * An error occurred during recording.
     * @param error The error message
     */
    data class Error(val error: String) : RecordingState()
}
