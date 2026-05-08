package downloader.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProgressReporter implements AutoCloseable {

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    public void start(Orchestrator orchestrator) {

        scheduler.scheduleAtFixedRate(() -> {

            int percent = orchestrator.getDownloadProcent();

            System.out.printf("\rDownloaded: %d%%", percent);
            System.out.flush();

        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}