# Voicera App

Voicera is an Android application that allows users to capture videos with AR mustache overlays using ARCore and SceneView. The app features real-time face tracking, multiple mustache styles, and a recording management system.

## Features

- **AR Mustache Overlay**: Apply 3D mustache models to faces in real-time using ARCore
- **Multiple Mustache Styles**: Choose from 5 different mustache styles
- **Video Recording**: Capture videos with the AR mustache overlay
- **Recording Management**: View, play, tag, and delete recordings
- **Search Functionality**: Search recordings by tag

## Architecture

The app is built using the MVI (Model-View-Intent) architecture pattern, which provides a unidirectional data flow and clear separation of concerns. The main components are:

- **Model**: Represents the state of the app
- **View**: Displays the UI and sends user actions as intents
- **Intent**: Represents user actions that trigger state changes
- **ViewModel**: Processes intents and updates the state

## Technologies Used

- **Kotlin**: Programming language
- **Jetpack Compose**: UI toolkit
- **ARCore**: Augmented reality framework
- **SceneView**: 3D rendering library
- **CameraX**: Camera API
- **Room**: Database for storing recording metadata
- **Hilt**: Dependency injection
- **Coroutines & Flow**: Asynchronous programming
- **Navigation Component**: Screen navigation

## Project Structure

- **data**: Contains the data layer (database, repositories)
- **di**: Contains dependency injection modules
- **domain**: Contains the domain layer (models, use cases)
- **presentation**: Contains the UI layer (screens, view models)
- **util**: Contains utility classes

## Setup Instructions

### Prerequisites

- Android Studio Arctic Fox or later
- Android device with ARCore support (or emulator with ARCore support)
- Android SDK 24 or higher

### Getting Started

1. Clone the repository
2. Open the project in Android Studio
3. Add 3D mustache models to the `app/src/main/assets/models` directory (see README in that directory)
4. Add mustache thumbnail images to the `app/src/main/res/drawable-xxhdpi` directory (see README in that directory)
5. Update the resource IDs in `MustacheProvider.kt`
6. Build and run the app on a device with ARCore support

## Usage

1. Grant the required permissions (camera, microphone, storage)
2. Point the camera at a face to see the AR mustache overlay
3. Select different mustache styles from the bottom carousel
4. Tap the record button to start/stop recording
5. Add a tag to the recording when prompted
6. View and manage recordings from the recordings list screen

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgements

- Google ARCore for the augmented reality framework
- SceneView for the 3D rendering library
- Android Jetpack for the modern Android development components
