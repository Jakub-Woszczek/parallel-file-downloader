package downloader.http;

/**
 * Thrown when the HTTP `Content-Range` header is missing, malformed,
 * or does not match the requested byte range.
 */
public class InvalidContentRangeException extends Exception {
    public InvalidContentRangeException(String message) {
        super(message);
    }

    public InvalidContentRangeException(String message, Throwable cause) {
        super(message, cause);
    }
}