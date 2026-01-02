package villagecompute.calendar.services;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import villagecompute.calendar.services.exceptions.StorageException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * Service for uploading files to Cloudflare R2 object storage. Uses AWS S3 SDK with S3-compatible API.
 */
@ApplicationScoped
public class StorageService {

    private static final Logger LOG = Logger.getLogger(StorageService.class);

    @ConfigProperty(
            name = "calendar.r2.endpoint")
    String r2Endpoint;

    @ConfigProperty(
            name = "calendar.r2.access-key")
    String r2AccessKey;

    @ConfigProperty(
            name = "calendar.r2.secret-key")
    String r2SecretKey;

    @ConfigProperty(
            name = "calendar.r2.bucket")
    String r2Bucket;

    @ConfigProperty(
            name = "calendar.r2.public-url")
    String publicUrlBase;

    private S3Client s3Client;

    /**
     * Get or create the S3 client for R2. Lazily initialized to avoid startup errors if credentials are not configured.
     * Uses synchronized method instead of double-checked locking for thread safety.
     *
     * @return S3Client configured for Cloudflare R2
     */
    private synchronized S3Client getS3Client() {
        if (s3Client == null) {
            LOG.infof("Initializing R2 S3 client with endpoint: %s", r2Endpoint);

            AwsBasicCredentials credentials = AwsBasicCredentials.create(r2AccessKey, r2SecretKey);

            s3Client = S3Client.builder().endpointOverride(URI.create(r2Endpoint)).region(Region.US_EAST_1) // R2 uses
                                                                                                            // 'auto'
                                                                                                            // region,
                                                                                                            // but SDK
                                                                                                            // requires
                                                                                                            // a
                    // region
                    .credentialsProvider(StaticCredentialsProvider.create(credentials)).build();

            LOG.info("R2 S3 client initialized successfully");
        }
        return s3Client;
    }

    /**
     * Upload a file to Cloudflare R2 and return the public URL.
     *
     * @param filename
     *            The filename to store (e.g., "calendar-2025-abc123.pdf")
     * @param fileBytes
     *            The file content as bytes
     * @param contentType
     *            The MIME type (e.g., "application/pdf")
     * @return The public URL of the uploaded file
     * @throws StorageException
     *             if upload fails
     */
    public String uploadFile(String filename, byte[] fileBytes, String contentType) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }

        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("File bytes cannot be null or empty");
        }

        try {
            LOG.infof("Uploading file to R2: %s (size: %d bytes)", filename, fileBytes.length);

            // Build the S3 key (path in bucket)
            String key = "calendar-pdfs/" + filename;

            // Create the PutObject request
            PutObjectRequest putRequest = PutObjectRequest.builder().bucket(r2Bucket).key(key).contentType(contentType)
                    .contentLength((long) fileBytes.length).build();

            // Upload the file
            S3Client client = getS3Client();
            PutObjectResponse response = client.putObject(putRequest, RequestBody.fromBytes(fileBytes));

            LOG.infof("File uploaded successfully to R2: %s (ETag: %s)", key, response.eTag());

            // Construct the public URL
            String publicUrl = publicUrlBase + "/" + key;

            LOG.infof("Public URL: %s", publicUrl);

            return publicUrl;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to upload file to R2: %s", filename);
            throw new StorageException("Failed to upload file to R2: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a file from R2 by its public URL. Extracts the key from the URL and deletes the object.
     *
     * @param publicUrl
     *            The public URL of the file to delete
     * @throws StorageException
     *             if deletion fails
     */
    public void deleteFile(String publicUrl) {
        if (publicUrl == null || publicUrl.isEmpty()) {
            throw new IllegalArgumentException("Public URL cannot be null or empty");
        }

        try {
            // Extract the key from the public URL
            String key = publicUrl.replace(publicUrlBase + "/", "");

            LOG.infof("Deleting file from R2: %s", key);

            S3Client client = getS3Client();
            client.deleteObject(builder -> builder.bucket(r2Bucket).key(key));

            LOG.infof("File deleted successfully from R2: %s", key);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to delete file from R2: %s", publicUrl);
            throw new StorageException("Failed to delete file from R2: " + e.getMessage(), e);
        }
    }
}
