package downloader;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class DownloadWorker implements Runnable{

    Client httpClient;
    FileChannel fileChannel;
    Range range;

    public DownloadWorker(FileChannel fileChannel, Client httpClient,Range range) {
        this.fileChannel = fileChannel;
        this.httpClient = httpClient;
        this.range = range;
    }

    @Override
    public void run() {
        try {
            byte[] data = httpClient.fetchChunk(range);
            fileChannel.write(ByteBuffer.wrap(data), range.start());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
