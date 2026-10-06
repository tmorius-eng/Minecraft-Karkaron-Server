package mn.suld.api.persistence;

/**
 * Unchecked wrapper for persistence failures, so repository contracts can expose
 * {@link java.util.concurrent.CompletableFuture} results without checked
 * exceptions leaking through gameplay code. The cause (e.g. {@code SQLException})
 * is always preserved.
 */
public class RepositoryException extends RuntimeException {

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
