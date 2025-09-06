package com.whistlecounter.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

public class WhistleDetectionService extends Service {
    
    private static final String TAG = "WhistleDetectionService";
    private static final String CHANNEL_ID = "whistle_detection_channel";
    private static final int NOTIFICATION_ID = 1;
    
    // Audio recording constants
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    // Whistle detection constants
    private static final long WHISTLE_COOLDOWN_MS = 3000;
    private static final double MIN_VOLUME_THRESHOLD = 0.05;
    private static final int SUSTAINED_SAMPLES_REQUIRED = 15;
    private static final int WHISTLE_END_SAMPLES = 20;
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private PowerManager.WakeLock wakeLock;
    
    // Detection state
    private int whistleCount = 0;
    private long lastWhistleTime = 0;
    private int sustainedHighFreqSamples = 0;
    private int silenceSamples = 0;
    private boolean isWhistleInProgress = false;
    
    private NotificationManager notificationManager;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        
        // Acquire wake lock to keep CPU running
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WhistleCounter::WhistleDetection");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getStringExtra("action");
            if ("start".equals(action)) {
                startDetection();
            } else if ("stop".equals(action)) {
                stopDetection();
            } else if ("reset".equals(action)) {
                resetCounter();
            }
        }
        
        return START_STICKY; // Restart if killed
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Whistle Detection",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows whistle count in notification bar");
            channel.setShowBadge(true);
            
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private void startDetection() {
        if (isRecording) return;
        
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Microphone permission not granted");
            return;
        }
        
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize audio recording");
                return;
            }
            
            // Reset detection state
            sustainedHighFreqSamples = 0;
            silenceSamples = 0;
            isWhistleInProgress = false;
            lastWhistleTime = 0;
            
            isRecording = true;
            audioRecord.startRecording();
            
            // Acquire wake lock
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
            }
            
            recordingThread = new Thread(this::processAudioData);
            recordingThread.start();
            
            startForeground(NOTIFICATION_ID, createNotification());
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio recording: " + e.getMessage());
        }
    }
    
    private void stopDetection() {
        isRecording = false;
        
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio recording: " + e.getMessage());
            }
        }
        
        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error joining recording thread: " + e.getMessage());
            }
            recordingThread = null;
        }
        
        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        stopForeground(true);
        stopSelf();
    }
    
    private void resetCounter() {
        whistleCount = 0;
        updateNotification();
    }
    
    private void processAudioData() {
        short[] buffer = new short[BUFFER_SIZE];
        double[] fftBuffer = new double[BUFFER_SIZE];
        
        while (isRecording && audioRecord != null) {
            int bytesRead = audioRecord.read(buffer, 0, BUFFER_SIZE);
            
            if (bytesRead > 0) {
                // Convert to double for processing
                for (int i = 0; i < bytesRead; i++) {
                    fftBuffer[i] = buffer[i] / 32768.0;
                }
                
                if (detectWhistle(fftBuffer, bytesRead)) {
                    mainHandler.post(this::incrementCounter);
                }
            }
        }
    }
    
    private boolean detectWhistle(double[] audioData, int length) {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastWhistleTime < WHISTLE_COOLDOWN_MS) {
            return false;
        }
        
        double totalEnergy = 0;
        double highFreqEnergy = 0;
        double midFreqEnergy = 0;
        double lowFreqEnergy = 0;
        
        // Calculate energy in different frequency bands
        for (int i = 0; i < length; i++) {
            double sample = audioData[i];
            double energy = sample * sample;
            totalEnergy += energy;
            
            if (i < length / 4) {
                lowFreqEnergy += energy;
            } else if (i < length / 2) {
                midFreqEnergy += energy;
            } else {
                highFreqEnergy += energy;
            }
        }
        
        double highFreqRatio = totalEnergy > 0 ? highFreqEnergy / totalEnergy : 0;
        double midFreqRatio = totalEnergy > 0 ? midFreqEnergy / totalEnergy : 0;
        double lowFreqRatio = totalEnergy > 0 ? lowFreqEnergy / totalEnergy : 0;
        
        boolean hasHighFreq = highFreqRatio > 0.4;
        boolean notTooMuchLowFreq = lowFreqRatio < 0.3;
        boolean hasMidFreq = midFreqRatio > 0.2;
        boolean isLoudEnough = totalEnergy >= MIN_VOLUME_THRESHOLD;
        
        boolean isWhistleSound = hasHighFreq && notTooMuchLowFreq && hasMidFreq && isLoudEnough;
        
        if (isWhistleSound) {
            sustainedHighFreqSamples++;
            silenceSamples = 0;
            
            if (!isWhistleInProgress && sustainedHighFreqSamples >= SUSTAINED_SAMPLES_REQUIRED) {
                isWhistleInProgress = true;
                sustainedHighFreqSamples = 0;
                lastWhistleTime = currentTime;
                return true;
            }
        } else {
            sustainedHighFreqSamples = 0;
            silenceSamples++;
            
            if (isWhistleInProgress && silenceSamples >= WHISTLE_END_SAMPLES) {
                isWhistleInProgress = false;
                silenceSamples = 0;
            }
        }
        
        return false;
    }
    
    private void incrementCounter() {
        whistleCount++;
        updateNotification();
    }
    
    private void updateNotification() {
        Notification notification = createNotification();
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create action buttons
        Intent stopIntent = new Intent(this, WhistleDetectionService.class);
        stopIntent.putExtra("action", "stop");
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Intent resetIntent = new Intent(this, WhistleDetectionService.class);
        resetIntent.putExtra("action", "reset");
        PendingIntent resetPendingIntent = PendingIntent.getService(
            this, 2, resetIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Whistle Counter")
            .setContentText("Whistles detected: " + whistleCount)
            .setSmallIcon(R.drawable.ic_notification) // We'll need to add this
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .addAction(R.drawable.ic_reset, "Reset", resetPendingIntent)
            .build();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopDetection();
    }
}
