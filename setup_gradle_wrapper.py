#!/usr/bin/env python3
"""
Script to download the Gradle wrapper JAR file for Android Studio compatibility.
"""

import os
import urllib.request
import sys

def download_gradle_wrapper():
    """Download the Gradle wrapper JAR file."""
    
    # Create the gradle/wrapper directory if it doesn't exist
    wrapper_dir = "gradle/wrapper"
    os.makedirs(wrapper_dir, exist_ok=True)
    
    # URL for the Gradle wrapper JAR
    gradle_wrapper_url = "https://github.com/gradle/gradle/raw/v8.0.0/gradle/wrapper/gradle-wrapper.jar"
    local_path = os.path.join(wrapper_dir, "gradle-wrapper.jar")
    
    print(f"Downloading Gradle wrapper JAR from: {gradle_wrapper_url}")
    print(f"Target location: {local_path}")
    
    try:
        # Download the file
        urllib.request.urlretrieve(gradle_wrapper_url, local_path)
        print("✅ Successfully downloaded gradle-wrapper.jar")
        
        # Make gradlew executable on Unix-like systems
        if os.name != 'nt':  # Not Windows
            os.chmod("gradlew", 0o755)
            print("✅ Made gradlew executable")
            
    except Exception as e:
        print(f"❌ Error downloading gradle-wrapper.jar: {e}")
        print("\nManual download instructions:")
        print("1. Create the directory: gradle/wrapper/")
        print("2. Download from: https://github.com/gradle/gradle/raw/v8.0.0/gradle/wrapper/gradle-wrapper.jar")
        print("3. Place the file in gradle/wrapper/ directory")
        return False
    
    return True

def main():
    """Main function."""
    print("Gradle Wrapper Setup for Android Studio")
    print("=" * 40)
    
    if download_gradle_wrapper():
        print("\n🎉 Setup complete! You can now open this project in Android Studio.")
        print("\nNext steps:")
        print("1. Open Android Studio")
        print("2. Click 'Open an existing Android Studio project'")
        print("3. Navigate to this directory and select it")
        print("4. Click 'OK' and wait for the project to sync")
    else:
        print("\n⚠️  Setup incomplete. Please follow the manual instructions above.")
        sys.exit(1)

if __name__ == "__main__":
    main()
