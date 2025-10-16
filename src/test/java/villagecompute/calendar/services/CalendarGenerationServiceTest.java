package villagecompute.calendar.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.exceptions.CalendarGenerationException;
import villagecompute.calendar.services.exceptions.StorageException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CalendarGenerationService.
 * Tests PDF generation pipeline with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
public class CalendarGenerationServiceTest {

    @InjectMocks
    CalendarGenerationService calendarGenerationService;

    @Mock
    CalendarService calendarService;

    @Mock
    PDFRenderingService pdfRenderingService;

    @Mock
    StorageService storageService;

    @Mock
    AstronomicalCalculationService astronomicalService;

    ObjectMapper objectMapper = new ObjectMapper();

    private UserCalendar testUserCalendar;
    private CalendarTemplate testTemplate;
    private CalendarUser testUser;

    @BeforeEach
    public void setup() throws Exception {
        // Inject objectMapper into the service manually (since @InjectMocks doesn't handle @Inject)
        java.lang.reflect.Field objectMapperField = CalendarGenerationService.class.getDeclaredField("objectMapper");
        objectMapperField.setAccessible(true);
        objectMapperField.set(calendarGenerationService, objectMapper);

        // Create test user
        testUser = new CalendarUser();
        testUser.id = UUID.randomUUID();
        testUser.email = "test@example.com";

        // Create test template
        testTemplate = new CalendarTemplate();
        testTemplate.id = UUID.randomUUID();
        testTemplate.name = "Test Template";
        testTemplate.configuration = createTestConfiguration();

        // Create test user calendar
        testUserCalendar = new UserCalendar();
        testUserCalendar.id = UUID.randomUUID();
        testUserCalendar.user = testUser;
        testUserCalendar.year = 2025;
        testUserCalendar.name = "Test Calendar";
        testUserCalendar.template = testTemplate;
        testUserCalendar.configuration = createUserConfiguration();
    }

    private JsonNode createTestConfiguration() {
        try {
            String json = """
                {
                  "theme": "default",
                  "showMoonPhases": true,
                  "showGrid": true,
                  "layoutStyle": "grid"
                }
                """;
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode createUserConfiguration() {
        try {
            String json = """
                {
                  "showMoonIllumination": true,
                  "latitude": 44.4759,
                  "longitude": -73.2121
                }
                """;
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGenerateCalendar_HappyPath() {
        // Arrange
        String mockSvg = "<svg>test</svg>";
        byte[] mockPdf = new byte[]{1, 2, 3, 4, 5};
        String mockPublicUrl = "https://r2.villagecompute.com/calendar-pdfs/test.pdf";

        when(calendarService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(mockPdf);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString())).thenReturn(mockPublicUrl);

        // Act
        String result = calendarGenerationService.generateCalendar(testUserCalendar);

        // Assert
        assertNotNull(result);
        assertEquals(mockPublicUrl, result);
        assertEquals(mockPublicUrl, testUserCalendar.generatedPdfUrl);
        assertEquals(mockSvg, testUserCalendar.generatedSvg);

        // Verify interactions
        verify(calendarService, times(1)).generateCalendarSVG(any());
        verify(pdfRenderingService, times(1)).renderSVGToPDF(mockSvg, 2025);
        verify(storageService, times(1)).uploadFile(anyString(), eq(mockPdf), eq("application/pdf"));
    }

    @Test
    public void testGenerateCalendar_NullUserCalendar() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            calendarGenerationService.generateCalendar(null);
        });
    }

    @Test
    public void testGenerateCalendar_NullYear() {
        // Arrange
        testUserCalendar.year = null;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            calendarGenerationService.generateCalendar(testUserCalendar);
        });
    }

    @Test
    public void testGenerateCalendar_SVGGenerationFails() {
        // Arrange
        when(calendarService.generateCalendarSVG(any()))
                .thenThrow(new RuntimeException("SVG generation failed"));

        // Act & Assert
        assertThrows(CalendarGenerationException.class, () -> {
            calendarGenerationService.generateCalendar(testUserCalendar);
        });

        // Verify PDF and storage were never called
        verify(pdfRenderingService, never()).renderSVGToPDF(anyString(), anyInt());
        verify(storageService, never()).uploadFile(anyString(), any(byte[].class), anyString());
    }

    @Test
    public void testGenerateCalendar_PDFRenderingFails() {
        // Arrange
        String mockSvg = "<svg>test</svg>";
        when(calendarService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt()))
                .thenThrow(new RuntimeException("PDF rendering failed"));

        // Act & Assert
        assertThrows(CalendarGenerationException.class, () -> {
            calendarGenerationService.generateCalendar(testUserCalendar);
        });

        // Verify storage was never called
        verify(storageService, never()).uploadFile(anyString(), any(byte[].class), anyString());
    }

    @Test
    public void testGenerateCalendar_EmptyPDFOutput() {
        // Arrange
        String mockSvg = "<svg>test</svg>";
        byte[] emptyPdf = new byte[]{};

        when(calendarService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(emptyPdf);

        // Act & Assert
        assertThrows(CalendarGenerationException.class, () -> {
            calendarGenerationService.generateCalendar(testUserCalendar);
        });
    }

    @Test
    public void testGenerateCalendar_StorageUploadFails() {
        // Arrange
        String mockSvg = "<svg>test</svg>";
        byte[] mockPdf = new byte[]{1, 2, 3, 4, 5};

        when(calendarService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(mockPdf);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString()))
                .thenThrow(new StorageException("Upload failed"));

        // Act & Assert
        assertThrows(StorageException.class, () -> {
            calendarGenerationService.generateCalendar(testUserCalendar);
        });
    }

    @Test
    public void testGenerateCalendar_ConfigurationMerging() {
        // Arrange
        String mockSvg = "<svg>test</svg>";
        byte[] mockPdf = new byte[]{1, 2, 3, 4, 5};
        String mockPublicUrl = "https://r2.villagecompute.com/calendar-pdfs/test.pdf";

        when(calendarService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(mockPdf);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString())).thenReturn(mockPublicUrl);

        // Act
        calendarGenerationService.generateCalendar(testUserCalendar);

        // Capture the CalendarConfig passed to generateCalendarSVG
        ArgumentCaptor<CalendarService.CalendarConfig> configCaptor =
                ArgumentCaptor.forClass(CalendarService.CalendarConfig.class);
        verify(calendarService).generateCalendarSVG(configCaptor.capture());

        CalendarService.CalendarConfig capturedConfig = configCaptor.getValue();

        // Assert: Configuration should merge template + user settings
        assertEquals(2025, capturedConfig.year);
        assertEquals("default", capturedConfig.theme); // From template
        assertTrue(capturedConfig.showMoonPhases); // From template
        assertTrue(capturedConfig.showMoonIllumination); // From user override
        assertEquals(44.4759, capturedConfig.latitude, 0.001); // From user
        assertEquals(-73.2121, capturedConfig.longitude, 0.001); // From user
    }

    @Test
    public void testGenerateCalendar_WithSessionId() {
        // Arrange
        testUserCalendar.user = null;
        testUserCalendar.sessionId = "test-session-123";

        String mockSvg = "<svg>test</svg>";
        byte[] mockPdf = new byte[]{1, 2, 3, 4, 5};
        String mockPublicUrl = "https://r2.villagecompute.com/calendar-pdfs/test.pdf";

        when(calendarService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(mockPdf);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString())).thenReturn(mockPublicUrl);

        // Act
        String result = calendarGenerationService.generateCalendar(testUserCalendar);

        // Assert
        assertNotNull(result);
        assertEquals(mockPublicUrl, result);

        // Verify filename contains session ID
        ArgumentCaptor<String> filenameCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).uploadFile(filenameCaptor.capture(), any(byte[].class), anyString());
        assertTrue(filenameCaptor.getValue().contains("session"));
    }

    @Test
    public void testGenerateCalendar_WithMoonPhases() {
        // Arrange
        String moonConfigJson = """
            {
              "showMoonPhases": true,
              "moonSize": 32,
              "moonOffsetX": 40,
              "moonOffsetY": 40,
              "moonBorderColor": "#cccccc",
              "moonBorderWidth": 1.0
            }
            """;

        try {
            testUserCalendar.configuration = objectMapper.readTree(moonConfigJson);
        } catch (Exception e) {
            fail("Failed to parse JSON: " + e.getMessage());
        }

        String mockSvg = "<svg>test with moons</svg>";
        byte[] mockPdf = new byte[]{1, 2, 3, 4, 5};
        String mockPublicUrl = "https://r2.villagecompute.com/calendar-pdfs/test.pdf";

        when(calendarService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(mockPdf);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString())).thenReturn(mockPublicUrl);

        // Act
        String result = calendarGenerationService.generateCalendar(testUserCalendar);

        // Assert
        assertNotNull(result);

        // Verify moon configuration was applied
        ArgumentCaptor<CalendarService.CalendarConfig> configCaptor =
                ArgumentCaptor.forClass(CalendarService.CalendarConfig.class);
        verify(calendarService).generateCalendarSVG(configCaptor.capture());

        CalendarService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertTrue(capturedConfig.showMoonPhases);
        assertEquals(32, capturedConfig.moonSize);
        assertEquals(40, capturedConfig.moonOffsetX);
        assertEquals(40, capturedConfig.moonOffsetY);
        assertEquals("#cccccc", capturedConfig.moonBorderColor);
        assertEquals(1.0, capturedConfig.moonBorderWidth, 0.001);
    }
}
