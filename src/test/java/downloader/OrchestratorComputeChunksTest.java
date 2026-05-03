package downloader;

import downloader.core.Orchestrator;
import downloader.http.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestratorComputeChunksTest {

    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        String tempPath = "target/test-file.tmp";
        Client dummyClient = new Client("http://0.0.0.0");

        orchestrator = new Orchestrator(tempPath, dummyClient);
    }

    // helpers
    private int invokeComputeChunks(long fileSize, int threads) throws Exception {
        Method m = Orchestrator.class.getDeclaredMethod("computeChunks", long.class, int.class);
        m.setAccessible(true);
        return (int) m.invoke(orchestrator, fileSize, threads);
    }

    @Test
    void fileSizeLessThanChunkSize() throws Exception {
        long fileSize = orchestrator.getChunkSize() / 2;
        int threads = 8;

        int resultThreads = invokeComputeChunks(fileSize, threads);

        long[] index = orchestrator.getIndexArray();
        assertEquals(1, index.length - 1, "Should create only 1 chunk");
        assertEquals(1, resultThreads, "Threads should be reduced to 1");
        assertEquals(0, index[0], "First index must be 0");
        assertEquals(fileSize, index[1], "Last index must equal file size");
    }

    @Test
    void exactOneChunkBoundary() throws Exception {
        long fileSize = orchestrator.getChunkSize();
        int threads = 5;

        int resultThreads = invokeComputeChunks(fileSize, threads);

        long[] index = orchestrator.getIndexArray();
        assertEquals(1, index.length - 1);
        assertEquals(1, resultThreads);
        assertEquals(0, index[0]);
        assertEquals(fileSize, index[1]);
    }

    @Test
    void multipleChunksNoThreadReduction() throws Exception {
        long fileSize = orchestrator.getChunkSize() * 5; // 5 chunks
        int threads = 10;

        int resultThreads = invokeComputeChunks(fileSize, threads);

        long[] index = orchestrator.getIndexArray();
        assertEquals(5, index.length - 1);
        assertEquals(5, resultThreads, "Threads should be reduced");

        assertChunkSpacingValid(index);
    }

    @Test
    void threadReductionWhenChunksLessThanThreads() throws Exception {
        long fileSize = (long) (orchestrator.getChunkSize() * 3.5); // 3.5 chunks
        int threads = 10;

        int resultThreads = invokeComputeChunks(fileSize, threads);

        long[] index = orchestrator.getIndexArray();
        assertEquals(4, index.length - 1, "Should round up to 4 chunks");
        assertEquals(4, resultThreads, "Threads should be reduced to 4");

        assertChunkSpacingValid(index);
    }

    @Test
    void smallFileHalfChunk() throws Exception {
        long fileSize = (long) (orchestrator.getChunkSize() * 0.5); // 0.5 chunk
        int threads = 4;

        int resultThreads = invokeComputeChunks(fileSize, threads);

        long[] index = orchestrator.getIndexArray();
        assertEquals(1, index.length - 1);
        assertEquals(1, resultThreads);
        assertEquals(0, index[0]);
        assertEquals(fileSize, index[1]);
    }

    @Test
    void largeFileMultipleExactChunks() throws Exception {
        long fileSize = 100 * orchestrator.getChunkSize();
        int threads = 50;

        int resultThreads = invokeComputeChunks(fileSize, threads);

        long[] index = orchestrator.getIndexArray();
        assertEquals(100, index.length - 1);
        assertEquals(50, resultThreads);

        assertChunkSpacingValid(index);
    }


    // Helper assertion
    private void assertChunkSpacingValid(long[] index) {
        long expectedChunk = orchestrator.getChunkSize();

        for (int i = 0; i < index.length - 2; i++) {
            long diff = index[i + 1] - index[i];
            assertEquals(expectedChunk, diff,
                    "Chunk spacing must equal CHUNK_SIZE between index " + i + " and " + (i + 1));
        }

        assertTrue(index.length >= 2, "Index must have at least start and end");
    }
}