package villagecompute.calendar.util;

import jakarta.ws.rs.core.MediaType;

/**
 * MIME type constants for the application.
 *
 * <p>
 * Extends jakarta.ws.rs.core.MediaType with image and document types not included in the standard JAX-RS API.
 */
public final class MimeTypes {

    private MimeTypes() {
    }

    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_CACHE_CONTROL = "Cache-Control";
    public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    public static final String HEADER_X_SESSION_ID = "X-Session-ID";
    public static final String HEADER_STRIPE_SIGNATURE = "Stripe-Signature";

    // Re-export common JAX-RS types for convenience
    public static final String APPLICATION_JSON = MediaType.APPLICATION_JSON;
    public static final String TEXT_HTML = MediaType.TEXT_HTML;
    public static final String TEXT_PLAIN = MediaType.TEXT_PLAIN;

    // Image types
    public static final String IMAGE_PNG = "image/png";
    public static final String IMAGE_JPEG = "image/jpeg";
    public static final String IMAGE_SVG = "image/svg+xml";
    public static final String IMAGE_WEBP = "image/webp";
    public static final String IMAGE_GIF = "image/gif";

    // Document types
    public static final String APPLICATION_PDF = "application/pdf";

    // Other
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
}
