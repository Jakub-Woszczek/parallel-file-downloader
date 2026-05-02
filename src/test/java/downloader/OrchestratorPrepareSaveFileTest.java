package downloader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestratorPrepareSaveFileTest {

    private Orchestrator orchestrator;

    @TempDir
    Path tempDir; // creates real temp dir on disc so tests run in real sandbox

    @BeforeEach
    void setUp() {
        Client dummyClient = new Client("http://0.0.0.0") {
        };
        orchestrator = new Orchestrator(tempDir.resolve("dummy.txt").toString(), dummyClient);
    }

    private RandomAccessFile invokePrepareSaveFile(String path) throws Exception {
        Method m = Orchestrator.class.getDeclaredMethod("prepareSaveFile", String.class);
        m.setAccessible(true);

        try {
            return (RandomAccessFile) m.invoke(orchestrator, path);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();

            if (cause instanceof IOException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (orchestrator != null) {
            orchestrator.close();
        }
    }

    @Test
    void createsNewEmptyFile() throws Exception {
        Path file = tempDir.resolve("file.txt");

        invokePrepareSaveFile(file.toString());

        assertTrue(Files.exists(file), "File must be created");
        assertEquals(0, Files.size(file), "New file must be empty");
    }

    @Test
    void deletesExistingFileBeforeCreatingNewOne() throws Exception {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "OLD CONTENT");
        assertEquals("OLD CONTENT", Files.readString(file));

        invokePrepareSaveFile(file.toString());

        assertEquals(0, Files.size(file), "File must be overwritten and empty");
    }

    @Test
    void nestedDirectoryCreationDeepPath() throws Exception {
        Path deep = tempDir.resolve("x/y/z/deepfile.txt");

        invokePrepareSaveFile(deep.toString());

        assertTrue(Files.exists(deep.getParent()));
        assertTrue(Files.exists(deep));
    }
}
