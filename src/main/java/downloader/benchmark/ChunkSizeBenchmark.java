package downloader.benchmark;

import downloader.Client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ChunkSizeBenchmark {

    private static final int NUM_TRIES = 10;

    public static void main(String[] args) throws Exception {
        String[] testFiles = {
                "t_100mb.dat",
                "t_1gb.dat"
        };

        List<Integer> chunkSizesMB = new ArrayList<>();
        for (int i = 5; i <= 100; i += 5) {
            chunkSizesMB.add(i);
        }

        for (String filename : testFiles) {
            System.out.println("\n=== Testing: " + filename + " (Averages over " + NUM_TRIES + " tries) ===");
            System.out.println("Chunk Size (MB) | Avg Time (ms) | Avg Speed (MB/s) | Requests");
            System.out.println("----------------|---------------|------------------|----------");

            for (int chunkMB : chunkSizesMB) {
                long totalDurationMs = 0;
                double totalSpeedMBps = 0;
                int requestCount = 0;
                boolean skipped = false;

                for (int t = 0; t < NUM_TRIES; t++) {
                    BenchResult result = testChunkSize(filename, chunkMB);

                    if (result == null) {
                        skipped = true;
                        break;
                    }

                    totalDurationMs += result.durationMs;
                    totalSpeedMBps += result.speedMBps;
                    requestCount = result.requestCount;

                    Thread.sleep(500);
                }

                if (skipped) continue;

                double avgDuration = (double) totalDurationMs / NUM_TRIES;
                double avgSpeed = totalSpeedMBps / NUM_TRIES;

                System.out.printf("%15d | %13.2f | %16.2f | %8d\n",
                        chunkMB,
                        avgDuration,
                        avgSpeed,
                        requestCount
                );

                Thread.sleep(1000); // cooldown between chunks
            }
        }
    }

    private static BenchResult testChunkSize(String filename, int chunkSizeMB)
            throws Exception {

        String url = "http://localhost:8080/" + filename;
        Path tempFile = Files.createTempFile("chunk-bench-", ".tmp");

        try {
            Client client = new Client(url);
            int fileSize = client.getFileSize();
            int chunkSizeBytes = chunkSizeMB * 1024 * 1024;

            if (chunkSizeBytes >= fileSize) {
                return null;
            }

            int requestCount = (int) Math.ceil((double) fileSize / chunkSizeBytes);

            Instant start = Instant.now();
            downloadWithFixedChunkSize(client, tempFile, fileSize, chunkSizeBytes);
            Instant end = Instant.now();

            long durationMs = Duration.between(start, end).toMillis();
            double speedMBps = (fileSize / 1024.0 / 1024.0) / (durationMs / 1000.0);

            return new BenchResult(durationMs, speedMBps, requestCount);

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void downloadWithFixedChunkSize(
            Client client,
            Path destination,
            int fileSize,
            int chunkSize) throws Exception {

        try (var raf = new java.io.RandomAccessFile(destination.toFile(), "rw");
             var channel = raf.getChannel()) {

            int offset = 0;
            while (offset < fileSize) {
                int end = Math.min(offset + chunkSize, fileSize);
                downloader.Range range = new downloader.Range(offset, end);

                byte[] data = client.fetchChunk(range);
                channel.write(java.nio.ByteBuffer.wrap(data), offset);

                offset = end;
            }
        }
    }

    static class BenchResult {
        long durationMs;
        double speedMBps;
        int requestCount;

        BenchResult(long durationMs, double speedMBps, int requestCount) {
            this.durationMs = durationMs;
            this.speedMBps = speedMBps;
            this.requestCount = requestCount;
        }
    }
}