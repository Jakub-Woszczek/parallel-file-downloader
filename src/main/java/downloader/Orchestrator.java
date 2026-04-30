package downloader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class Orchestrator {
    FileChannel fileChannel;
    Client httpClient;

    public Orchestrator(String path, Client httpClient) {
        this.httpClient = httpClient;
        prepareSaveFile(path);

        try {
            RandomAccessFile writer = new RandomAccessFile(path, "rw");
            fileChannel = writer.getChannel();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void downloadFile(String url) {
        int fileSize = httpClient.getFizeSize();
        int cores = Runtime.getRuntime().availableProcessors();
        int threadsCount = cores * 2;
        int chunkSize = fileSize / threadsCount;

        Client client = new Client(url);

        Thread[] threads = new Thread[threadsCount];
        for  (int i = 0; i < threadsCount; i++) {
            int start = i * chunkSize;
            int end = (i == threadsCount - 1)
                    ? fileSize
                    : start + chunkSize;

            Range range = new Range(start, end);

            DownloadWorker worker =
                    new DownloadWorker(fileChannel, client, range);

            threads[i] = new Thread(worker);
            threads[i].start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // TODO: workaround deleting file
    public void prepareSaveFile(String path) {
        try {
            Path filePath = Path.of(path);

            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.deleteIfExists(filePath);
            Files.createFile(filePath);

            RandomAccessFile writer = new RandomAccessFile(filePath.toFile(), "rw");
            this.fileChannel = writer.getChannel();

        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize file", e);
        }
    }
}
