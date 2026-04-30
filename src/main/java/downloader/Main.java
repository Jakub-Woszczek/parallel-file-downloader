package downloader;

public class Main {

    public static void main(String[] args) {
        String filename = "t_10gb.dat";
        String savePath = "src/test/resources/save/" + filename;
        String url = "http://localhost:8080/" +  filename;

        Client client = new Client(url);
        Orchestrator orchestrator = new Orchestrator(savePath, client);

        orchestrator.downloadFile(url);
    }
}
