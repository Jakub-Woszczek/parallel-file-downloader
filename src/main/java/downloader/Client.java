package downloader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Client {
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

    // TODO: rebuild to return CompletableFuture<byte[]> -> async pipeline
    public byte[] fetchChunk(Range range) {
        HttpRequest request = null;
        try {
            request = HttpRequest.newBuilder(url.toURI())
                    .header("Range", "bytes=" + range.start() + "-" + (range.end() - 1))
                    .build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL/URI: " + url, e);
        }

        // non-blocking
        CompletableFuture<HttpResponse<byte[]>> future =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());

        HttpResponse<byte[]> response;
        try {
            response = future.get(); // blocking
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for chunk", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof IOException) {
                throw new RuntimeException("Network failure during chunk download", cause);
            }
            throw new RuntimeException("Unexpected async failure", cause);
        }
        if (response.statusCode() != 206) {
            throw new RuntimeException("Server did not return partial content. Status: " + response.statusCode());
        }

        // TODO: add validation if content range is in right format

        byte[] body = response.body();
        int expectedLength = range.getLength();

        if (body.length != expectedLength) {
            // TODO: retry logic
            throw new RuntimeException(
                    "Chunk size mismatch. Expected " + expectedLength + " but got " + body.length
            );
        }

        return body;
    }

    public int getFileSize() {
        try {
            var request = java.net.http.HttpRequest.newBuilder(url.toURI())
                    .method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody())
                    .build();

            var response = httpClient.send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers.discarding()
            );
            // TODO: int crash with file 10GB<
            return Integer.parseInt(
                    response.headers()
                            .firstValue("Content-Length")
                            .orElseThrow()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operation interrupted", e);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL/URI: " + url, e);
        } catch (IOException e) {
            // TODO: retry logic
            throw new RuntimeException("Download failed (network issue): " + e.getMessage(), e);
        }
    }

}
