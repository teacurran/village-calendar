package villagecompute.calendar.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for loading classpath resources. Consolidates duplicated resource loading patterns across job handlers.
 */
public final class ResourceLoader {

    private ResourceLoader() {
        // Utility class
    }

    /**
     * Load a classpath resource file as a UTF-8 string.
     *
     * @param resourcePath
     *            Resource path relative to classpath (e.g., "css/email.css")
     * @return File contents as string
     * @throws IOException
     *             if resource cannot be found or read
     */
    public static String loadAsString(String resourcePath) throws IOException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
