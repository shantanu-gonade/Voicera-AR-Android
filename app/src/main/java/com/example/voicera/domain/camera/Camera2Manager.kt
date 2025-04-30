package com.example.voicera.domain.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager as AndroidCameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.Session
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Manager class for camera operations using Camera2 API with shared access for ARCore.
 * This class is responsible for initializing the camera, capturing video, and managing the recording state.
 */
@Singleton
class Camera2Manager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Camera2Manager"
        private const val DURATION_UPDATE_INTERVAL = 100L // Update duration every 100ms
        private const val CAMERA_OPEN_TIMEOUT_MS = 2500L
        private const val RECONNECT_DELAY = 1000L
    }

    // Camera states
    sealed class Camera2State {
        object Closed : Camera2State()
        object Opening : Camera2State()
        object Configuring : Camera2State()
        object Preview : Camera2State()
        data class Recording(val outputUri: Uri) : Camera2State()
        data class Error(val message: String) : Camera2State()
    }

    // Camera events
    sealed class CameraEvent {
        data class FrameAvailable(val timestamp: Long) : CameraEvent()
        data class SharedCameraConfigReady(val cameraId: String) : CameraEvent()
        data class ArSurfaceReady(val cameraId: String, val surface: Surface) : CameraEvent()
        data class RecordingCompleted(val outputUri: Uri) : CameraEvent()
        data class Error(val message: String) : CameraEvent()
        object CameraDisconnected : CameraEvent()
    }

    // State flows
    private val _cameraState = MutableStateFlow<Camera2State>(Camera2State.Closed)
    val cameraState: StateFlow<Camera2State> = _cameraState.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    // Event flow
    private val _cameraEvents = MutableSharedFlow<CameraEvent>()
    val cameraEvents: SharedFlow<CameraEvent> = _cameraEvents.asSharedFlow()

    // Camera components
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var previewSurface: Surface? = null
    private var arSurface: Surface? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingSurface: Surface? = null

    // Camera settings
    private var cameraId: String? = null
    private var previewSize: Size = Size(1280, 720) // Default size
    private var videoSize: Size = Size(1280, 720) // Default size
    private var currentRotation = 0

    // Threads and handlers
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val cameraExecutor: Executor = Executors.newSingleThreadExecutor()
    private val cameraOpenCloseLock = Semaphore(1)

    // Recording timer
    private var recordingJob: Job? = null
    private val recordingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Orientation listener
    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return

            // Convert orientation to nearest 90-degree increment
            val rotation = (orientation + 45) / 90 * 90 % 360

            // Update camera rotation if changed
            if (currentRotation != rotation) {
                currentRotation = rotation
                updateCameraRotation()
            }
        }
    }

    init {
        // Start orientation listener
        orientationListener.enable()
    }

    /**
     * Initialize the camera with a surface.
     * @param lifecycleOwner The lifecycle owner
     * @param surface The surface for the camera preview
     * @return Whether the initialization was successful
     */
    fun initCamera(lifecycleOwner: LifecycleOwner, surface: Surface): Boolean {
        previewSurface = surface
        return openCamera()
    }

    /**
     * Open the camera and set up the preview session.
     * @return Whether the camera was opened successfully
     */
    @SuppressLint("MissingPermission")
    private fun openCamera(): Boolean {
        if (_cameraState.value !is Camera2State.Closed) {
            Log.d(TAG, "Camera is already open or in the process of opening")
            return false
        }

        _cameraState.value = Camera2State.Opening

        try {
            // Find the front-facing camera
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    this.cameraId = cameraId

                    // Get supported sizes
                    val streamConfigurationMap = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                    ) ?: continue

                    // Choose optimal preview size
                    previewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture::class.java).maxByOrNull {
                        it.width * it.height
                    } ?: Size(1280, 720)

                    // Choose optimal video size
                    videoSize = streamConfigurationMap.getOutputSizes(MediaRecorder::class.java).maxByOrNull {
                        it.width * it.height
                    } ?: Size(1280, 720)

                    break
                }
            }

            if (cameraId == null) {
                Log.e(TAG, "Failed to find front-facing camera")
                _cameraState.value = Camera2State.Error("Failed to find front-facing camera")
                return false
            }

            // Acquire the camera open/close lock
            if (!cameraOpenCloseLock.tryAcquire(CAMERA_OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Timeout waiting to lock camera for opening")
                _cameraState.value = Camera2State.Error("Timeout waiting to lock camera for opening")
                return false
            }

            // Open the camera
            cameraManager.openCamera(cameraId!!, cameraExecutor, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    cameraDevice = camera
                    _cameraState.value = Camera2State.Configuring
                    createCameraPreviewSession()

                    // Emit event that camera is ready for AR
                    coroutineScope.launch {
                        _cameraEvents.emit(CameraEvent.SharedCameraConfigReady(camera.id))
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                    _cameraState.value = Camera2State.Closed

                    // Emit disconnected event
                    coroutineScope.launch {
                        _cameraEvents.emit(CameraEvent.CameraDisconnected)
                    }

                    // Attempt to reopen after delay
                    cameraHandler.postDelayed({ openCamera() }, RECONNECT_DELAY)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null

                    val errorMsg = when (error) {
                        ERROR_CAMERA_DEVICE -> "Fatal camera device error"
                        ERROR_CAMERA_DISABLED -> "Camera disabled"
                        ERROR_CAMERA_IN_USE -> "Camera in use"
                        ERROR_CAMERA_SERVICE -> "Camera service error"
                        ERROR_MAX_CAMERAS_IN_USE -> "Too many cameras in use"
                        else -> "Unknown camera error"
                    }

                    Log.e(TAG, "Camera error: $errorMsg ($error)")
                    _cameraState.value = Camera2State.Error(errorMsg)

                    // Emit error event
                    coroutineScope.launch {
                        _cameraEvents.emit(CameraEvent.Error(errorMsg))
                    }

                    // Attempt recovery for certain errors
                    if (error != ERROR_CAMERA_IN_USE && error != ERROR_MAX_CAMERAS_IN_USE) {
                        cameraHandler.postDelayed({ openCamera() }, RECONNECT_DELAY)
                    }
                }
            })

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            _cameraState.value = Camera2State.Error("Failed to open camera: ${e.message}")
            return false
        }
    }

    /**
     * Create a camera preview session.
     */
    private fun createCameraPreviewSession() {
        try {
            val device = cameraDevice ?: return

            // Create a capture request builder for preview
            captureRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            // Add the preview surface as a target
            previewSurface?.let { surface ->
                captureRequestBuilder?.addTarget(surface)
            }

            // Create a capture session
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Use the new SessionConfiguration API for Android 9+
                val outputConfigs = mutableListOf<OutputConfiguration>()

                previewSurface?.let { surface ->
                    outputConfigs.add(OutputConfiguration(surface))
                }

                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    cameraExecutor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            onCaptureSessionConfigured(session)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            onCaptureSessionConfigureFailed()
                        }
                    }
                )

                device.createCaptureSession(sessionConfig)
            } else {
                // Use the old createCaptureSession API for Android 8.1 and below
                val surfaces = mutableListOf<Surface>()

                previewSurface?.let { surface ->
                    surfaces.add(surface)
                }

                device.createCaptureSession(
                    surfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            onCaptureSessionConfigured(session)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            onCaptureSessionConfigureFailed()
                        }
                    },
                    cameraHandler
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create camera preview session", e)
            _cameraState.value = Camera2State.Error("Failed to create camera preview session: ${e.message}")
        }
    }

    /**
     * Called when a capture session is successfully configured.
     * @param session The configured capture session
     */
    private fun onCaptureSessionConfigured(session: CameraCaptureSession) {
        cameraCaptureSession = session

        try {
            // Set auto-focus mode
            captureRequestBuilder?.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )

            // Set auto-exposure mode
            captureRequestBuilder?.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )

            // Start the preview
            val captureRequest = captureRequestBuilder?.build()
            if (captureRequest != null) {
                session.setRepeatingRequest(
                    captureRequest,
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            // Frame captured, emit event with timestamp
                            coroutineScope.launch {
                                _cameraEvents.emit(CameraEvent.FrameAvailable(result.get(
                                    TotalCaptureResult.SENSOR_TIMESTAMP)!!))
                            }
                        }
                    },
                    cameraHandler
                )

                _cameraState.value = Camera2State.Preview
                Log.d(TAG, "Camera preview started")
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start camera preview", e)
            _cameraState.value = Camera2State.Error("Failed to start camera preview: ${e.message}")
        }
    }

    /**
     * Called when a capture session configuration fails.
     */
    private fun onCaptureSessionConfigureFailed() {
        Log.e(TAG, "Failed to configure capture session")
        _cameraState.value = Camera2State.Error("Failed to configure capture session")
    }

    /**
     * Update the camera rotation based on device orientation.
     */
    private fun updateCameraRotation() {
        try {
            val characteristics = cameraId?.let { cameraManager.getCameraCharacteristics(it) }
            val sensorOrientation = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            // Calculate the correct rotation based on sensor orientation and device rotation
            val totalRotation = (sensorOrientation + currentRotation) % 360

            // Update capture request with rotation
            captureRequestBuilder?.set(CaptureRequest.JPEG_ORIENTATION, totalRotation)

            // Update the session if active
            val session = cameraCaptureSession
            val request = captureRequestBuilder?.build()
            if (session != null && request != null) {
                session.setRepeatingRequest(request, null, cameraHandler)
            }

            // Update recording orientation if needed
            if (_cameraState.value is Camera2State.Recording) {
                // Note: MediaRecorder orientation should be set before starting recording
                // This is just for future recordings
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update camera rotation", e)
        }
    }

    /**
     * Configure transform for the preview surface.
     * @param viewWidth The width of the view
     * @param viewHeight The height of the view
     * @param rotation The current rotation
     * @return The transformation matrix
     */
    fun configureTransform(viewWidth: Int, viewHeight: Int, rotation: Int): Matrix {
        val matrix = Matrix()

        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        // Apply rotations based on device orientation
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }

        return matrix
    }

    /**
     * Add a surface for AR processing.
     * @param surface The surface for AR processing
     */
    fun addArSurface(surface: Surface) {
        // Check if surface is valid
        if (!surface.isValid) {
            Log.e(TAG, "Attempted to add invalid AR surface, ignoring")
            return
        }
        
        // Check if the surface is the same as the current AR surface
        if (arSurface == surface) {
            Log.d(TAG, "AR surface is already set, skipping")
            return
        }
        
        // Store the new AR surface
        arSurface = surface

        // Update the capture session to include the AR surface
        updateCaptureSession()

        // Emit event that AR surface is ready
        coroutineScope.launch {
            cameraId?.let {
                _cameraEvents.emit(CameraEvent.ArSurfaceReady(it, surface))
            }
        }
    }

    /**
     * Update the capture session with current surfaces.
     */
    private fun updateCaptureSession() {
        val device = cameraDevice ?: return

        try {
            // Stop the current session
            cameraCaptureSession?.close()
            cameraCaptureSession = null

            // Create a new capture request builder
            captureRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            // Add all surfaces as targets
            val surfaces = mutableListOf<Surface>()

            previewSurface?.let { surface ->
                surfaces.add(surface)
                captureRequestBuilder?.addTarget(surface)
            }

            arSurface?.let { surface ->
                surfaces.add(surface)
                captureRequestBuilder?.addTarget(surface)
            }

            // Create a new capture session
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Use the new SessionConfiguration API for Android 9+
                val outputConfigs = surfaces.map { OutputConfiguration(it) }

                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    cameraExecutor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            onCaptureSessionConfigured(session)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            onCaptureSessionConfigureFailed()
                        }
                    }
                )

                device.createCaptureSession(sessionConfig)
            } else {
                // Use the old createCaptureSession API for Android 8.1 and below
                device.createCaptureSession(
                    surfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            onCaptureSessionConfigured(session)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            onCaptureSessionConfigureFailed()
                        }
                    },
                    cameraHandler
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update capture session", e)
            _cameraState.value = Camera2State.Error("Failed to update capture session: ${e.message}")
        }
    }

    /**
     * Start recording video.
     * @param outputUri The URI where the video will be saved
     * @return Whether the recording was started successfully
     */
    fun startRecording(outputUri: Uri): Boolean {
        if (_cameraState.value !is Camera2State.Preview) {
            Log.e(TAG, "Cannot start recording, camera not in preview state")
            _cameraState.value = Camera2State.Error("Camera not ready for recording")
            return false
        }

        try {
            // Create and configure the MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            configureMediaRecorder(outputUri)

            // Prepare the MediaRecorder
            mediaRecorder?.prepare()

            // Get the recording surface
            recordingSurface = mediaRecorder?.surface

            // Update the capture session to include the recording surface
            updateRecordingSession()

            // Start recording
            mediaRecorder?.start()

            // Update state
            _cameraState.value = Camera2State.Recording(outputUri)

            // Reset duration
            _recordingDuration.value = 0L

            // Start duration timer
            startDurationTimer()

            Log.d(TAG, "Recording started")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _cameraState.value = Camera2State.Error("Failed to start recording: ${e.message}")

            // Clean up
            mediaRecorder?.release()
            mediaRecorder = null
            recordingSurface = null

            return false
        }
    }

    /**
     * Configure the MediaRecorder.
     * @param outputUri The URI where the video will be saved
     */
    private fun configureMediaRecorder(outputUri: Uri) {
        val mediaRecorder = mediaRecorder ?: return

        try {
            // Set the output file
            mediaRecorder.setOutputFile(File(outputUri.path.toString()).absolutePath)

            // Set video source
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)

            // Set audio source
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)

            // Use CamcorderProfile for consistent settings
            val profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH)
            mediaRecorder.setOutputFormat(profile.fileFormat)
            mediaRecorder.setVideoFrameRate(profile.videoFrameRate)
            mediaRecorder.setVideoSize(videoSize.width, videoSize.height)
            mediaRecorder.setVideoEncodingBitRate(profile.videoBitRate)
            mediaRecorder.setVideoEncoder(profile.videoCodec)
            mediaRecorder.setAudioEncodingBitRate(profile.audioBitRate)
            mediaRecorder.setAudioChannels(profile.audioChannels)
            mediaRecorder.setAudioSamplingRate(profile.audioSampleRate)
            mediaRecorder.setAudioEncoder(profile.audioCodec)

            // Set orientation
            val characteristics = cameraId?.let { cameraManager.getCameraCharacteristics(it) }
            val sensorOrientation = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val totalRotation = (sensorOrientation + currentRotation) % 360
            mediaRecorder.setOrientationHint(totalRotation)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure MediaRecorder", e)
            throw e
        }
    }

    /**
     * Update the capture session for recording.
     */
    private fun updateRecordingSession() {
        val device = cameraDevice ?: return
        val recordingSurface = recordingSurface ?: return

        try {
            // Stop the current session
            cameraCaptureSession?.close()
            cameraCaptureSession = null

            // Create a new capture request builder for recording
            captureRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)

            // Add all surfaces as targets
            val surfaces = mutableListOf<Surface>()

            previewSurface?.let { surface ->
                surfaces.add(surface)
                captureRequestBuilder?.addTarget(surface)
            }

            arSurface?.let { surface ->
                surfaces.add(surface)
                captureRequestBuilder?.addTarget(surface)
            }

            // Add recording surface
            surfaces.add(recordingSurface)
            captureRequestBuilder?.addTarget(recordingSurface)

            // Create a new capture session
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Use the new SessionConfiguration API for Android 9+
                val outputConfigs = surfaces.map { OutputConfiguration(it) }

                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    cameraExecutor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            cameraCaptureSession = session

                            try {
                                // Set auto-focus mode
                                captureRequestBuilder?.set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                                )

                                // Set auto-exposure mode
                                captureRequestBuilder?.set(
                                    CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON
                                )

                                // Start the recording
                                val captureRequest = captureRequestBuilder?.build()
                                if (captureRequest != null) {
                                    session.setRepeatingRequest(
                                        captureRequest,
                                        null,
                                        cameraHandler
                                    )
                                }
                            } catch (e: CameraAccessException) {
                                Log.e(TAG, "Failed to start camera recording", e)
                                _cameraState.value = Camera2State.Error("Failed to start camera recording: ${e.message}")
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Failed to configure recording session")
                            _cameraState.value = Camera2State.Error("Failed to configure recording session")
                        }
                    }
                )

                device.createCaptureSession(sessionConfig)
            } else {
                // Use the old createCaptureSession API for Android 8.1 and below
                device.createCaptureSession(
                    surfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            cameraCaptureSession = session

                            try {
                                // Set auto-focus mode
                                captureRequestBuilder?.set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                                )

                                // Set auto-exposure mode
                                captureRequestBuilder?.set(
                                    CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON
                                )

                                // Start the recording
                                val captureRequest = captureRequestBuilder?.build()
                                if (captureRequest != null) {
                                    session.setRepeatingRequest(
                                        captureRequest,
                                        null,
                                        cameraHandler
                                    )
                                }
                            } catch (e: CameraAccessException) {
                                Log.e(TAG, "Failed to start camera recording", e)
                                _cameraState.value = Camera2State.Error("Failed to start camera recording: ${e.message}")
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Failed to configure recording session")
                            _cameraState.value = Camera2State.Error("Failed to configure recording session")
                        }
                    },
                    cameraHandler
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update recording session", e)
            _cameraState.value = Camera2State.Error("Failed to update recording session: ${e.message}")
        }
    }

    /**
     * Stop recording video.
     * @return Whether the recording was stopped successfully
     */
    fun stopRecording(): Boolean {
        if (_cameraState.value !is Camera2State.Recording) {
            // No active recording
            return true
        }

        val outputUri = (_cameraState.value as? Camera2State.Recording)?.outputUri

        try {
            // Stop duration timer
            stopDurationTimer()

            // Stop recording
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            recordingSurface = null

            // Restore preview session
            updateCaptureSession()

            // Update state
            _cameraState.value = Camera2State.Preview

            // Emit recording completed event
            if (outputUri != null) {
                coroutineScope.launch {
                    _cameraEvents.emit(CameraEvent.RecordingCompleted(outputUri))
                }
            }

            Log.d(TAG, "Recording stopped")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            _cameraState.value = Camera2State.Error("Failed to stop recording: ${e.message}")
            return false
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
            if (_cameraState.value is Camera2State.Recording) {
                stopRecording()
            }

            // Stop orientation listener
            orientationListener.disable()

            // Stop the camera thread
            cameraThread.quitSafely()
            cameraThread.join()

            // Close the camera capture session
            cameraCaptureSession?.close()
            cameraCaptureSession = null

            // Close the camera device
            cameraDevice?.close()
            cameraDevice = null

            // Release the media recorder
            mediaRecorder?.release()
            mediaRecorder = null

            // Clear surfaces
            previewSurface = null
            arSurface = null
            recordingSurface = null

            // Reset state
            _cameraState.value = Camera2State.Closed
            _recordingDuration.value = 0L

            // Shutdown executor
//            cameraExecutor.shutdown()

            Log.d(TAG, "Camera resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera resources", e)
        }
    }}
