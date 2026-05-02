package downloader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkRangeTest {

    @Test
    void createsValidRange() {
        ChunkRange range = new ChunkRange(10, 20);

        assertEquals(10, range.start());
        assertEquals(20, range.end());
    }

    @Test
    void computesCorrectLength() {
        ChunkRange range = new ChunkRange(5, 15);

        assertEquals(10, range.getLength());
    }

    @Test
    void throwsWhenStartEqualsEnd() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new ChunkRange(10, 10)
        );

        assertTrue(ex.getMessage().contains("Start must be less than end"));
    }

    @Test
    void throwsWhenStartGreaterThanEnd() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new ChunkRange(20, 10)
        );

        assertTrue(ex.getMessage().contains("Start must be less than end"));
    }

    @Test
    void throwsWhenStartNegative() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new ChunkRange(-1, 10)
        );

        assertTrue(ex.getMessage().contains("cannot be negative"));
    }

    @Test
    void minimalValidRange() {
        ChunkRange range = new ChunkRange(0, 1);

        assertEquals(1, range.getLength());
    }

    @Test
    void largeRange() {
        ChunkRange range = new ChunkRange(0, Long.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, range.getLength());
    }
}