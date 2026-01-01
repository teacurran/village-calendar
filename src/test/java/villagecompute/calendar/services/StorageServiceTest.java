package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for StorageService. Tests input validation for file upload and delete operations. Note: S3 interaction
 * tests are not included as they require external service mocking.
 */
class StorageServiceTest {

    private final StorageService storageService = new StorageService();

    private static final String VALID_FILENAME = "test-file.pdf";
    private static final String VALID_CONTENT_TYPE = "application/pdf";
    private static final String VALID_PUBLIC_URL = "https://example.com/calendar-pdfs/test.pdf";

    // ========== UPLOAD FILE VALIDATION TESTS ==========

    @Test
    void testUploadFile_NullFilename_ThrowsIllegalArgumentException() {
        byte[] fileBytes = new byte[]{1, 2, 3};

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storageService.uploadFile(null, fileBytes, VALID_CONTENT_TYPE));

        assertEquals("Filename cannot be null or empty", exception.getMessage());
    }

    @Test
    void testUploadFile_EmptyFilename_ThrowsIllegalArgumentException() {
        byte[] fileBytes = new byte[]{1, 2, 3};

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storageService.uploadFile("", fileBytes, VALID_CONTENT_TYPE));

        assertEquals("Filename cannot be null or empty", exception.getMessage());
    }

    @Test
    void testUploadFile_NullFileBytes_ThrowsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storageService.uploadFile(VALID_FILENAME, null, VALID_CONTENT_TYPE));

        assertEquals("File bytes cannot be null or empty", exception.getMessage());
    }

    @Test
    void testUploadFile_EmptyFileBytes_ThrowsIllegalArgumentException() {
        byte[] emptyBytes = new byte[0];

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storageService.uploadFile(VALID_FILENAME, emptyBytes, VALID_CONTENT_TYPE));

        assertEquals("File bytes cannot be null or empty", exception.getMessage());
    }

    // ========== DELETE FILE VALIDATION TESTS ==========

    @Test
    void testDeleteFile_NullPublicUrl_ThrowsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storageService.deleteFile(null));

        assertEquals("Public URL cannot be null or empty", exception.getMessage());
    }

    @Test
    void testDeleteFile_EmptyPublicUrl_ThrowsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storageService.deleteFile(""));

        assertEquals("Public URL cannot be null or empty", exception.getMessage());
    }
}
