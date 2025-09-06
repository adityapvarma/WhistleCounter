# Pressure Cooker Whistle Counter

A simple Android app that automatically counts pressure cooker whistles using the device's microphone.

## Features

- **Automatic Whistle Detection**: Uses audio frequency analysis to detect pressure cooker whistles
- **Real-time Counter**: Displays the current whistle count with a large, easy-to-read number
- **Start/Stop Listening**: Toggle audio recording on and off
- **Reset Counter**: Reset the whistle count to zero
- **Permission Handling**: Requests microphone permission when needed

## How It Works

The app uses the device's microphone to continuously monitor audio input. It analyzes the frequency content of the audio to detect the characteristic high-frequency sounds of pressure cooker whistles (typically in the 1-4 kHz range).

## Usage

1. **Grant Permissions**: When you first open the app, it will request microphone permission. Grant this permission to enable whistle detection.

2. **Start Listening**: Tap the "Start Listening" button to begin monitoring for whistles.

3. **Place Near Pressure Cooker**: Position your phone near the pressure cooker so it can clearly hear the whistles.

4. **Automatic Counting**: The app will automatically detect and count each whistle as it occurs.

5. **Stop/Reset**: Use the "Stop Listening" button to pause detection, or the "Reset" button to reset the counter to zero.

## Technical Details

- **Minimum Android Version**: API 21 (Android 5.0)
- **Target Android Version**: API 34 (Android 14)
- **Audio Processing**: Real-time frequency analysis using FFT
- **Permissions Required**: 
  - `RECORD_AUDIO` - To capture audio from microphone
  - `MODIFY_AUDIO_SETTINGS` - To configure audio recording

## Building the App

1. Open the project in Android Studio
2. Sync the project with Gradle files
3. Build and run on an Android device or emulator

## Notes

- The app works best in quiet environments where the pressure cooker whistle is clearly audible
- Make sure to place the phone close enough to hear the whistle but far enough to avoid damage from steam
- The whistle detection algorithm is tuned for typical pressure cooker whistle frequencies
- The app will automatically pause when the device goes to sleep to preserve battery

## Troubleshooting

- **No whistles detected**: Ensure the microphone permission is granted and the phone is close enough to the pressure cooker
- **False detections**: The app may occasionally detect other high-frequency sounds as whistles
- **App crashes**: Make sure you're running on Android 5.0 or higher

Enjoy cooking with your new whistle counter! 🍲
