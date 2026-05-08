package downloader;

import downloader.core.Orchestrator;
import downloader.http.Client;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java downloader.Main <url> <output-file>");
            System.exit(1);
        }

        String url = args[0];
        String outputPath = args[1];

        try (Client client = new Client(url);
             Orchestrator orchestrator = new Orchestrator(outputPath, client)) {
            orchestrator.downloadFile();
            System.out.println("\nDownload completed: " + outputPath);
        } catch (Exception e) {
            System.err.println("\nDownload failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
