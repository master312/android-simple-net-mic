package com.sythini.simplenetmic;


import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.net.ServerSocket;


public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST_CODE = 1;

    private MicrophoneAccesser microphoneAccesser;
    private HttpMp3Streamer httpMp3Streamer;

    private Button toggleButton;
    private View statusDot;
    private TextView statusTextView;
    private TextView ipListTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Initialize views
        toggleButton = findViewById(R.id.toggleButton);
        statusDot = findViewById(R.id.statusDot);
        statusTextView = findViewById(R.id.statusTextView);
        ipListTextView = findViewById(R.id.ipListTextView);

        // Initialize components
        microphoneAccesser = new MicrophoneAccesser();
        httpMp3Streamer = new HttpMp3Streamer();

        // Set up microphone data listener
        microphoneAccesser.setOnAudioDataAvailableListener((data, length) ->
                httpMp3Streamer.streamData(data, length));

        // Set up button click listener
        toggleButton.setOnClickListener(v -> toggleStreaming());

        // Display IP addresses
        updateIpAddresses();

        // Request permissions if needed
        if (!hasRequiredPermissions()) {
            requestPermissions();
        }
    }

    private void toggleStreaming() {
        if (!microphoneAccesser.isRecording()) {
            startStreaming();
        } else {
            stopStreaming();
        }
    }

    private void startStreaming() {
        if (!hasRequiredPermissions()) {
            requestPermissions();
            return;
        }

        // Check if we're on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            new Thread(() -> {
                httpMp3Streamer.start();
                runOnUiThread(() -> {
                    microphoneAccesser.startRecording();
                    updateUI(true);
                });
            }).start();
        } else {
            httpMp3Streamer.start();
            microphoneAccesser.startRecording();
            runOnUiThread(() -> updateUI(true));
        }
    }

    private void stopStreaming() {
        microphoneAccesser.stopRecording();
        httpMp3Streamer.stop();

        updateUI(false);
    }

    private void updateUI(boolean isStreaming) {
        toggleButton.setText(isStreaming ? "Stop" : "Start");
        statusTextView.setText(isStreaming ? "Recording" : "Offline");

//        GradientDrawable dot = (GradientDrawable) statusDot.getBackground();
//        dot.setColor(isStreaming ? Color.GREEN : Color.GRAY);
    }

    private void updateIpAddresses() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ipAddress = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
        ipListTextView.setText(ipAddress + ":" + HttpMp3Streamer.PORT);
    }

    private boolean hasRequiredPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                updateUI(false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStreaming();
    }
}