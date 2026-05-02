package downloader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FunctionalTests {

    public static void main(String[] args) throws Exception {
        String url = "http://localhost:8080/t_1gb.dat";

        Path myFile = Path.of("http://localhost:8080/t_1gb.dat");
        Path refFile = Path.of("src/test/resources/save/functional.dat");

        try (Client client = new Client(url);
             Orchestrator orch = new Orchestrator(myFile.toString(), client)) {
            orch.downloadFile();
        }

        try (InputStream in = new java.net.URL(url).openStream()) {
            Files.copy(in, refFile);
        }

        String h1 = HashUtils.sha256(myFile);
        String h2 = HashUtils.sha256(refFile);

        if (!h1.equals(h2)) {
            throw new RuntimeException("Download corrupted!");
        }

        System.out.println("Test passed.");
    }
}