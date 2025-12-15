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
    CalendarRenderingService calendarRenderingService;

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

        when(calendarRenderingService.generateCalendarSVG(any())).thenReturn(mockSvg);
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
        verify(calendarRenderingService, times(1)).generateCalendarSVG(any());
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
        when(calendarRenderingService.generateCalendarSVG(any()))
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
        when(calendarRenderingService.generateCalendarSVG(any())).thenReturn(mockSvg);
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

        when(calendarRenderingService.generateCalendarSVG(any())).thenReturn(mockSvg);
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

        when(calendarRenderingService.generateCalendarSVG(any())).thenReturn(mockSvg);
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

        when(calendarRenderingService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(mockPdf);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString())).thenReturn(mockPublicUrl);

        // Act
        calendarGenerationService.generateCalendar(testUserCalendar);

        // Capture the CalendarConfig passed to generateCalendarSVG
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor =
                ArgumentCaptor.forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();

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

        when(calendarRenderingService.generateCalendarSVG(any())).thenReturn(mockSvg);
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

        when(calendarRenderingService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(mockPdf);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString())).thenReturn(mockPublicUrl);

        // Act
        String result = calendarGenerationService.generateCalendar(testUserCalendar);

        // Assert
        assertNotNull(result);

        // Verify moon configuration was applied
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor =
                ArgumentCaptor.forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertTrue(capturedConfig.showMoonPhases);
        assertEquals(32, capturedConfig.moonSize);
        assertEquals(40, capturedConfig.moonOffsetX);
        assertEquals(40, capturedConfig.moonOffsetY);
        assertEquals("#cccccc", capturedConfig.moonBorderColor);
        assertEquals(1.0, capturedConfig.moonBorderWidth, 0.001);
    }

    @Test
    public void testGenerateCalendar_MoonDisplayModeIllumination_DerivesBoolean() {
        // Arrange: Config has moonDisplayMode but NOT explicit showMoonIllumination
        String moonDisplayModeJson = """
            {
              "moonDisplayMode": "illumination",
              "moonSize": 20,
              "latitude": 44.2172,
              "longitude": -72.2011
            }
            """;

        try {
            testUserCalendar.configuration = objectMapper.readTree(moonDisplayModeJson);
            testUserCalendar.template = null; // No template to avoid merging
        } catch (Exception e) {
            fail("Failed to parse JSON: " + e.getMessage());
        }

        String mockSvg = "<svg>test</svg>";
        byte[] mockPdf = new byte[]{1, 2, 3, 4, 5};
        String mockPublicUrl = "https://r2.villagecompute.com/calendar-pdfs/test.pdf";

        when(calendarRenderingService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(mockPdf);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString())).thenReturn(mockPublicUrl);

        // Act
        String result = calendarGenerationService.generateCalendar(testUserCalendar);

        // Assert: moonDisplayMode="illumination" should derive showMoonIllumination=true
        assertNotNull(result);

        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor =
                ArgumentCaptor.forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertTrue(capturedConfig.showMoonIllumination,
                "showMoonIllumination should be derived from moonDisplayMode='illumination'");
        assertFalse(capturedConfig.showMoonPhases,
                "showMoonPhases should be false when moonDisplayMode='illumination'");
        assertFalse(capturedConfig.showFullMoonOnly,
                "showFullMoonOnly should be false when moonDisplayMode='illumination'");
    }

    @Test
    public void testGenerateCalendar_MoonDisplayModePhases_DerivesBoolean() {
        // Arrange: Config has moonDisplayMode="phases"
        String moonDisplayModeJson = """
            {
              "moonDisplayMode": "phases",
              "moonSize": 15
            }
            """;

        try {
            testUserCalendar.configuration = objectMapper.readTree(moonDisplayModeJson);
            testUserCalendar.template = null;
        } catch (Exception e) {
            fail("Failed to parse JSON: " + e.getMessage());
        }

        String mockSvg = "<svg>test</svg>";
        byte[] mockPdf = new byte[]{1, 2, 3, 4, 5};
        String mockPublicUrl = "https://r2.villagecompute.com/calendar-pdfs/test.pdf";

        when(calendarRenderingService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(mockPdf);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString())).thenReturn(mockPublicUrl);

        // Act
        calendarGenerationService.generateCalendar(testUserCalendar);

        // Assert: moonDisplayMode="phases" should derive showMoonPhases=true
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor =
                ArgumentCaptor.forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertTrue(capturedConfig.showMoonPhases,
                "showMoonPhases should be derived from moonDisplayMode='phases'");
        assertFalse(capturedConfig.showMoonIllumination,
                "showMoonIllumination should be false when moonDisplayMode='phases'");
    }

    @Test
    public void testGenerateCalendar_MoonDisplayModeFullOnly_DerivesBoolean() {
        // Arrange: Config has moonDisplayMode="full-only"
        String moonDisplayModeJson = """
            {
              "moonDisplayMode": "full-only"
            }
            """;

        try {
            testUserCalendar.configuration = objectMapper.readTree(moonDisplayModeJson);
            testUserCalendar.template = null;
        } catch (Exception e) {
            fail("Failed to parse JSON: " + e.getMessage());
        }

        String mockSvg = "<svg>test</svg>";
        byte[] mockPdf = new byte[]{1, 2, 3, 4, 5};
        String mockPublicUrl = "https://r2.villagecompute.com/calendar-pdfs/test.pdf";

        when(calendarRenderingService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(mockPdf);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString())).thenReturn(mockPublicUrl);

        // Act
        calendarGenerationService.generateCalendar(testUserCalendar);

        // Assert: moonDisplayMode="full-only" should derive showFullMoonOnly=true
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor =
                ArgumentCaptor.forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertTrue(capturedConfig.showFullMoonOnly,
                "showFullMoonOnly should be derived from moonDisplayMode='full-only'");
    }

    @Test
    public void testGenerateCalendar_ExplicitBooleanOverridesMoonDisplayMode() {
        // Arrange: Config has BOTH moonDisplayMode and explicit boolean
        // The explicit boolean should be used, not derived from moonDisplayMode
        String configJson = """
            {
              "moonDisplayMode": "phases",
              "showMoonIllumination": true
            }
            """;

        try {
            testUserCalendar.configuration = objectMapper.readTree(configJson);
            testUserCalendar.template = null;
        } catch (Exception e) {
            fail("Failed to parse JSON: " + e.getMessage());
        }

        String mockSvg = "<svg>test</svg>";
        byte[] mockPdf = new byte[]{1, 2, 3, 4, 5};
        String mockPublicUrl = "https://r2.villagecompute.com/calendar-pdfs/test.pdf";

        when(calendarRenderingService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(mockPdf);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString())).thenReturn(mockPublicUrl);

        // Act
        calendarGenerationService.generateCalendar(testUserCalendar);

        // Assert: explicit showMoonIllumination should be used, derivation should be skipped
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor =
                ArgumentCaptor.forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertTrue(capturedConfig.showMoonIllumination,
                "Explicit showMoonIllumination=true should be used");
        assertFalse(capturedConfig.showMoonPhases,
                "showMoonPhases should NOT be derived when explicit boolean exists");
    }

    @Test
    public void testGenerateCalendar_MoonDisplayModeNone_NoMoons() {
        // Arrange: Config has moonDisplayMode="none"
        String moonDisplayModeJson = """
            {
              "moonDisplayMode": "none"
            }
            """;

        try {
            testUserCalendar.configuration = objectMapper.readTree(moonDisplayModeJson);
            testUserCalendar.template = null;
        } catch (Exception e) {
            fail("Failed to parse JSON: " + e.getMessage());
        }

        String mockSvg = "<svg>test</svg>";
        byte[] mockPdf = new byte[]{1, 2, 3, 4, 5};
        String mockPublicUrl = "https://r2.villagecompute.com/calendar-pdfs/test.pdf";

        when(calendarRenderingService.generateCalendarSVG(any())).thenReturn(mockSvg);
        when(pdfRenderingService.renderSVGToPDF(anyString(), anyInt())).thenReturn(mockPdf);
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString())).thenReturn(mockPublicUrl);

        // Act
        calendarGenerationService.generateCalendar(testUserCalendar);

        // Assert: moonDisplayMode="none" means all moon booleans should be false
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor =
                ArgumentCaptor.forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertFalse(capturedConfig.showMoonPhases,
                "showMoonPhases should be false when moonDisplayMode='none'");
        assertFalse(capturedConfig.showMoonIllumination,
                "showMoonIllumination should be false when moonDisplayMode='none'");
        assertFalse(capturedConfig.showFullMoonOnly,
                "showFullMoonOnly should be false when moonDisplayMode='none'");
    }
}
