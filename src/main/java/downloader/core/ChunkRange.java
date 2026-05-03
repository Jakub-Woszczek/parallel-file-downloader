package downloader.core;

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