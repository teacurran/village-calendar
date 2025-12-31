package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.exceptions.CalendarGenerationException;
import villagecompute.calendar.services.exceptions.StorageException;

/**
 * Unit tests for CalendarGenerationService. Tests PDF generation pipeline with mocked dependencies.
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
                      "moonDisplayMode": "phases",
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
                      "moonDisplayMode": "illumination",
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
    void testGenerateCalendar_HappyPath() {
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
    void testGenerateCalendar_NullUserCalendar() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            calendarGenerationService.generateCalendar(null);
        });
    }

    @Test
    void testGenerateCalendar_NullYear() {
        // Arrange
        testUserCalendar.year = null;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            calendarGenerationService.generateCalendar(testUserCalendar);
        });
    }

    @Test
    void testGenerateCalendar_SVGGenerationFails() {
        // Arrange
        when(calendarRenderingService.generateCalendarSVG(any()))
                .thenThrow(new RuntimeException("SVG generation failed"));

        // Act & Assert
        assertThrows(
                CalendarGenerationException.class,
                () -> {
                    calendarGenerationService.generateCalendar(testUserCalendar);
                });

        // Verify PDF and storage were never called
        verify(pdfRenderingService, never()).renderSVGToPDF(anyString(), anyInt());
        verify(storageService, never()).uploadFile(anyString(), any(byte[].class), anyString());
    }

    @Test
    void testGenerateCalendar_PDFRenderingFails() {
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
    void testGenerateCalendar_EmptyPDFOutput() {
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
    void testGenerateCalendar_StorageUploadFails() {
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
    void testGenerateCalendar_ConfigurationMerging() {
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
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();

        // Assert: Configuration should merge template + user settings
        assertEquals(2025, capturedConfig.year);
        assertEquals("default", capturedConfig.theme); // From template
        assertEquals("illumination", capturedConfig.moonDisplayMode); // From user override (overrides template's
                                                                      // "phases")
        assertEquals(44.4759, capturedConfig.latitude, 0.001); // From user
        assertEquals(-73.2121, capturedConfig.longitude, 0.001); // From user
    }

    @Test
    void testGenerateCalendar_WithSessionId() {
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
    void testGenerateCalendar_WithMoonPhases() {
        // Arrange
        String moonConfigJson = """
                {
                  "moonDisplayMode": "phases",
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
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertEquals("phases", capturedConfig.moonDisplayMode);
        assertEquals(32, capturedConfig.moonSize);
        assertEquals(40, capturedConfig.moonOffsetX);
        assertEquals(40, capturedConfig.moonOffsetY);
        assertEquals("#cccccc", capturedConfig.moonBorderColor);
        assertEquals(1.0, capturedConfig.moonBorderWidth, 0.001);
    }

    @Test
    void testGenerateCalendar_MoonDisplayModeIllumination() {
        // Arrange: Config has moonDisplayMode="illumination"
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

        // Assert: moonDisplayMode should be propagated correctly
        assertNotNull(result);

        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertEquals("illumination", capturedConfig.moonDisplayMode, "moonDisplayMode should be 'illumination'");
    }

    @Test
    void testGenerateCalendar_MoonDisplayModePhases() {
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

        // Assert: moonDisplayMode should be propagated correctly
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertEquals("phases", capturedConfig.moonDisplayMode, "moonDisplayMode should be 'phases'");
    }

    @Test
    void testGenerateCalendar_MoonDisplayModeFullOnly() {
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

        // Assert: moonDisplayMode should be propagated correctly
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertEquals("full-only", capturedConfig.moonDisplayMode, "moonDisplayMode should be 'full-only'");
    }

    @Test
    void testGenerateCalendar_MoonDisplayModeIgnoresLegacyBooleans() {
        // Arrange: Config has BOTH moonDisplayMode and legacy booleans
        // Only moonDisplayMode is used; legacy booleans are ignored
        String configJson = """
                {
                  "moonDisplayMode": "phases",
                  "showMoonIllumination": true,
                  "showMoonPhases": false,
                  "showFullMoonOnly": false
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

        // Assert: moonDisplayMode is used (legacy booleans are ignored)
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertEquals("phases", capturedConfig.moonDisplayMode,
                "moonDisplayMode should be 'phases' (legacy booleans are ignored)");
    }

    @Test
    void testGenerateCalendar_MoonDisplayModeNone() {
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

        // Assert: moonDisplayMode should be "none" (no moons displayed)
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertEquals("none", capturedConfig.moonDisplayMode, "moonDisplayMode should be 'none'");
    }

    // ============================================================================
    // Holiday Configuration Tests
    // ============================================================================

    @Test
    void testGenerateCalendar_WithHolidaySets() {
        // Arrange: Config has holidaySets
        String holidayConfigJson = """
                {
                  "holidaySets": ["us-federal", "us-christian"]
                }
                """;

        try {
            testUserCalendar.configuration = objectMapper.readTree(holidayConfigJson);
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

        // Assert: holidaySets should be parsed correctly
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertEquals(2, capturedConfig.holidaySets.size());
        assertTrue(capturedConfig.holidaySets.contains("us-federal"));
        assertTrue(capturedConfig.holidaySets.contains("us-christian"));
    }

    @Test
    void testGenerateCalendar_WithEmptyHolidaySets() {
        // Arrange: Empty holidaySets should clear any defaults
        String holidayConfigJson = """
                {
                  "holidaySets": []
                }
                """;

        try {
            testUserCalendar.configuration = objectMapper.readTree(holidayConfigJson);
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

        // Assert: holidaySets should be empty
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertTrue(capturedConfig.holidaySets.isEmpty());
    }

    @Test
    void testGenerateCalendar_WithHolidayEmojis() {
        // Arrange: Config has holidayEmojis map
        String holidayConfigJson = """
                {
                  "holidayEmojis": {
                    "2025-01-01": "🎉",
                    "2025-12-25": "🎄"
                  }
                }
                """;

        try {
            testUserCalendar.configuration = objectMapper.readTree(holidayConfigJson);
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

        // Assert: holidayEmojis should be parsed correctly
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertEquals(2, capturedConfig.holidayEmojis.size());
        assertEquals("🎉", capturedConfig.holidayEmojis.get("2025-01-01"));
        assertEquals("🎄", capturedConfig.holidayEmojis.get("2025-12-25"));
    }

    @Test
    void testGenerateCalendar_WithHolidayNames() {
        // Arrange: Config has holidayNames map
        String holidayConfigJson = """
                {
                  "holidayNames": {
                    "2025-01-01": "New Year's Day",
                    "2025-07-04": "Independence Day"
                  }
                }
                """;

        try {
            testUserCalendar.configuration = objectMapper.readTree(holidayConfigJson);
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

        // Assert: holidayNames should be parsed correctly
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertEquals(2, capturedConfig.holidayNames.size());
        assertEquals("New Year's Day", capturedConfig.holidayNames.get("2025-01-01"));
        assertEquals("Independence Day", capturedConfig.holidayNames.get("2025-07-04"));
    }

    @Test
    void testGenerateCalendar_WithAllHolidayConfiguration() {
        // Arrange: Config has all holiday-related fields
        String holidayConfigJson = """
                {
                  "holidaySets": ["us-federal"],
                  "holidayEmojis": {
                    "2025-01-01": "🎉"
                  },
                  "holidayNames": {
                    "2025-01-01": "New Year's Day"
                  }
                }
                """;

        try {
            testUserCalendar.configuration = objectMapper.readTree(holidayConfigJson);
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

        // Assert: All holiday config should be parsed correctly
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertEquals(1, capturedConfig.holidaySets.size());
        assertTrue(capturedConfig.holidaySets.contains("us-federal"));
        assertEquals("🎉", capturedConfig.holidayEmojis.get("2025-01-01"));
        assertEquals("New Year's Day", capturedConfig.holidayNames.get("2025-01-01"));
    }

    @Test
    void testGenerateCalendar_UserHolidayConfigOverridesTemplate() {
        // Arrange: Template has holidays, user provides empty to override
        String templateConfigJson = """
                {
                  "holidaySets": ["us-federal", "us-christian"],
                  "holidayEmojis": {
                    "2025-01-01": "🎆"
                  }
                }
                """;

        String userConfigJson = """
                {
                  "holidaySets": [],
                  "holidayEmojis": {}
                }
                """;

        try {
            testTemplate.configuration = objectMapper.readTree(templateConfigJson);
            testUserCalendar.configuration = objectMapper.readTree(userConfigJson);
            testUserCalendar.template = testTemplate;
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

        // Assert: User's empty config should override template
        ArgumentCaptor<CalendarRenderingService.CalendarConfig> configCaptor = ArgumentCaptor
                .forClass(CalendarRenderingService.CalendarConfig.class);
        verify(calendarRenderingService).generateCalendarSVG(configCaptor.capture());

        CalendarRenderingService.CalendarConfig capturedConfig = configCaptor.getValue();
        assertTrue(capturedConfig.holidaySets.isEmpty(), "User's empty holidaySets should override template");
        assertTrue(capturedConfig.holidayEmojis.isEmpty(), "User's empty holidayEmojis should override template");
    }
}
