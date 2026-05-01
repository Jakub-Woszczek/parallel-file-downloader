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

public class Client {
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

    public byte[] fetchChunk(Range range) throws InterruptedException {
        HttpRequest request = buildRequest(range);
        long backoff = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<byte[]> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 206) {
                    byte[] body = validateAndExtractBody(response, range);
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

    private HttpRequest buildRequest(Range range) {
        try {
            return HttpRequest.newBuilder(url.toURI())
                    .header("Range", "bytes=" + range.start() + "-" + (range.end() - 1))
                    .build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL/URI: " + url, e);
        }
    }

    private byte[] validateAndExtractBody(HttpResponse<byte[]> response, Range range) throws IOException, InvalidContentRangeException {
        validateContentRange(response, range);

        byte[] body = response.body();
        long expectedLength = range.getLength();

        if (body.length != expectedLength) {
            throw new IOException("Invalid chunk size");
        }
        return body;
    }

    private void validateContentRange(HttpResponse<byte[]> response, Range requestedRange) throws InvalidContentRangeException {
        String contentRange = response.headers()
                .firstValue("Content-Range")
                .orElseThrow(() -> new InvalidContentRangeException(
                        "Missing Content-Range header"));

        // Expected format: bytes start-end/total
        if (!contentRange.startsWith("bytes ")) {
            throw new InvalidContentRangeException("Invalid Content-Range format: " + contentRange);
        }

        try {
            String rangePart = contentRange.substring(6).trim();
            String[] parts = rangePart.split("[ /]");

            String[] startEnd = parts[0].split("-");

            long start = Long.parseLong(startEnd[0]);
            long end = Long.parseLong(startEnd[1]);

            long expectedStart = requestedRange.start();
            long expectedEnd = requestedRange.end() - 1;

            if (start != expectedStart || end != expectedEnd) {
                throw new InvalidContentRangeException(
                        "Server returned mismatched range. Expected: " +
                                expectedStart + "-" + expectedEnd +
                                " but got: " + start + "-" + end);
            }

        } catch (Exception e) {
            throw new InvalidContentRangeException(
                    "Failed to parse Content-Range: " + contentRange, e);
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
        return status == 408 || status == 429 || (status >= 500 && status < 600) || (status >= 200 && status < 300);
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
                    .firstValue("Content-Range")
                    .orElse("");

            return contentRange.startsWith("bytes 0-0");

        } catch (Exception e) {
            return false;
        }
    }
}
