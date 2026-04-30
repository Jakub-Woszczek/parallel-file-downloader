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
    private static final int CORES = Runtime.getRuntime().availableProcessors();

    public Orchestrator(String path, Client httpClient) {
        this.httpClient = httpClient;
        prepareSaveFile(path);

        try {
            RandomAccessFile writer = new RandomAccessFile(path, "rw");
            fileChannel = writer.getChannel();
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Path " + path + "(not found): " + " ", e);
        }
    }

    public void downloadFile(int threadPerCore) {
        // TODO: add here validation if server supports range queries (206)
        long fileSize = httpClient.getFileSize();
        int threadsCount = CORES * threadPerCore;
        long chunkSize = 10 * 1024 * 1024;

        Thread[] threads = new Thread[threadsCount];
        for (int i = 0; i < threadsCount; i++) {
            long start = i * chunkSize;
            long end = (i == threadsCount - 1)
                    ? fileSize
                    : start + chunkSize;

            Range range = new Range(start, end);

            DownloadWorker worker =
                    new DownloadWorker(fileChannel, httpClient, range);

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
            throw new RuntimeException("Failed to initialize save file: ", e);
        }
    }
}
