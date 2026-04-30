package downloader;

public record Range(long start, long end) {
    public long getLength() {
        return end - start;
    }
}
