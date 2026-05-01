package downloader;

public class Main {
    private static final int THREADS_PER_CORE = 11;
    private static final String SAVE_PATH = "src/test/resources/save/";
    private static final String FILENAME = "t_1gb.dat";

    public static void main(String[] args) throws InterruptedException {
        String filename = "t_10gb.dat";
//        String savePath = "src/test/resources/save/" + filename;
        String url = "http://localhost:8080/" + FILENAME;

        Client client = new Client(url);
        Orchestrator orchestrator = new Orchestrator(SAVE_PATH + FILENAME, client);

        orchestrator.downloadFile(THREADS_PER_CORE);
    }
}
