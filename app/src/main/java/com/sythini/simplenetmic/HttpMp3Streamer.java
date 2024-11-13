// HttpMp3Streamer.java
package com.sythini.simplenetmic;

import android.util.Log;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpMp3Streamer {
    private static final String TAG = "HttpMp3Streamer";
    public static final int PORT = 4040;
    private ServerSocket serverSocket;
    private boolean isRunning;
    private final List<OutputStream> clients = new ArrayList<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private boolean headersSent = false;

    public void start() {
        if (isRunning) return;

        isRunning = true;
        headersSent = false;

        executorService.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.i(TAG, "Server started on port " + PORT);

                while (isRunning) {
                    Socket client = serverSocket.accept();
                    handleNewClient(client);
                }
            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Server error: " + e.getMessage());
                }
            }
        });
    }

    private void handleNewClient(Socket client) {
        try {
            OutputStream output = client.getOutputStream();

            // Send HTTP headers
            String headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: audio/wav\r\n" +
                    "Connection: close\r\n" +
                    "Cache-Control: no-cache\r\n\r\n";
            output.write(headers.getBytes());

            // Send WAV header
            writeWavHeader(output);

            synchronized (clients) {
                clients.add(output);
                Log.d(TAG, "New client connected. Total clients: " + clients.size());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error handling client: " + e.getMessage());
            try {
                client.close();
            } catch (IOException ex) {
                Log.e(TAG, "Error closing client: " + ex.getMessage());
            }
        }
    }

    private void writeWavHeader(OutputStream output) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(44);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // RIFF header
        buffer.put("RIFF".getBytes());
        buffer.putInt(Integer.MAX_VALUE); // File size unknown, set to max
        buffer.put("WAVE".getBytes());

        // Format chunk
        buffer.put("fmt ".getBytes());
        buffer.putInt(16); // Chunk size
        buffer.putShort((short) 1); // Audio format (1 = PCM)
        buffer.putShort((short) MicrophoneAccesser.CHANNELS); // Channels
        buffer.putInt(MicrophoneAccesser.SAMPLE_RATE); // Sample rate
        int byteRate = MicrophoneAccesser.SAMPLE_RATE * MicrophoneAccesser.CHANNELS * MicrophoneAccesser.BITS_PER_SAMPLE / 8;
        buffer.putInt(byteRate); // Byte rate
        buffer.putShort((short) (MicrophoneAccesser.CHANNELS * MicrophoneAccesser.BITS_PER_SAMPLE / 8)); // Block align
        buffer.putShort((short) MicrophoneAccesser.BITS_PER_SAMPLE); // Bits per sample

        // Data chunk
        buffer.put("data".getBytes());
        buffer.putInt(Integer.MAX_VALUE); // Data size unknown, set to max

        output.write(buffer.array());
        output.flush();
    }

    public void streamData(byte[] data, int length) {
        if (!isRunning) return;

        synchronized (clients) {
            List<OutputStream> deadClients = new ArrayList<>();

            for (OutputStream client : clients) {
                try {
                    client.write(data, 0, length);
                    client.flush();
                } catch (IOException e) {
                    deadClients.add(client);
                    try {
                        client.close();
                    } catch (IOException ex) {
                        Log.e(TAG, "Error closing dead client: " + ex.getMessage());
                    }
                }
            }

            if (!deadClients.isEmpty()) {
                clients.removeAll(deadClients);
                Log.d(TAG, "Removed " + deadClients.size() + " dead clients. Remaining: " + clients.size());
            }
        }
    }

    public void stop() {
        isRunning = false;

        synchronized (clients) {
            for (OutputStream client : clients) {
                try {
                    client.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing client: " + e.getMessage());
                }
            }
            clients.clear();
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server: " + e.getMessage());
            }
        }

        executorService.shutdown();
        Log.i(TAG, "Server stopped");
    }

    public boolean isRunning() {
        return isRunning;
    }
}
