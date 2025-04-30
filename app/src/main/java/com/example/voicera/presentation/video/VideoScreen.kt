package com.example.voicera.presentation.video

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.voicera.domain.ar.AugmentedFaceNodeModel
import com.example.voicera.presentation.components.ErrorDialog
import com.example.voicera.presentation.components.FaceDetectionIndicator
import com.example.voicera.presentation.components.LoadingIndicator
import com.example.voicera.presentation.components.MustacheSelector
import com.example.voicera.presentation.components.RecordButton
import com.example.voicera.presentation.components.RecordingDuration
import com.example.voicera.presentation.components.TagInputDialog
import com.example.voicera.presentation.components.VoiceraTopAppBar
import com.google.ar.core.ArCoreApk
import com.google.ar.core.AugmentedFace
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Config.AugmentedFaceMode
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AugmentedFaceNode
import io.github.sceneview.ar.rememberARCameraStream
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView

/**
 * Video screen composable.
 * This screen displays the camera preview with AR face tracking and recording controls.
 * @param navController The navigation controller
 * @param viewModel The view model for this screen
 */
@Composable
fun VideoScreen(
    navController: NavController,
    viewModel: VideoScreenViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val modelLoader = rememberModelLoader(engine)
    val childNodes = rememberNodes()
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)

    // Face tracking state
    var augmentedFaceNode by remember { mutableStateOf<AugmentedFaceNodeModel?>(null) }
    var mustacheNode by remember { mutableStateOf<ModelNode?>(null) }
    var currentMustacheIndex by remember { mutableStateOf(-1) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var mustacheModelInstance by remember { mutableStateOf<ModelInstance?>(null) }
    var isLoadingModel by remember { mutableStateOf(false) }

    // Permission launchers
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.processIntent(VideoScreenIntent.InitializeCamera)
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.processIntent(VideoScreenIntent.RequestStoragePermission)
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // All permissions granted, we're ready to go
        }
    }

    // Function to update or create mustache node
    fun updateOrCreateMustacheNode(mustacheIndex: Int) {
        if (augmentedFaceNode == null || mustacheIndex < 0 || mustacheIndex >= state.availableMustaches.size) {
            return
        }

        val mustacheInfo = state.availableMustaches[mustacheIndex]

        // Remove existing mustache node if it exists
        mustacheNode?.let { node ->
            augmentedFaceNode?.regionNodes?.get(AugmentedFace.RegionType.NOSE_TIP)?.childNodes?.toMutableList()
                ?.remove(node)
            mustacheNode = null
        }

        // Don't proceed if we're still loading a model
        if (isLoadingModel) return

        // Load and create new mustache node
        isLoadingModel = true
        
        try {
            // Use loadModelInstanceAsync to load the model
            // The path needs to be prefixed with "models/" to match the asset folder structure
            val modelPath = if (!mustacheInfo.modelPath.startsWith("models/")) {
                "models/${mustacheInfo.modelPath.substringAfterLast("/")}"
            } else {
                mustacheInfo.modelPath
            }
            
            Log.d("VideoScreen", "Loading mustache model from path: $modelPath")
            
            // Validate that the model file exists before attempting to load it
            val assetExists = try {
                context.assets.open(modelPath).use { true }
            } catch (e: Exception) {
                Log.e("VideoScreen", "Model file not found: $modelPath", e)
                false
            }
            
            if (!assetExists) {
                Log.e("VideoScreen", "Mustache model file not found: $modelPath")
                viewModel.processIntent(VideoScreenIntent.HandleError("Mustache model file not found"))
                isLoadingModel = false
                return
            }

            modelLoader.loadModelInstanceAsync(
                fileLocation = "models/mustache_pencil.glb",
                onResult = { modelInstance ->
                    if (modelInstance == null) {
                        Log.e("VideoScreen", "Failed to load mustache model: Model instance is null")
                        viewModel.processIntent(VideoScreenIntent.HandleError("Failed to load mustache model"))
                        isLoadingModel = false
                        return@loadModelInstanceAsync
                    }

                    try {
                        // Log entity information for debugging
                        Log.d("VideoScreen", "Model loaded with ${modelInstance.entities.size} entities")

                        val renderableEntities = modelInstance.entities
                        if (renderableEntities.isEmpty()) {
                            Log.e("VideoScreen", "Model has no renderable entities")
                            viewModel.processIntent(VideoScreenIntent.HandleError("Model has no renderable entities"))
                            isLoadingModel = false
                            return@loadModelInstanceAsync
                        }

                        Log.d("VideoScreen", "Model has ${renderableEntities.size} renderable entities")


                        // Create the ModelNode with safeguards against errors
                        try {
                            val newMustacheNode = ModelNode(
                                modelInstance = modelInstance,
                                // Important: Auto-scale the model to a reasonable size if needed
                                scaleToUnits = 0.1f, // Scale to 10cm - adjust as needed
                                autoAnimate = false // Disable animations since this is a static model
                            ).apply {
                                // Apply position and scale after the node is created
                                // This happens after the ModelNode constructor has done its validation
                                scale = Float3(
                                    mustacheInfo.scale,
                                    mustacheInfo.scale,
                                    mustacheInfo.scale
                                )

                                position = Float3(
                                    mustacheInfo.horizontalOffset,
                                    mustacheInfo.verticalOffset,
                                    0.01f // Slight offset forward
                                )
                            }

                                augmentedFaceNode?.regionNodes?.get(AugmentedFace.RegionType.NOSE_TIP)?.let { noseTipNode ->
                                    // Success case - attach the mustache
                                    newMustacheNode.parent = noseTipNode
                                    mustacheNode = newMustacheNode
                                    Log.d("VideoScreen", "Mustache attached successfully to face node")
                                } ?: run {
                                    Log.e("VideoScreen", "Nose tip region node not found")
                                    viewModel.processIntent(VideoScreenIntent.HandleError("Nose tip region node not found"))
                                }
                        } catch (e: Exception) {
                            // This exception would likely be where the "vertexCount cannot be 0" error occurs
                            Log.e("VideoScreen", "Error creating ModelNode: ${e.message}", e)
                            viewModel.processIntent(VideoScreenIntent.HandleError("Error creating model node: ${e.message}"))
                        }

                    } catch (e: Exception) {
                        Log.e("VideoScreen", "Error processing loaded model: ${e.message}", e)
                        viewModel.processIntent(VideoScreenIntent.HandleError("Error processing loaded model: ${e.message}"))
                    } finally {
                        isLoadingModel = false
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("VideoScreen", "Error loading mustache model", e)
            viewModel.processIntent(VideoScreenIntent.HandleError("Error loading mustache model: ${e.localizedMessage}"))
            isLoadingModel = false
        }
    }

    // Function to handle face tracking
    fun updateFaceTracking(session: Session, updatedFrame: Frame) {
        try {
            // Get all tracked faces
            val faces = updatedFrame.getUpdatedTrackables(AugmentedFace::class.java)

            // Update face detection status for UI
            val hasFace = faces.any { it.trackingState == TrackingState.TRACKING }
            viewModel.processIntent(VideoScreenIntent.FaceDetectionStatus(hasFace))

            if (!hasFace) {
                // No trackable faces detected
                if (augmentedFaceNode != null) {
                    // Remove existing face node when no face is detected
                    augmentedFaceNode?.let { node ->
                        childNodes.remove(node)
                    }
                    augmentedFaceNode = null
                    mustacheNode = null
                }
                return
            }

            // Get the first tracked face
            val face = faces.firstOrNull { it.trackingState == TrackingState.TRACKING } ?: return



            if (augmentedFaceNode == null) {
                try {
                    if (face.trackingState == TrackingState.TRACKING) {
                        // UVs and indices can be cached as they do not change during the session.
                        val uvs = face.meshTextureCoordinates
                        val indices = face.meshTriangleIndices
                        // Center and region poses, mesh vertices, and normals are updated each frame.
                        val facePose = face.centerPose
                        val faceVertices = face.meshVertices
                        val faceNormals = face.meshNormals
                        // Render the face using these values with OpenGL.
                    }
                    // Create a new augmented face node with proper error handling
                    augmentedFaceNode = AugmentedFaceNodeModel(
                        engine = engine,
                        augmentedFace = face,
                        builder = {
                                // Face is no longer being tracked
                                viewModel.processIntent(VideoScreenIntent.FaceDetectionStatus(false))
                                
                                // Clean up mustache node when tracking is lost
                                mustacheNode?.let { node ->
                                    augmentedFaceNode?.regionNodes?.get(AugmentedFace.RegionType.NOSE_TIP)?.childNodes?.toMutableList()
                                        ?.remove(node)
                                    mustacheNode = null
                                }

                        }
                    ).also { newFaceNode ->
                        // Add the face node to the scene
                        childNodes.add(newFaceNode)

                        // Apply selected mustache if we have one
                        if (state.selectedMustacheIndex != currentMustacheIndex) {
                            currentMustacheIndex = state.selectedMustacheIndex
                            updateOrCreateMustacheNode(currentMustacheIndex)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VideoScreen", "Error creating AugmentedFaceNode", e)
                    viewModel.processIntent(VideoScreenIntent.HandleError("Error creating face tracking: ${e.localizedMessage}"))
                    return
                }
            } else {
                try {
                    // Update existing face node with new face data
                    augmentedFaceNode?.update(face)
                } catch (e: Exception) {
                    Log.e("VideoScreen", "Error updating AugmentedFaceNode", e)
                    
                    // If updating fails, remove the node and create a new one next frame
                    augmentedFaceNode?.let { node ->
                        childNodes.remove(node)
                    }
                    augmentedFaceNode = null
                    mustacheNode = null
                }
            }
        } catch (e: Exception) {
            Log.e("VideoScreen", "Error in face tracking", e)
            viewModel.processIntent(VideoScreenIntent.HandleError("Face tracking error: ${e.localizedMessage}"))
        }
    }

    // Watch for mustache selection changes
    LaunchedEffect(state.selectedMustacheIndex) {
        if (state.selectedMustacheIndex != currentMustacheIndex) {
            currentMustacheIndex = state.selectedMustacheIndex
            updateOrCreateMustacheNode(currentMustacheIndex)
        }
    }

    // Check ARCore availability
    LaunchedEffect(Unit) {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isSupported) {
            // Request camera permission if not granted
            if (!state.cameraPermissionGranted) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                viewModel.processIntent(VideoScreenIntent.InitializeCamera)
            }
        } else {
            viewModel.processIntent(
                VideoScreenIntent.HandleError("ARCore is not supported on this device")
            )
        }
    }

    // Request microphone permission if camera permission is granted but microphone is not
    LaunchedEffect(state.cameraPermissionGranted) {
        if (state.cameraPermissionGranted && !state.microphonePermissionGranted) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Request storage permissions if needed
    LaunchedEffect(state.cameraPermissionGranted, state.microphonePermissionGranted) {
        if (state.cameraPermissionGranted && state.microphonePermissionGranted && !state.storagePermissionGranted) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                storagePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO
                    )
                )
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                storagePermissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                )
            } else {
                storagePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }

    // Clean up resources when component is disposed
    DisposableEffect(Unit) {
        onDispose {
            // Clean up face node
            augmentedFaceNode?.let { childNodes.remove(it) }
            augmentedFaceNode = null

            // Clean up mustache node (already handled by removing the parent face node)
            mustacheNode = null

            // Release model instance - no need to explicitly destroy it
            // The ModelLoader will handle cleanup when it's destroyed
            mustacheModelInstance = null
            
            // Cancel any ongoing model loading
            if (isLoadingModel) {
                isLoadingModel = false
                // The coroutine scope in ModelLoader will be cancelled when the activity is destroyed
            }
        }
    }

    Scaffold(
        topBar = {
            VoiceraTopAppBar(
                title = "Voicera",
                navController = navController
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // AR Scene
            ARScene(
                modifier = Modifier.fillMaxSize(),
                childNodes = childNodes,
                engine = engine,
                view = view,
                modelLoader = modelLoader,
                collisionSystem = collisionSystem,


                // Configure camera for front-facing camera and face tracking
                sessionCameraConfig = { session ->
                    val cameraConfigFilter = CameraConfigFilter(session)
                        .setFacingDirection(CameraConfig.FacingDirection.FRONT)

                    val filteredConfigs = session.getSupportedCameraConfigs(cameraConfigFilter)
                    if (filteredConfigs.isNotEmpty()) {
                        filteredConfigs[0]
                    } else {
                        session.cameraConfig
                    }
                },

                // Configure AR session for face tracking
                sessionConfiguration = { session, config ->
                    // Set face mesh mode for 3D tracking
                    config.augmentedFaceMode = AugmentedFaceMode.MESH3D

                    // Optimize for face tracking
                    config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    config.focusMode = Config.FocusMode.AUTO

                    // Disable unnecessary features for face tracking
                    config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                    config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                    config.lightEstimationMode = Config.LightEstimationMode.DISABLED

                    // Enable depth if supported
                    config.depthMode =
                        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            Config.DepthMode.AUTOMATIC
                        } else {
                            Config.DepthMode.DISABLED
                        }
                    // Configure the session
                    session.configure(config)
                },

                // Use camera stream for rendering
                cameraStream = rememberARCameraStream(materialLoader),

                // Session features for face tracking
                sessionFeatures = setOf(Session.Feature.FRONT_CAMERA),
                
                // Session lifecycle callbacks
                onSessionCreated = { session ->
                    Log.d("VideoScreen", "AR session created")
                    viewModel.setArSession(session)
                    viewModel.processIntent(VideoScreenIntent.InitializeArSession)
                },
                onSessionResumed = { session ->
                    Log.d("VideoScreen", "AR session resumed")
                    viewModel.resumeArSession()
                },
                onSessionPaused = { session ->
                    Log.d("VideoScreen", "AR session paused")
                    viewModel.pauseArSession()
                },

                // Process frames for face tracking
                onSessionUpdated = { session, updatedFrame ->
                    try {
                        // Update face tracking with the latest frame
                        updateFaceTracking(session, updatedFrame)
                        
                        // If we have a face node and a selected mustache, make sure it's applied
                        if (augmentedFaceNode != null &&
                            state.selectedMustacheIndex >= 0 && 
                            state.selectedMustacheIndex != currentMustacheIndex) {
                            currentMustacheIndex = state.selectedMustacheIndex
                            updateOrCreateMustacheNode(currentMustacheIndex)
                        }
                    } catch (e: Exception) {
                        Log.e("VideoScreen", "Error in session update", e)
                        // Don't show error to user for every frame, just log it
                    }
                },

                // Handle AR session errors
                onSessionFailed = { exception ->
                    Log.e("VideoScreen", "AR session error", exception)
                    val errorMessage = "AR session error: ${exception.message}"
                    viewModel.processIntent(VideoScreenIntent.HandleError(errorMessage))
                },

                // Handle tracking failure
                onTrackingFailureChanged = { reason ->
                    trackingFailureReason = reason
                    if (reason != null) {
                        Log.d("VideoScreen", "Tracking failure: $reason")
                    }
                }
            )

            // UI Overlay
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top section with face detection indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    FaceDetectionIndicator(faceDetected = state.faceDetected)
                }

                // Middle section (empty)
                Spacer(modifier = Modifier.weight(1f))

                // Bottom section with controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mustache selector
                    MustacheSelector(
                        mustaches = state.availableMustaches,
                        selectedIndex = state.selectedMustacheIndex,
                        onMustacheSelected = { index ->
                            viewModel.processIntent(VideoScreenIntent.SelectMustache(index))
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Recording duration
                    if (state.isRecording) {
                        RecordingDuration(durationMs = state.recordingDuration)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Record button
                    RecordButton(
                        isRecording = state.isRecording,
                        onClick = {
                            if (state.isRecording) {
                                viewModel.processIntent(VideoScreenIntent.StopRecording)
                            } else {
                                viewModel.processIntent(VideoScreenIntent.StartRecording)
                            }
                        }
                    )
                }
            }

            // Tag input dialog
            if (state.isTagDialogVisible) {
                TagInputDialog(
                    title = "Add a tag",
                    value = state.currentTag,
                    onValueChange = { tag ->
                        viewModel.processIntent(VideoScreenIntent.UpdateTagInput(tag))
                    },
                    onConfirm = {
                        viewModel.processIntent(VideoScreenIntent.SaveRecording(state.currentTag))
                    },
                    onDismiss = {
                        viewModel.processIntent(VideoScreenIntent.DismissTagDialog)
                    }
                )
            }

            // Error dialog
            state.error?.let { error ->
                ErrorDialog(
                    errorMessage = error,
                    onDismiss = {
                        viewModel.processIntent(VideoScreenIntent.ClearError)
                    }
                )
            }

            // Loading indicator
            if (state.isLoading || isLoadingModel) {
                LoadingIndicator()
            }
        }
    }
}
