package com.example.voicera.di

import android.content.Context
import com.example.voicera.domain.ar.ARSessionManager
import com.example.voicera.domain.ar.FaceArManager
import com.example.voicera.domain.camera.Camera2Manager
import com.example.voicera.domain.camera.CameraManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing AR and camera-related singleton dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object ArCoreModule {
    
    /**
     * Provides the ARSessionManager instance.
     */
    @Provides
    @Singleton
    fun provideARSessionManager(
        @ApplicationContext context: Context
    ): ARSessionManager {
        return ARSessionManager(context)
    }
    
    /**
     * Provides the Camera2Manager instance.
     */
    @Provides
    @Singleton
    fun provideCamera2Manager(@ApplicationContext context: Context): Camera2Manager {
        return Camera2Manager(context)
    }
    
    /**
     * Provides the legacy CameraManager instance.
     * This is kept for backward compatibility during migration.
     * Will be removed once migration is complete.
     */
    @Provides
    @Singleton
    fun provideCameraManager(@ApplicationContext context: Context): CameraManager {
        return CameraManager(context)
    }
}

/**
 * Hilt module for providing AR feature-related dependencies scoped to ViewModels.
 */
@Module
@InstallIn(ViewModelComponent::class)
object ArFeatureModule {
    
    /**
     * Provides the FaceArManager instance scoped to ViewModels.
     */
    @Provides
    @ViewModelScoped
    fun provideFaceArManager(
        arSessionManager: ARSessionManager,
        camera2Manager: Camera2Manager
    ): FaceArManager {
        return FaceArManager(arSessionManager, camera2Manager)
    }
}
