package downloader.core;

/**
 * Represents an inclusive-exclusive byte range used for chunked file downloading.
 * <p>
 * The range is defined as [start, end), meaning:
 * - start is inclusive
 * - end is exclusive
 * <p>
 * This format simplifies chunk splitting and length calculation,
 * while being compatible with HTTP Range requests (after adjustment).
 */
public record ChunkRange(long start, long end) {
    public ChunkRange {
        if (start >= end) {
            throw new IllegalArgumentException(
                    "Start must be less than end. Got: [" + start + ", " + end + "]"
            );
        }
        if (start < 0) {
            throw new IllegalArgumentException(
                    "Start cannot be negative. Got: " + start
            );
        }
    }

    public long getLength() {
        return end - start;
    }
}