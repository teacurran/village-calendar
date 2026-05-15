package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import villagecompute.calendar.services.exceptions.StorageException;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * Unit tests for StorageService. Mocks the AWS S3Client to verify upload and delete behavior without hitting a real R2
 * endpoint, covering both validation paths and success/failure flows for S3 calls.
 */
class StorageServiceTest {

    private static final String VALID_FILENAME = "test-file.pdf";
    private static final String VALID_CONTENT_TYPE = "application/pdf";
    private static final String BUCKET = "test-bucket";
    private static final String PUBLIC_URL_BASE = "https://cdn.example.com";
    private static final String ENDPOINT = "https://example.r2.cloudflarestorage.com";
    private static final String ACCESS_KEY = "test-access";
    private static final String SECRET_KEY = "test-secret";

    private StorageService storageService;
    private S3Client mockS3Client;

    @BeforeEach
    void setUp() throws Exception {
        storageService = new StorageService();
        setField(storageService, "r2Endpoint", ENDPOINT);
        setField(storageService, "r2AccessKey", ACCESS_KEY);
        setField(storageService, "r2SecretKey", SECRET_KEY);
        setField(storageService, "r2Bucket", BUCKET);
        setField(storageService, "publicUrlBase", PUBLIC_URL_BASE);

        mockS3Client = mock(S3Client.class);
        setField(storageService, "s3Client", mockS3Client);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = StorageService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ========== UPLOAD FILE VALIDATION TESTS ==========

    @Test
    void testUploadFile_NullFilename_ThrowsIllegalArgumentException() {
        byte[] fileBytes = new byte[]{1, 2, 3};

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storageService.uploadFile(null, fileBytes, VALID_CONTENT_TYPE));

        assertEquals("Filename cannot be null or empty", exception.getMessage());
        verifyNoInteractions(mockS3Client);
    }

    @Test
    void testUploadFile_EmptyFilename_ThrowsIllegalArgumentException() {
        byte[] fileBytes = new byte[]{1, 2, 3};

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storageService.uploadFile("", fileBytes, VALID_CONTENT_TYPE));

        assertEquals("Filename cannot be null or empty", exception.getMessage());
        verifyNoInteractions(mockS3Client);
    }

    @Test
    void testUploadFile_NullFileBytes_ThrowsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storageService.uploadFile(VALID_FILENAME, null, VALID_CONTENT_TYPE));

        assertEquals("File bytes cannot be null or empty", exception.getMessage());
        verifyNoInteractions(mockS3Client);
    }

    @Test
    void testUploadFile_EmptyFileBytes_ThrowsIllegalArgumentException() {
        byte[] emptyBytes = new byte[0];

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storageService.uploadFile(VALID_FILENAME, emptyBytes, VALID_CONTENT_TYPE));

        assertEquals("File bytes cannot be null or empty", exception.getMessage());
        verifyNoInteractions(mockS3Client);
    }

    // ========== DELETE FILE VALIDATION TESTS ==========

    @Test
    void testDeleteFile_NullPublicUrl_ThrowsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storageService.deleteFile(null));

        assertEquals("Public URL cannot be null or empty", exception.getMessage());
        verifyNoInteractions(mockS3Client);
    }

    @Test
    void testDeleteFile_EmptyPublicUrl_ThrowsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storageService.deleteFile(""));

        assertEquals("Public URL cannot be null or empty", exception.getMessage());
        verifyNoInteractions(mockS3Client);
    }

    // ========== UPLOAD FILE SUCCESS / ERROR (MOCKED S3) ==========

    @Test
    void testUploadFile_Success_ReturnsPublicUrl() {
        byte[] fileBytes = new byte[]{10, 20, 30, 40, 50};
        PutObjectResponse mockResponse = PutObjectResponse.builder().eTag("\"abc123\"").build();
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(mockResponse);

        String result = storageService.uploadFile(VALID_FILENAME, fileBytes, VALID_CONTENT_TYPE);

        assertEquals(PUBLIC_URL_BASE + "/calendar-pdfs/" + VALID_FILENAME, result);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest captured = requestCaptor.getValue();
        assertEquals(BUCKET, captured.bucket());
        assertEquals("calendar-pdfs/" + VALID_FILENAME, captured.key());
        assertEquals(VALID_CONTENT_TYPE, captured.contentType());
        assertEquals(Long.valueOf(fileBytes.length), captured.contentLength());
    }

    @Test
    void testUploadFile_S3Exception_WrappedInStorageException() {
        byte[] fileBytes = new byte[]{1, 2, 3};
        AwsServiceException s3Error = AwsServiceException.builder().message("Access Denied").build();
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(s3Error);

        StorageException ex = assertThrows(StorageException.class,
                () -> storageService.uploadFile(VALID_FILENAME, fileBytes, VALID_CONTENT_TYPE));

        assertTrue(ex.getMessage().startsWith("Failed to upload file to R2:"));
        assertSame(s3Error, ex.getCause());
    }

    @Test
    void testUploadFile_GenericException_WrappedInStorageException() {
        byte[] fileBytes = new byte[]{1};
        RuntimeException boom = new RuntimeException("boom");
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(boom);

        StorageException ex = assertThrows(StorageException.class,
                () -> storageService.uploadFile(VALID_FILENAME, fileBytes, VALID_CONTENT_TYPE));

        assertTrue(ex.getMessage().contains("boom"));
        assertSame(boom, ex.getCause());
    }

    // ========== DELETE FILE SUCCESS / ERROR (MOCKED S3) ==========

    @Test
    @SuppressWarnings("unchecked")
    void testDeleteFile_Success_CallsS3DeleteWithExtractedKey() {
        String filename = "calendar-2025-abc123.pdf";
        String publicUrl = PUBLIC_URL_BASE + "/calendar-pdfs/" + filename;

        DeleteObjectResponse mockResponse = DeleteObjectResponse.builder().build();
        when(mockS3Client.deleteObject(any(Consumer.class))).thenReturn(mockResponse);

        assertDoesNotThrow(() -> storageService.deleteFile(publicUrl));

        ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockS3Client).deleteObject(consumerCaptor.capture());

        // Apply the captured consumer to a real builder to verify it populates bucket/key correctly.
        DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
        consumerCaptor.getValue().accept(builder);
        DeleteObjectRequest built = builder.build();
        assertEquals(BUCKET, built.bucket());
        assertEquals("calendar-pdfs/" + filename, built.key());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDeleteFile_S3Exception_WrappedInStorageException() {
        String publicUrl = PUBLIC_URL_BASE + "/calendar-pdfs/foo.pdf";
        AwsServiceException s3Error = AwsServiceException.builder().message("NoSuchKey").build();
        when(mockS3Client.deleteObject(any(Consumer.class))).thenThrow(s3Error);

        StorageException ex = assertThrows(StorageException.class, () -> storageService.deleteFile(publicUrl));

        assertTrue(ex.getMessage().startsWith("Failed to delete file from R2:"));
        assertSame(s3Error, ex.getCause());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDeleteFile_GenericException_WrappedInStorageException() {
        String publicUrl = PUBLIC_URL_BASE + "/calendar-pdfs/foo.pdf";
        RuntimeException boom = new RuntimeException("kaboom");
        when(mockS3Client.deleteObject(any(Consumer.class))).thenThrow(boom);

        StorageException ex = assertThrows(StorageException.class, () -> storageService.deleteFile(publicUrl));

        assertTrue(ex.getMessage().contains("kaboom"));
        assertSame(boom, ex.getCause());
    }

    // ========== LAZY S3 CLIENT INITIALIZATION ==========

    @Test
    void testS3ClientLazyInit_CreatedOnFirstUseWhenUnset() throws Exception {
        // Reset s3Client to null so getS3Client() must construct it.
        setField(storageService, "s3Client", null);

        // Trigger a validation-passing call that will then attempt to construct the client and call putObject.
        // The construction should succeed (URI/region/credentials are all valid); the network call would fail
        // if it actually went out, but we only need to confirm the lazy init code path executes. We use a
        // try/catch to absorb the eventual S3 SDK exception.
        try {
            storageService.uploadFile(VALID_FILENAME, new byte[]{1, 2, 3}, VALID_CONTENT_TYPE);
        } catch (StorageException expected) {
            // Expected - the real S3Client will fail to reach the fake endpoint; we just want to prove
            // the lazy init branch was exercised.
        }

        Field field = StorageService.class.getDeclaredField("s3Client");
        field.setAccessible(true);
        assertNotNull(field.get(storageService), "s3Client should be lazily initialized on first use");
    }
}
