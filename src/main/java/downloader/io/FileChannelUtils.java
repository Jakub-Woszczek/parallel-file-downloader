package downloader.io;

import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileChannelUtils {

    private static final int MAX_ATTEMPTS = 3;

    /**
     * Truncates the given file channel to the specified size with retry logic.
     * Retries the operation on I/O failure using a backoff strategy.
     *
     * @param fileChannel the file channel to truncate
     * @param size        target size in bytes
     * @throws RuntimeException if truncation fails after maximum retry attempts
     */
    public static void truncateWithRetry(FileChannel fileChannel, long size) {
        int attempts = 0;

        while (true) {
            try {
                fileChannel.truncate(size);
                return;

            } catch (IOException e) {
                attempts++;

                if (attempts >= MAX_ATTEMPTS) {
                    throw new RuntimeException(
                            "Failed to truncate file after " + MAX_ATTEMPTS + " attempts to size: " + size,
                            e
                    );
                }

                try {
                    Thread.sleep(100L * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Truncate retry interrupted", ie);
                }
            }
        }
    }
}