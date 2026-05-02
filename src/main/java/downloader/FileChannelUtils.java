package downloader;

import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileChannelUtils {

    private static final int MAX_ATTEMPTS = 3;

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