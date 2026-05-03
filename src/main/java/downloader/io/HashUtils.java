package downloader.io;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public class HashUtils {

    /**
     * Computes SHA-256 hash of a file at the given path.
     * The file is read in a streaming manner to avoid loading it entirely
     * into memory, making it suitable for large files.
     *
     * @param path path to the file
     * @return hexadecimal representation of SHA-256 hash
     * @throws Exception if hashing algorithm is not available or I/O error occurs
     */
    public static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        byte[] hash = digest.digest();

        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
