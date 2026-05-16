package villagecompute.calendar.api.graphql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import villagecompute.calendar.services.AuthenticationService;

/**
 * Unit tests for PdfGraphQL. Tests both stub query and mutation methods with mocked dependencies. PDF rendering and R2
 * storage are not exercised here because the implementation is a stub.
 */
@ExtendWith(MockitoExtension.class)
class PdfGraphQLTest {

    @InjectMocks
    PdfGraphQL pdfGraphQL;

    @Mock
    JsonWebToken jwt;

    @Mock
    AuthenticationService authService;

    @Nested
    class PdfJobQueryTests {

        @Test
        void pdfJob_ValidUuid_ReturnsNullStub() {
            String jobId = UUID.randomUUID().toString();

            PdfGraphQL.PdfJobStub result = pdfGraphQL.pdfJob(jobId);

            assertNull(result, "Stub implementation must return null until PdfJob entity is wired up");
        }

        @Test
        void pdfJob_ArbitraryStringId_ReturnsNullStub() {
            PdfGraphQL.PdfJobStub result = pdfGraphQL.pdfJob("not-a-uuid-but-still-handled");

            assertNull(result);
        }

        @Test
        void pdfJob_EmptyStringId_ReturnsNullStub() {
            PdfGraphQL.PdfJobStub result = pdfGraphQL.pdfJob("");

            assertNull(result);
        }
    }

    @Nested
    class GeneratePdfMutationTests {

        @Test
        void generatePdf_WithWatermark_ThrowsUnsupportedOperation() {
            String calendarId = UUID.randomUUID().toString();

            UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                    () -> pdfGraphQL.generatePdf(calendarId, Boolean.TRUE));

            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("PDF generation not yet implemented"),
                    "Stub error message should explain that PDF generation is not implemented");
        }

        @Test
        void generatePdf_WithoutWatermark_ThrowsUnsupportedOperation() {
            String calendarId = UUID.randomUUID().toString();

            UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                    () -> pdfGraphQL.generatePdf(calendarId, Boolean.FALSE));

            assertTrue(ex.getMessage().contains("PDF rendering"),
                    "Stub error message should reference the future PDF rendering job");
        }

        @Test
        void generatePdf_EmptyCalendarId_StillThrowsUnsupportedOperation() {
            // Until the stub is replaced, every invocation should fail fast with the same exception type.
            assertThrows(UnsupportedOperationException.class, () -> pdfGraphQL.generatePdf("", Boolean.TRUE));
        }
    }

    @Nested
    class PdfJobStubTests {

        @Test
        void pdfJobStub_FieldsAreAssignable() {
            PdfGraphQL.PdfJobStub stub = new PdfGraphQL.PdfJobStub();
            stub.id = UUID.randomUUID().toString();
            stub.calendarId = UUID.randomUUID().toString();
            stub.status = "PENDING";
            stub.progress = 0;
            stub.attempts = 0;
            stub.includeWatermark = Boolean.FALSE;
            stub.pdfUrl = null;
            stub.errorMessage = null;
            stub.createdAt = "2026-04-29T00:00:00Z";
            stub.startedAt = null;
            stub.completedAt = null;

            assertEquals("PENDING", stub.status);
            assertEquals(0, stub.progress);
            assertEquals(0, stub.attempts);
            assertFalse(stub.includeWatermark);
            assertNull(stub.pdfUrl);
            assertNull(stub.errorMessage);
            assertEquals("2026-04-29T00:00:00Z", stub.createdAt);
            assertNull(stub.startedAt);
            assertNull(stub.completedAt);
        }

        @Test
        void pdfJobStub_CompletedJob_HoldsUrl() {
            PdfGraphQL.PdfJobStub stub = new PdfGraphQL.PdfJobStub();
            stub.id = UUID.randomUUID().toString();
            stub.calendarId = UUID.randomUUID().toString();
            stub.status = "COMPLETED";
            stub.progress = 100;
            stub.attempts = 1;
            stub.includeWatermark = Boolean.TRUE;
            stub.pdfUrl = "https://r2.example/calendars/foo.pdf";
            stub.completedAt = "2026-04-29T00:05:00Z";

            assertEquals("COMPLETED", stub.status);
            assertEquals(100, stub.progress);
            assertTrue(stub.includeWatermark);
            assertEquals("https://r2.example/calendars/foo.pdf", stub.pdfUrl);
        }

        @Test
        void pdfJobStub_FailedJob_HoldsErrorMessage() {
            PdfGraphQL.PdfJobStub stub = new PdfGraphQL.PdfJobStub();
            stub.id = UUID.randomUUID().toString();
            stub.calendarId = UUID.randomUUID().toString();
            stub.status = "FAILED";
            stub.progress = 42;
            stub.attempts = 3;
            stub.includeWatermark = Boolean.FALSE;
            stub.errorMessage = "Rendering failed";
            stub.completedAt = "2026-04-29T00:05:00Z";

            assertEquals("FAILED", stub.status);
            assertEquals(3, stub.attempts);
            assertEquals("Rendering failed", stub.errorMessage);
            assertNull(stub.pdfUrl);
        }
    }
}
