package downloader.benchmark;

import downloader.Orchestrator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ThreadScalingBenchmark {

    private static final String TEST_URL = "http://localhost:8080/t_1gb.dat";
    private static final int RUNS = 10;

    public static void main(String[] args) throws Exception {

        List<Integer> threadCounts = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            threadCounts.add(i);
        }

        System.out.println("File: " + TEST_URL);
        System.out.println("\nThread per core | Time (ms) | Speed (MB/s) | Efficiency");
        System.out.println("----------------|-----------|--------------|------------");

        List<BenchmarkResult> results = new ArrayList<>();
        double baselineSpeed = 0;

        for (int threadCount : threadCounts) {

            List<Long> times = new ArrayList<>();
            List<Double> speeds = new ArrayList<>();

            for (int i = 0; i < RUNS; i++) {
                SingleRunResult r = runBenchmark(threadCount);
                times.add(r.durationMs);
                speeds.add(r.speedMBps);

                Thread.sleep(500); // cooldown
            }

            double avgTime = times.stream().mapToLong(Long::longValue).average().orElse(0);
            double avgSpeed = speeds.stream().mapToDouble(Double::doubleValue).average().orElse(0);

            BenchmarkResult result = new BenchmarkResult(threadCount, avgTime, avgSpeed);
            if (threadCount == 1) {
                baselineSpeed = avgSpeed;
                result.efficiency = 1.0;
            } else {
                result.efficiency = avgSpeed / (baselineSpeed * threadCount);
            }

            results.add(result);

            System.out.printf("%15d | %9.0f | %12.2f | %10.2f%%\n",
                    threadCount,
                    avgTime,
                    avgSpeed,
                    result.efficiency * 100
            );
        }

        // fastest
        BenchmarkResult fastest = results.stream()
                .min((a, b) -> Double.compare(a.avgTime, b.avgTime))
                .orElseThrow();

        System.out.println("\n=== OPTIMAL ===");
        System.out.println("Threads: " + fastest.threads);
        System.out.println("Speed: " + String.format("%.2f MB/s", fastest.avgSpeed));
    }

    // Single execution
    private static SingleRunResult runBenchmark(int threadCount) throws Exception {

        Path tempFile = Files.createTempFile("download-bench-", ".tmp");

        try {
            Instant start = Instant.now();

            Orchestrator orchestrator = new Orchestrator(
                    tempFile.toString(),
                    new downloader.Client(TEST_URL)
            );

            orchestrator.downloadFile(threadCount);

            Instant end = Instant.now();

            long durationMs = Duration.between(start, end).toMillis();
            long fileSize = Files.size(tempFile);

            double speedMBps =
                    (fileSize / 1024.0 / 1024.0) / (durationMs / 1000.0);

            return new SingleRunResult(durationMs, speedMBps);

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    static class SingleRunResult {
        long durationMs;
        double speedMBps;

        SingleRunResult(long durationMs, double speedMBps) {
            this.durationMs = durationMs;
            this.speedMBps = speedMBps;
        }
    }

    static class BenchmarkResult {
        int threads;
        double avgTime;
        double avgSpeed;
        double efficiency;

        BenchmarkResult(int threads, double avgTime, double avgSpeed) {
            this.threads = threads;
            this.avgTime = avgTime;
            this.avgSpeed = avgSpeed;
        }
    }
}