package downloader;

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
            Range range = orchestrator.acquireChunkRange();
            if (range == null) {
                break;
            }

            try {
                byte[] data = httpClient.fetchChunk(range);
                writeToFile(data, range);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void writeToFile(byte[] data, Range range) {
        int attempts = 0;

        while (true) {
            try {
                fileChannel.write(ByteBuffer.wrap(data), range.start());
                break;

            } catch (IOException e) {
                attempts++;
                if (attempts >= MAX_WRITES_ATTEMPTS) {
                    throw new RuntimeException("Failed to write chunk after retries: " + range, e);
                }

                try {
                    Thread.sleep(100 * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Write retry interrupted", ie);
                }
            }
        }
    }
}
