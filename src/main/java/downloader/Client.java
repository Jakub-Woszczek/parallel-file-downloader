package downloader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public class Client implements AutoCloseable {
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 50;
    private static final long MAX_BACKOFF = 5000;
    private final URL url;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Client(String url) {
        try {
            this.url = new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
    }

    public byte[] fetchChunk(ChunkRange chunkRange) throws InterruptedException {
        HttpRequest request = buildRequest(chunkRange);
        long backoff = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<byte[]> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 206) {
                    byte[] body = validateAndExtractBody(response, chunkRange);
                    return body;
                }

                if (!isRetryableStatus(response.statusCode())) {
                    throw new RuntimeException("Non-retryable status: " + response.statusCode());
                }

            } catch (IOException | InvalidContentRangeException e) { // ? TBD: should RuntimeException be here ?
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("Failed after retries", e);
                }
            }

            sleepWithBackoff(backoff);
            backoff = Math.min(MAX_BACKOFF, backoff * 2);
        }

        throw new RuntimeException("Exhausted retries without success");
    }

    /*
    HTTP request if inclusive on end byte:
    curl -H "ChunkRange: bytes=0-2" http://localhost:8080/file1.txt
    > ask

    so when chunk size is 3, I have index array like this: [0,3,6,...]
    so the second byte is not inclusive and i decrease it in header
     */
    private HttpRequest buildRequest(ChunkRange chunkRange) {
        try {
            return HttpRequest.newBuilder(url.toURI())
                    .header("Range", "bytes=" + chunkRange.start() + "-" + (chunkRange.end() - 1))
                    .build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL/URI: " + url, e);
        }
    }

    private byte[] validateAndExtractBody(HttpResponse<byte[]> response, ChunkRange chunkRange) throws IOException, InvalidContentRangeException {
        validateContentRange(response, chunkRange);

        byte[] body = response.body();
        long expectedLength = chunkRange.getLength();

        if (body.length != expectedLength) {
            throw new IOException("Invalid chunk size");
        }
        return body;
    }

    private void validateContentRange(HttpResponse<byte[]> response, ChunkRange requestedChunkRange) throws InvalidContentRangeException {
        String contentRange = response.headers()
                .firstValue("Content-ChunkRange")
                .orElseThrow(() -> new InvalidContentRangeException(
                        "Missing Content-ChunkRange header"));

        // Expected format: bytes start-end/total
        if (!contentRange.startsWith("bytes ")) {
            throw new InvalidContentRangeException("Invalid Content-ChunkRange format: " + contentRange);
        }

        try {
            String rangePart = contentRange.substring(6).trim();
            String[] parts = rangePart.split("[ /]");

            String[] startEnd = parts[0].split("-");

            long start = Long.parseLong(startEnd[0]);
            long end = Long.parseLong(startEnd[1]);

            long expectedStart = requestedChunkRange.start();
            long expectedEnd = requestedChunkRange.end() - 1;

            if (start != expectedStart || end != expectedEnd) {
                throw new InvalidContentRangeException(
                        "Server returned mismatched range. Expected: " +
                                expectedStart + "-" + expectedEnd +
                                " but got: " + start + "-" + end);
            }

        } catch (Exception e) {
            throw new InvalidContentRangeException(
                    "Failed to parse Content-ChunkRange: " + contentRange, e);
        }
    }

    public long getFileSize() throws InterruptedException {
        HttpRequest request = buildHeadRequest();
        long backoff = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                var response = httpClient.send(
                        request,
                        java.net.http.HttpResponse.BodyHandlers.discarding()
                );

                if (response.statusCode() == 200) {
                    return extractContentLength(response);
                }
                if (!isRetryableStatus(response.statusCode())) {
                    throw new RuntimeException("Non-retryable status: " + response.statusCode());
                }

            } catch (IOException | NumberFormatException | IllegalStateException e) {
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("Failed after retries", e);
                }
            }

            sleepWithBackoff(backoff);
            backoff = Math.min(MAX_BACKOFF, backoff * 2);
        }
        throw new RuntimeException("Exhausted retries");
    }

    private HttpRequest buildHeadRequest() {
        try {
            return HttpRequest.newBuilder(url.toURI())
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL/URI: " + url, e);
        }
    }

    private long extractContentLength(HttpResponse<?> response) {
        String value = response.headers()
                .firstValue("Content-Length")
                .orElseThrow(() -> new IllegalStateException("Missing Content-Length"));

        return Long.parseLong(value);
    }

    private boolean isRetryableStatus(int status) {
        return status == 408 || status == 429 || (status >= 500 && status < 600);
    }

    private void sleepWithBackoff(long backoff) throws InterruptedException {
        long jitter = ThreadLocalRandom.current().nextLong(backoff);
        Thread.sleep(backoff + jitter);
    }

    public boolean supportsRangeRequests() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url.toURI())
                    .header("Range", "bytes=0-0")
                    .GET()
                    .build();

            HttpResponse<Void> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 206) {
                return false;
            }

            String contentRange = response.headers()
                    .firstValue("Content-ChunkRange")
                    .orElse("");

            return contentRange.startsWith("bytes 0-0");

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() throws Exception {
    }
}
