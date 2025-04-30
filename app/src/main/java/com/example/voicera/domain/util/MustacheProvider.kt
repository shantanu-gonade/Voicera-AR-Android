package com.example.voicera.domain.util

import com.example.voicera.R
import com.example.voicera.domain.model.MustacheModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider class for mustache models.
 * This class is responsible for providing the available mustache types.
 */
@Singleton
class MustacheProvider @Inject constructor() {
    
    /**
     * Get the list of available mustache models.
     * 
     * Note: The thumbnail resource IDs are currently set to 0 as placeholders.
     * Once you add the actual thumbnail images to the drawable-xxhdpi directory,
     * update the R.drawable references below with the actual resource IDs.
     * 
     * For example:
     * thumbnailResId = R.drawable.mustache_handlebar
     */
    fun getAvailableMustaches(): List<MustacheModel> {
        return listOf(
            MustacheModel(
                id = 1,
                name = "Handlebar",
                modelPath = "models/mustache_handlebar.glb",
                thumbnailResId = R.drawable.mustache_handlebar, // TODO: Replace with R.drawable.mustache_handlebar
                scale = 1.0f,
                verticalOffset = 0.5f,
                horizontalOffset = 0.6f
            ),
            MustacheModel(
                id = 2,
                name = "Walrus",
                modelPath = "models/mustache_walrus.glb",
                thumbnailResId = R.drawable.mustache_walrus, // TODO: Replace with R.drawable.mustache_walrus
                scale = 1.2f,
                verticalOffset = 0.02f,
                horizontalOffset = 0.4f
            ),
            MustacheModel(
                id = 3,
                name = "Pencil",
                modelPath = "models/mustache_pencil.glb",
                thumbnailResId = R.drawable.mustache_pencil, // TODO: Replace with R.drawable.mustache_pencil
                scale = 0.8f,
                verticalOffset = 0.01f,
                horizontalOffset = 0.3f
            ),
            MustacheModel(
                id = 4,
                name = "Horseshoe",
                modelPath = "models/mustache_horseshoe.glb",
                thumbnailResId = R.drawable.mustache_horseshoe, // TODO: Replace with R.drawable.mustache_horseshoe
                scale = 1.1f,
                verticalOffset = -0.01f,
                horizontalOffset = 0.2f
            ),
            MustacheModel(
                id = 5,
                name = "Imperial",
                modelPath = "models/mustache_imperial.glb",
                thumbnailResId = R.drawable.mustache_imperial, // TODO: Replace with R.drawable.mustache_imperial
                scale = 1.3f,
                verticalOffset = 0.03f,
                horizontalOffset = 0.2f
            )
        )
    }
}
