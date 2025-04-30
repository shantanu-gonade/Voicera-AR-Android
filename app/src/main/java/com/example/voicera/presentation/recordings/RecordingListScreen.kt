package com.example.voicera.presentation.recordings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.voicera.presentation.components.EmptyState
import com.example.voicera.presentation.components.ErrorDialog
import com.example.voicera.presentation.components.LoadingIndicator
import com.example.voicera.presentation.components.RecordingGrid
import com.example.voicera.presentation.components.TagInputDialog
import com.example.voicera.presentation.components.VoiceraTopAppBar
import com.example.voicera.presentation.components.VoiceraTopAppBarRecordList
import com.example.voicera.presentation.navigation.Screen

/**
 * Recording list screen composable.
 * This screen displays a grid of recordings and provides options to play, edit, and delete recordings.
 * @param navController The navigation controller
 * @param viewModel The view model for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingListScreen(
    navController: NavController,
    viewModel: RecordingListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            VoiceraTopAppBarRecordList(
                title = "Recordings",
                showBackButton = true,
                onBackClick = {
                    navController.navigateUp()
                },
                onClick = {
                        viewModel.processIntent(RecordingListIntent.ActivateSearch)
                    }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate(Screen.VideoScreen.route) {
                        popUpTo(Screen.VideoScreen.route) {
                            inclusive = true
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Recording"
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            if (state.isSearchActive) {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = { query ->
                        viewModel.processIntent(RecordingListIntent.UpdateSearchQuery(query))
                    },
                    onSearch = { query ->
                        viewModel.processIntent(RecordingListIntent.UpdateSearchQuery(query))
                    },
                    active = true,
                    onActiveChange = { active ->
                        if (!active) {
                            viewModel.processIntent(RecordingListIntent.DeactivateSearch)
                        }
                    },
                    placeholder = {
                        Text(text = "Search by tag")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Search results
                    if (state.recordings.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.VideoLibrary,
                            message = "No recordings found"
                        )
                    } else {
                        RecordingGrid(
                            recordings = state.recordings,
                            onRecordingClick = { recording ->
                                playRecording(context, recording.videoPath)
                            },
                            onEditTagClick = { recording ->
                                viewModel.processIntent(
                                    RecordingListIntent.ShowTagEditDialog(
                                        recordingId = recording.id,
                                        currentTag = recording.tag
                                    )
                                )
                            },
                            onDeleteClick = { recording ->
                                viewModel.processIntent(
                                    RecordingListIntent.DeleteRecording(recording.id)
                                )
                            }
                        )
                    }
                }
            } else {
                // Regular content
                if (state.recordings.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.VideoLibrary,
                        message = "No recordings yet. Tap the + button to create one."
                    )
                } else {
                    RecordingGrid(
                        recordings = state.recordings,
                        onRecordingClick = { recording ->
                            playRecording(context, recording.videoPath)
                        },
                        onEditTagClick = { recording ->
                            viewModel.processIntent(
                                RecordingListIntent.ShowTagEditDialog(
                                    recordingId = recording.id,
                                    currentTag = recording.tag
                                )
                            )
                        },
                        onDeleteClick = { recording ->
                            viewModel.processIntent(
                                RecordingListIntent.DeleteRecording(recording.id)
                            )
                        }
                    )
                }
            }
            
            // Tag edit dialog
            if (state.isTagEditDialogVisible) {
                TagInputDialog(
                    title = "Edit Tag",
                    value = state.currentEditTag,
                    onValueChange = { tag ->
                        viewModel.processIntent(RecordingListIntent.UpdateTagEditInput(tag))
                    },
                    onConfirm = {
                        viewModel.processIntent(RecordingListIntent.SaveEditedTag)
                    },
                    onDismiss = {
                        viewModel.processIntent(RecordingListIntent.DismissTagEditDialog)
                    }
                )
            }
            
            // Error dialog
            state.error?.let { error ->
                ErrorDialog(
                    errorMessage = error,
                    onDismiss = {
                        viewModel.processIntent(RecordingListIntent.ClearError)
                    }
                )
            }
            
            // Loading indicator
            if (state.isLoading) {
                LoadingIndicator()
            }
        }
    }
}

/**
 * Play a recording using the system's video player.
 * @param context The context
 * @param videoPath The path to the video file
 */
private fun playRecording(context: android.content.Context, videoPath: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(videoPath), "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}
