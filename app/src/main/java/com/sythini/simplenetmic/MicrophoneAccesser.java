package com.sythini.simplenetmic;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class MicrophoneAccesser {
    private static final String TAG = "MicrophoneAccesser";
    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNELS = 1; // Mono
    public static final int BITS_PER_SAMPLE = 16;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    private AudioRecord audioRecord;
    private boolean isRecording;
    private Thread recordingThread;
    private OnAudioDataAvailableListener listener;

    public interface OnAudioDataAvailableListener {
        void onAudioDataAvailable(byte[] data, int length);
    }

    public void setOnAudioDataAvailableListener(OnAudioDataAvailableListener listener) {
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    public void startRecording() {
        if (isRecording) return;

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                return;
            }

            audioRecord.startRecording();
            isRecording = true;

            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (isRecording && !Thread.currentThread().isInterrupted()) {
                    int bytesRead = audioRecord.read(buffer, 0, BUFFER_SIZE);
                    if (bytesRead > 0 && listener != null) {
                        byte[] audioData = new byte[bytesRead];
                        System.arraycopy(buffer, 0, audioData, 0, bytesRead);
                        listener.onAudioDataAvailable(audioData, bytesRead);
                    }
                }
            });
            recordingThread.start();
            Log.d(TAG, "Recording started with buffer size: " + BUFFER_SIZE);
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage());
            stopRecording();
        }
    }

    public void stopRecording() {
        isRecording = false;
        if (recordingThread != null) {
            recordingThread.interrupt();
            recordingThread = null;
        }
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping AudioRecord: " + e.getMessage());
            }
            audioRecord.release();
            audioRecord = null;
        }
    }

    public boolean isRecording() {
        return isRecording;
    }
}