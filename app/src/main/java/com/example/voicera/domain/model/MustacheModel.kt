package com.example.voicera.domain.model

/**
 * Model class representing a mustache type.
 * This is used for displaying and selecting mustaches in the UI.
 */
data class MustacheModel(
    /**
     * Unique identifier for the mustache
     */
    val id: Int,
    
    /**
     * Display name of the mustache
     */
    val name: String,
    
    /**
     * Path to the 3D model file
     */
    val modelPath: String,
    
    /**
     * Resource ID for the thumbnail image
     */
    val thumbnailResId: Int,
    
    /**
     * Scale factor for the mustache model
     */
    val scale: Float = 1.0f,
    
    /**
     * Vertical offset for positioning the mustache
     */
    val verticalOffset: Float = 0.0f,
    
    /**
     * Horizontal offset for positioning the mustache
     */
    val horizontalOffset: Float = 0.0f
)
