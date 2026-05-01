package downloader;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class DownloadWorker implements Runnable {

    Client httpClient;
    FileChannel fileChannel;
    Orchestrator orchestrator;

    public DownloadWorker(Orchestrator orchestrator, FileChannel fileChannel, Client httpClient) {
        this.fileChannel = fileChannel;
        this.httpClient = httpClient;
        this.orchestrator = orchestrator;
    }

    @Override
    public void run() {
        while (true) {
            Range range = orchestrator.aquireChunkRange();
            if (range == null) {
                break;
            }

            try {
                byte[] data = httpClient.fetchChunk(range);
                fileChannel.write(ByteBuffer.wrap(data), range.start());
            } catch (Exception e) {
                System.err.println("Failed chunk: " + range + " -> " + e.getMessage());
                // TODO: maybe retry queue/ sth
            }
        }
    }
}
