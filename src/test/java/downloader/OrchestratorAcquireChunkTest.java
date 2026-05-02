package downloader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class OrchestratorAcquireChunkTest {

    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() throws Exception {
        String tempPath = "target/test-file.tmp";
        Client dummyClient = new Client("http://0.0.0.0");
        orchestrator = new Orchestrator(tempPath, dummyClient);

        setIndexArray(new long[]{
                0,
                10,
                20,
                30
        });

        setChunkIndex(0);
    }

    // Reflection helpers
    private void setIndexArray(long[] array) throws Exception {
        Field f = Orchestrator.class.getDeclaredField("indexArray");
        f.setAccessible(true);
        f.set(orchestrator, array);
    }

    private void setChunkIndex(int value) throws Exception {
        Field f = Orchestrator.class.getDeclaredField("chunkIndex");
        f.setAccessible(true);
        f.set(orchestrator, value);
    }

    private int getChunkIndex() throws Exception {
        Field f = Orchestrator.class.getDeclaredField("chunkIndex");
        f.setAccessible(true);
        return (int) f.get(orchestrator);
    }

    @Test
    void returnsCorrectFirstChunkRange() {
        ChunkRange r = orchestrator.acquireChunkRange();

        assertNotNull(r);
        assertEquals(0, r.start());
        assertEquals(10, r.end());
    }

    @Test
    void chunkIndexIsIncrementedAfterAcquire() throws Exception {
        assertEquals(0, getChunkIndex());

        orchestrator.acquireChunkRange();

        assertEquals(1, getChunkIndex(), "chunkIndex should increment after acquisition");
    }

    @Test
    void sequentialCallsReturnCorrectRanges() {
        ChunkRange r1 = orchestrator.acquireChunkRange();
        ChunkRange r2 = orchestrator.acquireChunkRange();
        ChunkRange r3 = orchestrator.acquireChunkRange();
        ChunkRange r4 = orchestrator.acquireChunkRange();

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);
        assertNull(r4);

        assertEquals(0, r1.start());
        assertEquals(10, r1.end());

        assertEquals(10, r2.start());
        assertEquals(20, r2.end());

        assertEquals(20, r3.start());
        assertEquals(30, r3.end());
    }

    @Test
    void returnsNullWhenNoMoreChunks() {
        // 3 valid chunks
        orchestrator.acquireChunkRange();
        orchestrator.acquireChunkRange();
        orchestrator.acquireChunkRange();

        // next call should return null
        ChunkRange r = orchestrator.acquireChunkRange();

        assertNull(r);
    }
}
