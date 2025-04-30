package com.example.voicera

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Voicera app.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class VoiceraApplication : Application()
