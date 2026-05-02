package downloader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

public class Orchestrator implements AutoCloseable {
    FileChannel fileChannel;
    RandomAccessFile randomAccessFile;
    Client httpClient;
    private static final int CORES = Runtime.getRuntime().availableProcessors();
    private static final long MB_SIZE = 1024 * 1024;
    private static final long GB_SIZE = 1024 * 1024 * 1024;
    private static final long CHUNK_SIZE = 10 * MB_SIZE;
    private static final int THREADS_PER_CORE = 11;
    /*
     * Shared resources:
     * indexArray - list of ranges for i-th chunk defined as (L[i], L[i+1]).
     */
    private final ReentrantLock lock = new ReentrantLock();
    private int chunkIndex = 0;
    private long[] indexArray;

    public Orchestrator(String path, Client httpClient) {
        this.httpClient = httpClient;
        this.randomAccessFile = prepareSaveFile(path);
        this.fileChannel = randomAccessFile.getChannel();
    }

    public void downloadFile() throws InterruptedException {
        if (!httpClient.supportsRangeRequests()) {
            throw new IllegalStateException("Server does not support HTTP range requests (206).");
        }

        long fileSize = httpClient.getFileSize();
        int availableThreads = CORES * THREADS_PER_CORE;
        int threadsCount = computeChunks(fileSize, availableThreads);

        Thread[] threads = new Thread[threadsCount];
        try {
            for (int i = 0; i < threadsCount; i++) {
                DownloadWorker worker = new DownloadWorker(this, fileChannel, httpClient);

                threads[i] = new Thread(worker);
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            for (Thread t : threads) {
                if (t != null) {
                    t.interrupt();
                }
            }
            throw e;
        }
    }

    // TODO: make test if thread makes one request given chunk size as file size
    private int computeChunks(long fileSize, int threadsCount) {

        int chunksAmount = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        if (chunksAmount < threadsCount) {
            threadsCount = chunksAmount;
        }

        // Initialization
        indexArray = new long[chunksAmount + 1];
        for (int i = 0; i < chunksAmount; i++) {
            indexArray[i] = i * CHUNK_SIZE;
        }
        indexArray[chunksAmount] = fileSize;

        return threadsCount;
    }

    public Range aquireChunkRange() {
        lock.lock();
        // no more chunks
        if (chunkIndex >= indexArray.length - 1) {
            lock.unlock();
            return null;
        }

        try {
            long start = indexArray[chunkIndex];
            long end = indexArray[chunkIndex + 1];
            chunkIndex++;
            return new Range(start, end);
        } finally {
            lock.unlock();
        }
    }

    public RandomAccessFile prepareSaveFile(String path) {
        try {
            Path filePath = Path.of(path);

            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.deleteIfExists(filePath); // creating logic of 'file (1).txt' is not crucial in this task
            Files.createFile(filePath);

            return new RandomAccessFile(filePath.toFile(), "rw");

        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize save file: ", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (fileChannel != null) {
            fileChannel.close();
        }
        if (randomAccessFile != null) {
            randomAccessFile.close();
        }
    }
}
