package downloader;

public class InvalidContentRangeException extends Exception {
    public InvalidContentRangeException(String message) {
        super(message);
    }

    public InvalidContentRangeException(String message, Throwable cause) {
        super(message, cause);
    }
}