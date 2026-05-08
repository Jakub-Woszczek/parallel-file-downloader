package downloader.core;

import downloader.http.Client;
import downloader.io.FileChannelUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Coordinates the parallel file download process.
 * Responsible for:
 * - initializing the output file
 * - splitting the file into chunks
 * - managing worker threads
 * - distributing chunk ranges safely between workers
 */
public class Orchestrator implements AutoCloseable {
    final FileChannel fileChannel;
    final RandomAccessFile randomAccessFile;
    final Client httpClient;
    long fileSize;
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
    private long downloadProgress = 0; // How many bytes of file is already downloaded

    public Orchestrator(String path, Client httpClient) {
        this.httpClient = httpClient;
        this.randomAccessFile = prepareSaveFile(path);
        this.fileChannel = randomAccessFile.getChannel();
    }

    /**
     * Starts the download process.
     * Validates server capabilities, computes chunk distribution,
     * spawns worker threads, and waits for completion.
     *
     * @throws InterruptedException  if the download is interrupted
     * @throws IllegalStateException if the server does not support range requests
     */
    public void downloadFile() throws InterruptedException {
        if (!httpClient.supportsRangeRequests()) {
            throw new IllegalStateException("Server does not support HTTP range requests (206).");
        }

        fileSize = httpClient.getFileSize();
        int availableThreads = CORES * THREADS_PER_CORE;
        int threadsCount = computeChunks(fileSize, availableThreads);
        FileChannelUtils.truncateWithRetry(fileChannel, fileSize);

        Thread[] threads = new Thread[threadsCount];
        try (ProgressReporter progressReporter = new ProgressReporter()) {
            progressReporter.start(this);

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
        System.out.print("\rDownloaded: 100%%");
        System.out.flush();
    }

    /**
     * Splits the file into fixed-size chunks and initializes internal range mapping.
     * Also adjusts the number of worker threads so it does not exceed
     * the number of chunks.
     *
     * @param fileSize     total file size in bytes
     * @param threadsCount desired number of threads
     * @return effective number of threads to use
     */
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

    /**
     * Provides the next available chunk range in a thread-safe manner.
     * Each chunk is assigned exactly once to a worker thread.
     *
     * @return next ChunkRange or null if no chunks remain
     */
    public ChunkRange acquireChunkRange() {
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
            return new ChunkRange(start, end);
        } finally {
            lock.unlock();
        }
    }

    private RandomAccessFile prepareSaveFile(String path) {
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

    /**
     * All workers update here their chunkSize
     */
    public void downloadProgressUpdate(long chunkSize) {
        lock.lock();
        try {
            downloadProgress += chunkSize;
        } finally {
            lock.unlock();
        }
    }

    public int getDownloadProcent() {
        lock.lock();
        try {
            return (int) ((downloadProgress * 100) / fileSize);
        } finally {
            lock.unlock();
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

    public long getChunkSize() {
        return CHUNK_SIZE;
    }

    public long[] getIndexArray() {
        return indexArray;
    }

    public long getFileSize() {
        return fileSize;
    }
}
