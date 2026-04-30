package downloader;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Client {
    private final URL url;
    private final HttpURLConnection connection;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public Client(String url) {
        try {
            this.url = new URL(url);
            this.connection = (HttpURLConnection) this.url.openConnection();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    public byte[] fetchChunk(Range range) throws URISyntaxException, IOException, InterruptedException, ExecutionException {
        var request = java.net.http.HttpRequest.newBuilder(url.toURI())
                .header("Range", "bytes=" + range.start() + "-" + (range.end() - 1))
                .build();

        CompletableFuture<HttpResponse<byte[]>> responseFuture = this.httpClient.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());

        return responseFuture.get().body(); // blocking
    }

    public int getFizeSize() {
        System.out.println("URL: " +  url.toString());
        try {
            var request = java.net.http.HttpRequest.newBuilder(url.toURI())
                    .method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody())
                    .build();

            var response = httpClient.send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers.discarding()
            );
            return Integer.parseInt(
                    response.headers()
                            .firstValue("Content-Length")
                            .orElseThrow()
            );

        } catch (InterruptedException | URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

}
