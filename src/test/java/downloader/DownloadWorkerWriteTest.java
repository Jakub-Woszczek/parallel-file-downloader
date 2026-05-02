package downloader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DownloadWorkerWriteTest {

    @TempDir
    Path tempDir;

    private FileChannel fileChannel;
    private DownloadWorker worker;

    @BeforeEach
    void setUp() throws Exception {
        Path file = tempDir.resolve("test.bin");

        RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw");
        raf.setLength(100);

        fileChannel = raf.getChannel();
        Client dummyClient = new Client("http://0.0.0.0");
        Orchestrator orchestrator = new Orchestrator(tempDir.toString() + "f.txt", dummyClient) {
            @Override
            public long getFileSize() {
                return 100;
            }
        };

        worker = new DownloadWorker(orchestrator, fileChannel, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        fileChannel.close();
    }

    // reflection helper
    private void write(byte[] data, ChunkRange ChunkRange) throws Exception {
        Method m = DownloadWorker.class
                .getDeclaredMethod("writeToFile", byte[].class, ChunkRange.class);

        m.setAccessible(true);
        m.invoke(worker, data, ChunkRange);
    }

    private byte[] readAll() throws Exception {
        byte[] content = new byte[(int) fileChannel.size()];
        fileChannel.read(java.nio.ByteBuffer.wrap(content), 0);
        return content;
    }

    @Test
    void writeAtStartOfFile() throws Exception {
        byte[] data = new byte[]{1, 2, 3, 4};

        write(data, new ChunkRange(0, 4));

        byte[] file = readAll();
        assertArrayEquals(new byte[]{1, 2, 3, 4}, java.util.Arrays.copyOfRange(file, 0, 4));
    }

    @Test
    void writeAtEndOfFile() throws Exception {
        byte[] data = new byte[]{9, 9, 9};

        write(data, new ChunkRange(97, 100));

        byte[] file = readAll();

        assertArrayEquals(new byte[]{9, 9, 9},
                java.util.Arrays.copyOfRange(file, 97, 100));
    }

    @Test
    void writeInMiddle() throws Exception {
        byte[] data = new byte[]{5, 5, 5, 5};

        write(data, new ChunkRange(40, 44));

        byte[] file = readAll();

        assertArrayEquals(new byte[]{5, 5, 5, 5},
                java.util.Arrays.copyOfRange(file, 40, 44));
    }

    @Test
    void throwsWhenChunkRangeExceedsFileSize() {
        byte[] data = new byte[10];

        Exception ex = assertThrows(Exception.class, () ->
                write(data, new ChunkRange(95, 105))
        );

        assertTrue(ex.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void throwsWhenDataLengthMismatch() {
        byte[] data = new byte[5];

        Exception ex = assertThrows(Exception.class, () ->
                write(data, new ChunkRange(10, 20)) // expects 10 bytes
        );

        assertTrue(ex.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void multipleWritesDoNotOverlapIncorrectly() throws Exception {
        write(new byte[]{1, 1, 1}, new ChunkRange(0, 3));
        write(new byte[]{2, 2, 2}, new ChunkRange(3, 6));

        byte[] file = readAll();

        assertArrayEquals(new byte[]{1, 1, 1, 2, 2, 2},
                java.util.Arrays.copyOfRange(file, 0, 6));
    }
}