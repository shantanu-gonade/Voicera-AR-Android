package com.example.voicera.di

import android.content.Context
import com.example.voicera.domain.util.FileManager
import com.example.voicera.domain.util.MustacheProvider
import com.example.voicera.domain.util.PermissionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing utility-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object UtilModule {
    
    /**
     * Provides the FileManager instance.
     */
    @Provides
    @Singleton
    fun provideFileManager(@ApplicationContext context: Context): FileManager {
        return FileManager(context)
    }
    
    /**
     * Provides the PermissionManager instance.
     */
    @Provides
    @Singleton
    fun providePermissionManager(@ApplicationContext context: Context): PermissionManager {
        return PermissionManager(context)
    }
    
    /**
     * Provides the MustacheProvider instance.
     */
    @Provides
    @Singleton
    fun provideMustacheProvider(): MustacheProvider {
        return MustacheProvider()
    }
}
