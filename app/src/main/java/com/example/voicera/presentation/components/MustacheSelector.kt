package com.example.voicera.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.voicera.domain.model.MustacheModel

/**
 * Mustache selector component.
 * This component displays a horizontal list of mustache options.
 * @param mustaches The list of available mustaches
 * @param selectedIndex The index of the currently selected mustache
 * @param onMustacheSelected Callback when a mustache is selected
 */
@Composable
fun MustacheSelector(
    mustaches: List<MustacheModel>,
    selectedIndex: Int,
    onMustacheSelected: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(mustaches) { index, mustache ->
            MustacheThumbnail(
                mustache = mustache,
                isSelected = index == selectedIndex,
                onClick = { onMustacheSelected(index) }
            )
        }
    }
}

/**
 * Mustache thumbnail component.
 * This component displays a thumbnail for a mustache option.
 * @param mustache The mustache model
 * @param isSelected Whether this mustache is currently selected
 * @param onClick Callback when this mustache is clicked
 */
@Composable
fun MustacheThumbnail(
    mustache: MustacheModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // If we have a thumbnail resource, display it
        if (mustache.thumbnailResId != 0) {
            AsyncImage(
                model = mustache.thumbnailResId,
                contentDescription = mustache.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            // Otherwise, just display the name
            Text(
                text = mustache.name,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

/**
 * Face detection indicator component.
 * This component displays an indicator for face detection status.
 * @param faceDetected Whether a face is currently detected
 */
@Composable
fun FaceDetectionIndicator(faceDetected: Boolean) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(if (faceDetected) Color.Green else Color.Red)
            .border(
                width = 1.dp,
                color = Color.White,
                shape = CircleShape
            )
    )
}

/**
 * Recording duration component.
 * This component displays the current recording duration.
 * @param durationMs The duration in milliseconds
 */
@Composable
fun RecordingDuration(durationMs: Long) {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = (durationMs / (1000 * 60 * 60))
    
    val durationText = if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
    
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = durationText,
            color = Color.White
        )
    }
}
