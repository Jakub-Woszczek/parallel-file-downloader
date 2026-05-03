package downloader.core;

import downloader.http.Client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class DownloadWorker implements Runnable {

    Client httpClient;
    FileChannel fileChannel;
    Orchestrator orchestrator;
    private static final int MAX_WRITES_ATTEMPTS = 3;

    public DownloadWorker(Orchestrator orchestrator, FileChannel fileChannel, Client httpClient) {
        this.fileChannel = fileChannel;
        this.httpClient = httpClient;
        this.orchestrator = orchestrator;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            ChunkRange chunkRange = orchestrator.acquireChunkRange();
            if (chunkRange == null) {
                break;
            }

            try {
                byte[] data = httpClient.fetchChunk(chunkRange);
                writeToFile(data, chunkRange);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void writeToFile(byte[] data, ChunkRange chunkRange) {
        int attempts = 0;

        while (true) {
            try {
                long fileSize = orchestrator.getFileSize();

                long start = chunkRange.start();
                long end = chunkRange.end();
                long length = chunkRange.getLength();

                if (end > fileSize) {
                    throw new IllegalArgumentException(
                            "Invalid chunkRange: end exceeds file size: " + end + ", fileSize: " + fileSize
                    );
                }

                if (data.length != length) {
                    throw new IllegalArgumentException(
                            "Data length (" + data.length + ") does not match chunkRange length (" + length + ")"
                    );
                }

                ByteBuffer buffer = ByteBuffer.wrap(data);
                long position = start;

                while (buffer.hasRemaining()) {
                    int written = fileChannel.write(buffer, position);
                    if (written <= 0) {
                        throw new IOException("Failed to make progress writing chunk");
                    }
                    position += written;
                }
                break;

            } catch (IOException e) {
                attempts++;
                if (attempts >= MAX_WRITES_ATTEMPTS) {
                    throw new RuntimeException("Failed to write chunk after retries: " + chunkRange, e);
                }

                try {
                    Thread.sleep(100L * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Write retry interrupted", ie);
                }
            }
        }
    }
}
