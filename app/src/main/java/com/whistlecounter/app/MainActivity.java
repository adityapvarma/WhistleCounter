package com.whistlecounter.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1002;
    private static final int SAMPLE_RATE = 22050; // Reduced for better battery life
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private boolean isListening = false;
    private int whistleCount = 0;
    private Thread recordingThread;
    
    // Whistle detection variables
    private long lastWhistleTime = 0;
    private static final long WHISTLE_COOLDOWN_MS = 3000; // 3 seconds between detections
    private static final double MIN_VOLUME_THRESHOLD = 0.01; // Minimum volume to consider (lowered for sensitivity)
    private static final int SUSTAINED_SAMPLES_REQUIRED = 8; // Samples needed for sustained sound (reduced for responsiveness)
    private static final int WHISTLE_END_SAMPLES = 10; // Samples of silence to end whistle (reduced for faster reset)
    private static final long WHISTLE_MAX_DURATION_MS = 30000; // Maximum 30 seconds per whistle
    private int sustainedHighFreqSamples = 0;
    private int silenceSamples = 0;
    private int interruptionSamples = 0;
    private boolean isWhistleInProgress = false;
    private long whistleStartTime = 0;
    private long lastStatusUpdate = 0;
    private static final long STATUS_UPDATE_INTERVAL = 500; // 500ms between status updates
    private static final int MAX_INTERRUPTION_SAMPLES = 3; // Allow up to 3 samples of interruption
    
    private TextView statusText;
    private TextView counterValue;
    private Button startStopButton;
    private Button resetButton;
    private Button backgroundToggleButton;
    private boolean isBackgroundMode = false;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupClickListeners();
        checkPermissions();
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.statusText);
        counterValue = findViewById(R.id.counterValue);
        startStopButton = findViewById(R.id.startStopButton);
        resetButton = findViewById(R.id.resetButton);
        backgroundToggleButton = findViewById(R.id.backgroundToggleButton);
    }
    
    private void setupClickListeners() {
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isListening) {
                    stopListening();
                } else {
                    startListening();
                }
            }
        });
        
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetCounter();
            }
        });
        
        backgroundToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleBackgroundMode();
            }
        });
    }
    
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.RECORD_AUDIO}, 
                PERMISSION_REQUEST_CODE);
        }
        
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted! You can now start listening.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(this, "Failed to initialize audio recording", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Reset detection variables
            sustainedHighFreqSamples = 0;
            silenceSamples = 0;
            interruptionSamples = 0;
            isWhistleInProgress = false;
            lastWhistleTime = 0;
            whistleStartTime = 0;
            
            isListening = true;
            isRecording = true;
            
            audioRecord.startRecording();
            
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    processAudioData();
                }
            });
            recordingThread.start();
            
            updateUI();
            
        } catch (Exception e) {
            Toast.makeText(this, "Error starting audio recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopListening() {
        isListening = false;
        isRecording = false;
        
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            } catch (Exception e) {
                // Ignore errors during cleanup
            }
        }
        
        if (recordingThread != null) {
            try {
                recordingThread.join(1000); // Wait up to 1 second for thread to finish
            } catch (InterruptedException e) {
                // Ignore
            }
            recordingThread = null;
        }
        
        // Reset whistle state when stopping
        isWhistleInProgress = false;
        sustainedHighFreqSamples = 0;
        silenceSamples = 0;
        interruptionSamples = 0;
        whistleStartTime = 0;
        
        updateUI();
    }
    
    private void processAudioData() {
        short[] buffer = new short[BUFFER_SIZE];
        double[] fftBuffer = new double[BUFFER_SIZE];
        
        while (isRecording && audioRecord != null) {
            int bytesRead = audioRecord.read(buffer, 0, BUFFER_SIZE);
            
            if (bytesRead > 0) {
                // Convert to double for FFT processing
                for (int i = 0; i < bytesRead; i++) {
                    fftBuffer[i] = buffer[i] / 32768.0; // Normalize to [-1, 1]
                }
                
                // Detect whistle based on frequency analysis
                if (detectWhistle(fftBuffer, bytesRead)) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            incrementCounter();
                        }
                    });
                } else if (isWhistleInProgress) {
                    // Update status to show whistle is in progress
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Whistle in progress... Count: " + whistleCount);
                        }
                    });
                }
            }
        }
    }
    
    private boolean detectWhistle(double[] audioData, int length) {
        // Improved whistle detection for pressure cooker whistles
        // Handles both short and long whistles as single events
        
        long currentTime = System.currentTimeMillis();
        
        // Check for maximum whistle duration timeout (only if we have a valid start time)
        if (isWhistleInProgress && whistleStartTime > 0 && currentTime - whistleStartTime > WHISTLE_MAX_DURATION_MS) {
            isWhistleInProgress = false;
            silenceSamples = 0;
            sustainedHighFreqSamples = 0;
            whistleStartTime = 0;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    statusText.setText("Whistle timeout - listening for new whistles...");
                }
            });
        }
        
        // Only check cooldown if we're not already tracking a whistle
        if (!isWhistleInProgress && currentTime - lastWhistleTime < WHISTLE_COOLDOWN_MS) {
            return false;
        }
        
        // Calculate total energy first
        double totalEnergy = 0;
        for (int i = 0; i < length; i++) {
            double sample = audioData[i];
            totalEnergy += sample * sample;
        }
        
        // Check if loud enough first
        boolean isLoudEnough = totalEnergy >= MIN_VOLUME_THRESHOLD;
        
        if (!isLoudEnough) {
            // Not loud enough, reset counters
            sustainedHighFreqSamples = 0;
            silenceSamples++;
            interruptionSamples++;
            
            // Only reset sustained samples if interruption is long enough
            if (interruptionSamples >= MAX_INTERRUPTION_SAMPLES) {
                sustainedHighFreqSamples = 0;
            }
            return false;
        }
        
        // Simple frequency analysis using zero-crossing rate and energy distribution
        int zeroCrossings = 0;
        double highFreqEnergy = 0;
        double midFreqEnergy = 0;
        double lowFreqEnergy = 0;
        
        for (int i = 0; i < length - 1; i++) {
            double sample = audioData[i];
            double nextSample = audioData[i + 1];
            double energy = sample * sample;
            
            // Count zero crossings (indicates high frequency content)
            if ((sample > 0 && nextSample < 0) || (sample < 0 && nextSample > 0)) {
                zeroCrossings++;
            }
            
            // Simple frequency band analysis based on sample position
            if (i < length / 8) {
                lowFreqEnergy += energy;      // 0-2.75 kHz (low frequency)
            } else if (i < length / 4) {
                midFreqEnergy += energy;      // 2.75-5.5 kHz (mid frequency)
            } else if (i < length / 2) {
                highFreqEnergy += energy;     // 5.5-11 kHz (high frequency - whistle range)
            }
        }
        
        // Calculate frequency ratios
        double highFreqRatio = totalEnergy > 0 ? highFreqEnergy / totalEnergy : 0;
        double midFreqRatio = totalEnergy > 0 ? midFreqEnergy / totalEnergy : 0;
        double lowFreqRatio = totalEnergy > 0 ? lowFreqEnergy / totalEnergy : 0;
        
        // Calculate zero crossing rate (higher = more high frequency content)
        double zeroCrossingRate = (double) zeroCrossings / length;
        
        // Check if this looks like a whistle
        boolean hasHighFreq = highFreqRatio > 0.3;  // At least 30% high frequency (lowered threshold)
        boolean notTooMuchLowFreq = lowFreqRatio < 0.4;  // Less than 40% low frequency (relaxed)
        boolean hasMidFreq = midFreqRatio > 0.15;  // Some mid frequency content (lowered threshold)
        boolean hasHighZeroCrossing = zeroCrossingRate > 0.1;  // High zero crossing rate indicates high frequency
        
        boolean isWhistleSound = hasHighFreq && notTooMuchLowFreq && hasMidFreq && hasHighZeroCrossing && isLoudEnough;
        
        // Enhanced debug logging
        if (totalEnergy > 0.001) { // Lower threshold to see more data
            Log.d("WhistleDetection", String.format("Energy: %.4f, High: %.2f, Mid: %.2f, Low: %.2f, ZC: %.3f, Loud: %b, Whistle: %b, Sustained: %d", 
                totalEnergy, highFreqRatio, midFreqRatio, lowFreqRatio, zeroCrossingRate, isLoudEnough, isWhistleSound, sustainedHighFreqSamples));
        }
        
        // Update status with real-time debug info
        if (totalEnergy > 0.01 && currentTime - lastStatusUpdate > STATUS_UPDATE_INTERVAL) {
            lastStatusUpdate = currentTime;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    statusText.setText(String.format("Energy: %.3f, High: %.1f%%, ZC: %.2f, Sustained: %d", 
                        totalEnergy, highFreqRatio * 100, zeroCrossingRate, sustainedHighFreqSamples));
                }
            });
        }
        
        if (isWhistleSound) {
            // We're hearing whistle-like sound
            sustainedHighFreqSamples++;
            silenceSamples = 0; // Reset silence counter
            interruptionSamples = 0; // Reset interruption counter
            
            // Update status to show we're detecting whistle-like sound (throttled)
            if (sustainedHighFreqSamples > 5 && currentTime - lastStatusUpdate > STATUS_UPDATE_INTERVAL) {
                lastStatusUpdate = currentTime;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText("Detecting whistle... (" + sustainedHighFreqSamples + "/" + SUSTAINED_SAMPLES_REQUIRED + ")");
                    }
                });
            }
            
            // If we're not already tracking a whistle, start tracking
            if (!isWhistleInProgress && sustainedHighFreqSamples >= SUSTAINED_SAMPLES_REQUIRED) {
                isWhistleInProgress = true;
                whistleStartTime = currentTime;
                lastWhistleTime = currentTime; // Update cooldown
                return true; // This is the start of a new whistle
            }
        } else {
            // We're not hearing whistle-like sound
            silenceSamples++;
            interruptionSamples++;
            
            // Only reset sustained samples if interruption is long enough
            if (interruptionSamples >= MAX_INTERRUPTION_SAMPLES) {
                sustainedHighFreqSamples = 0;
            }
            
            // Update status to show we're listening
            if (silenceSamples == 1 && !isWhistleInProgress) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText("Listening for whistles...");
                    }
                });
            }
            
            // If we were tracking a whistle and now have enough silence, end the whistle
            if (isWhistleInProgress && silenceSamples >= WHISTLE_END_SAMPLES) {
                isWhistleInProgress = false;
                silenceSamples = 0;
                sustainedHighFreqSamples = 0;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText("Whistle ended - listening for new whistles...");
                    }
                });
            }
        }
        
        return false; // No new whistle detected
    }
    
    private void incrementCounter() {
        whistleCount++;
        counterValue.setText(String.valueOf(whistleCount));
        
        // Update status to show detection
        statusText.setText("Whistle detected! Count: " + whistleCount);
        
        // Provide haptic feedback
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startStopButton.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        }
        
        // Reset status after 3 seconds
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isListening) {
                    statusText.setText("Listening for whistles...");
                }
            }
        }, 3000);
    }
    
    private void resetCounter() {
        whistleCount = 0;
        counterValue.setText("0");
        
        // Reset whistle state
        isWhistleInProgress = false;
        sustainedHighFreqSamples = 0;
        silenceSamples = 0;
        interruptionSamples = 0;
        whistleStartTime = 0;
        
        // Also reset the service counter if running
        if (isBackgroundMode) {
            Intent serviceIntent = new Intent(this, WhistleDetectionService.class);
            serviceIntent.putExtra("action", "reset");
            startService(serviceIntent);
        }
        
        statusText.setText("Ready to listen for whistles");
    }
    
    private void toggleBackgroundMode() {
        isBackgroundMode = !isBackgroundMode;
        
        if (isBackgroundMode) {
            // Start background service
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                    == PackageManager.PERMISSION_GRANTED) {
                Intent serviceIntent = new Intent(this, WhistleDetectionService.class);
                serviceIntent.putExtra("action", "start");
                startForegroundService(serviceIntent);
                
                backgroundToggleButton.setText("Stop Background");
                statusText.setText("Background detection active - check notification bar");
            } else {
                Toast.makeText(this, "Microphone permission required for background detection", Toast.LENGTH_SHORT).show();
                isBackgroundMode = false;
            }
        } else {
            // Stop background service
            Intent serviceIntent = new Intent(this, WhistleDetectionService.class);
            serviceIntent.putExtra("action", "stop");
            startService(serviceIntent);
            
            backgroundToggleButton.setText("Start Background");
            statusText.setText("Ready to listen for whistles");
        }
    }
    
    private void updateUI() {
        if (isListening) {
            statusText.setText("Listening for whistles...");
            startStopButton.setText(getString(R.string.stop_listening));
            startStopButton.setBackgroundColor(getResources().getColor(R.color.secondary_color));
        } else {
            statusText.setText(getString(R.string.listening_status));
            startStopButton.setText(getString(R.string.start_listening));
            startStopButton.setBackgroundColor(getResources().getColor(R.color.primary_color));
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListening();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (isListening) {
            stopListening();
        }
    }
}

