# 📱 How to Install WhistleCounter on Your Phone

You can test the app on your Android phone without installing Android Studio! Here are several methods:

## Method 1: Build APK with Command Line (Recommended)

### Prerequisites:
- Java Development Kit (JDK) 8 or higher installed on your computer
- Android SDK (you can install just the command line tools)

### Steps:

1. **Install Java JDK** (if not already installed):
   - Download from: https://adoptium.net/
   - Or install via Homebrew: `brew install openjdk@11`

2. **Install Android SDK Command Line Tools**:
   - Download from: https://developer.android.com/studio#command-tools
   - Extract to a folder (e.g., `~/android-sdk`)
   - Add to your PATH: `export PATH=$PATH:~/android-sdk/cmdline-tools/latest/bin`

3. **Build the APK**:
   ```bash
   cd /Users/adityapvarma/Desktop/WhistleCounter
   ./build-apk.sh
   ```

4. **Install on your phone**:
   - Enable "Developer Options" on your Android phone
   - Enable "USB Debugging" in Developer Options
   - Connect phone via USB
   - Run: `adb install app/build/outputs/apk/debug/app-debug.apk`

## Method 2: Use Online Build Services

### Option A: GitHub Actions (if you push to GitHub)
1. Push the code to a GitHub repository
2. Set up GitHub Actions to build APK automatically
3. Download the built APK from the Actions tab

### Option B: Use online Android builders
- **Bitrise**: Free tier available
- **AppVeyor**: Free for open source
- **GitLab CI**: Free for public repositories

## Method 3: Use Android Studio (if you change your mind)

1. Download Android Studio from: https://developer.android.com/studio
2. Open the project folder
3. Let it sync and build
4. Connect your phone and click "Run"

## Method 4: Pre-built APK (Easiest)

I can help you create a pre-built APK file that you can directly install on your phone. Let me know if you'd like me to do this!

## Troubleshooting

### "gradlew: command not found"
- Make sure you're in the project directory
- Run: `chmod +x gradlew`

### "Java not found"
- Install Java JDK and add it to your PATH
- Or set JAVA_HOME environment variable

### "Android SDK not found"
- Install Android SDK command line tools
- Set ANDROID_HOME environment variable

### "Permission denied" on phone
- Enable "Install from Unknown Sources" in phone settings
- Or use ADB to install: `adb install app-debug.apk`

## Quick Test

Once installed, the app will:
1. Ask for microphone permission (grant it!)
2. Show a counter starting at 0
3. Tap "Start Listening" to begin detecting whistles
4. Place phone near pressure cooker and test!

## Need Help?

If you run into any issues, let me know and I can help troubleshoot or provide alternative solutions!
