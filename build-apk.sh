#!/bin/bash

# Build APK script for WhistleCounter app
# This script builds the APK without requiring Android Studio

echo "🔨 Building WhistleCounter APK..."

# Check if we're in the right directory
if [ ! -f "settings.gradle" ]; then
    echo "❌ Error: Please run this script from the WhistleCounter project root directory"
    exit 1
fi

# Make gradlew executable
chmod +x gradlew

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./gradlew clean

# Build debug APK
echo "📱 Building debug APK..."
./gradlew assembleDebug

# Check if build was successful
if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo "📦 APK location: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "📱 To install on your phone:"
    echo "1. Enable 'Developer Options' on your Android phone"
    echo "2. Enable 'USB Debugging' in Developer Options"
    echo "3. Connect your phone via USB"
    echo "4. Run: adb install app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "Or transfer the APK file to your phone and install it manually"
else
    echo "❌ Build failed. Please check the error messages above."
    exit 1
fi
