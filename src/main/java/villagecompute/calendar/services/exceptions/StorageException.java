package villagecompute.calendar.services.exceptions;

/** Exception thrown when file storage operations fail (e.g., R2 upload). */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
