package com.example.voicera.domain.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for file operations.
 * This class is responsible for saving videos, generating thumbnails, and other file-related tasks.
 */
@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val THUMBNAIL_WIDTH = 512
        private const val THUMBNAIL_HEIGHT = 384
        private const val VIDEO_DIRECTORY = "Voicera"
        private const val THUMBNAIL_DIRECTORY = "Voicera/Thumbnails"
        private const val VIDEO_MIME_TYPE = "video/mp4"
        private const val IMAGE_MIME_TYPE = "image/jpeg"
        private const val THUMBNAIL_QUALITY = 80
    }
    
    /**
     * Create a file for saving a video recording.
     * @return The URI of the created file
     */
    suspend fun createVideoFile(): Uri = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "VID_$timeStamp.mp4"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, VIDEO_MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$VIDEO_DIRECTORY")
            }
            
            val contentResolver = context.contentResolver
            return@withContext contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IllegalStateException("Failed to create video file")
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                VIDEO_DIRECTORY
            ).apply { 
                if (!exists()) mkdirs() 
            }
            
            val file = File(directory, fileName)
            return@withContext Uri.fromFile(file)
        }
    }
    
    /**
     * Generate a thumbnail for a video.
     * @param videoUri The URI of the video
     * @return The path to the generated thumbnail
     */
    suspend fun generateThumbnail(videoUri: Uri): String = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "THUMB_$timeStamp.jpg"
        
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ThumbnailUtils.createVideoThumbnail(
                File(getFilePath(videoUri).toString()),
                Size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT),
                null
            )
        } else {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            retriever.frameAtTime?.let { frame ->
                ThumbnailUtils.extractThumbnail(
                    frame,
                    THUMBNAIL_WIDTH,
                    THUMBNAIL_HEIGHT
                )
            }
        } ?: throw IllegalStateException("Failed to generate thumbnail2")


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, IMAGE_MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$THUMBNAIL_DIRECTORY")
            }
            
            val contentResolver = context.contentResolver
            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IllegalStateException("Failed to create thumbnail file")
            
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, outputStream)
            }
            
            return@withContext uri.toString()
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                THUMBNAIL_DIRECTORY
            ).apply { 
                if (!exists()) mkdirs() 
            }
            
            val file = File(directory, fileName)
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, outputStream)
            }
            
            return@withContext Uri.fromFile(file).toString()
        }
    }
    
    /**
     * Get the duration of a video.
     * @param videoUri The URI of the video
     * @return The duration in milliseconds
     */
    suspend fun getVideoDuration(videoUri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        return@withContext duration?.toLong() ?: 0L
    }
    
    /**
     * Get the size of a file.
     * @param uri The URI of the file
     * @return The size in bytes
     */
    suspend fun getFileSize(uri: Uri): Long = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            return@withContext cursor.getLong(sizeIndex)
        }
            return@withContext 0L
        }

    
    /**
     * Get the resolution of a video.
     * @param videoUri The URI of the video
     * @return The resolution as a string (e.g., "1920x1080")
     */
    suspend fun getVideoResolution(videoUri: Uri): String = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        retriever.release()
        return@withContext "${width}x${height}"
    }
    
    /**
     * Delete a file.
     * @param uri The URI of the file to delete
     * @return Whether the deletion was successful
     */
    suspend fun deleteFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentResolver = context.contentResolver
                return@withContext contentResolver.delete(uri, null, null) > 0
            } else {
                val filePath = getFilePath(uri)
                if (filePath == null){
                        return@withContext false
                }
                val file = File(filePath)

                return@withContext file.exists() && file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
    private fun getFilePath(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        } else if (uri.scheme == "content") {
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    return it.getString(columnIndex)
                }
            }
        }

        return null
    }
}
