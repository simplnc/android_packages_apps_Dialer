# Android Studio Setup Guide

This project has been configured to work with Android Studio. Follow these steps to open and run the project:

## Prerequisites

- Android Studio (latest version recommended)
- Java Development Kit (JDK) 8 or higher
- Android SDK (API level 24 or higher)

## Setup Steps

### 1. Download Gradle Wrapper JAR

Since the `gradle-wrapper.jar` file is not included in the repository, you need to download it:

1. Create the directory: `gradle/wrapper/`
2. Download the Gradle wrapper JAR from: https://github.com/gradle/gradle/raw/v8.0.0/gradle/wrapper/gradle-wrapper.jar
3. Place it in the `gradle/wrapper/` directory

Alternatively, you can run this command in the project root:
```bash
# On Windows
gradlew.bat wrapper

# On Unix/Linux/macOS
./gradlew wrapper
```

### 2. Open Project in Android Studio

1. Launch Android Studio
2. Click "Open an existing Android Studio project"
3. Navigate to this project directory and select it
4. Click "OK"

### 3. Sync Project

1. Android Studio will automatically detect the Gradle files
2. Click "Sync Now" when prompted
3. Wait for the Gradle sync to complete

### 4. Configure SDK

1. Go to File → Project Structure
2. Under "SDK Location", ensure you have:
   - Android SDK location set correctly
   - JDK location set correctly
3. Click "OK"

### 5. Build and Run

1. Click the "Build" button (hammer icon) or press Ctrl+F9 (Cmd+F9 on macOS)
2. If successful, you can run the app on an emulator or device

## Project Structure

The project has been configured with the following structure:

- **Root level**: Contains `build.gradle`, `settings.gradle`, and `gradle.properties`
- **App module**: Located in the `app/` directory with its own `build.gradle`
- **Source code**: Java source files are in `java/` directory
- **Resources**: Layout and resource files are in their respective directories
- **Assets**: Quantum icons and other assets in `assets/` directory

## Build Configuration

The project is configured with:
- **Compile SDK**: 34 (Android 14)
- **Target SDK**: 34
- **Minimum SDK**: 24 (Android 7.0)
- **Java version**: 1.8
- **Gradle version**: 8.0

## Dependencies

The project includes these main dependencies:
- AndroidX AppCompat
- Material Design Components
- ConstraintLayout
- Preference library
- Media library for call recording

## Troubleshooting

### Common Issues

1. **Gradle sync fails**: Make sure you have the correct JDK version installed
2. **SDK not found**: Verify your Android SDK installation in Android Studio settings
3. **Build errors**: Check that all required dependencies are available

### Sync Issues

If you encounter sync issues:
1. File → Invalidate Caches and Restart
2. Clean and rebuild the project
3. Check the Gradle console for specific error messages

## Notes

- This project was originally an AOSP-style project using `Android.bp`
- It has been converted to use Gradle for Android Studio compatibility
- Some AOSP-specific features may require additional configuration
- The project maintains its original structure while adding Gradle build support

## Support

For issues specific to this project setup, check the project documentation or create an issue in the project repository.
