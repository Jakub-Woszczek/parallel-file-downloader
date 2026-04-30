package downloader;

public record Range(int start, int end) {
    public int getLength() {
        return end - start;
    }
}
