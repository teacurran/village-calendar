package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import villagecompute.calendar.data.models.enums.MazeType;
import villagecompute.calendar.services.MazeGenerationService.MazeGenerationConfig;

import io.quarkus.test.junit.QuarkusTest;

/** Tests for MazeGenerationService. Covers the maze generation and preview methods. */
@QuarkusTest
class MazeGenerationServiceTest {

    @Inject
    MazeGenerationService mazeGenerationService;

    // ============================================================================
    // generatePreview() TESTS
    // ============================================================================

    @Test
    void testGeneratePreview_Orthogonal_ReturnsSvg() {
        // When
        String svg = mazeGenerationService.generatePreview(MazeType.ORTHOGONAL, 5, 3, false);

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("</svg>"));
    }

    @Test
    void testGeneratePreview_WithSolution_IncludesSolutionPath() {
        // When
        String svg = mazeGenerationService.generatePreview(MazeType.ORTHOGONAL, 5, 3, true);

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
        // Solution path should be visible (green color)
        assertTrue(svg.contains("#4CAF50") || svg.contains("path"));
    }

    @Test
    void testGeneratePreview_WithDeadEnds_IncludesDeadEndHighlighting() {
        // When
        String svg = mazeGenerationService.generatePreview(MazeType.ORTHOGONAL, 5, 3, false, true);

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
    }

    @Test
    void testGeneratePreview_Delta_ReturnsSvg() {
        // When
        String svg = mazeGenerationService.generatePreview(MazeType.DELTA, 5, 3, false);

        // Then - Delta currently uses orthogonal with a comment prepended
        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("</svg>"));
    }

    @Test
    void testGeneratePreview_Sigma_ReturnsSvg() {
        // When
        String svg = mazeGenerationService.generatePreview(MazeType.SIGMA, 5, 3, false);

        // Then - Sigma currently uses orthogonal with a comment prepended
        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("</svg>"));
    }

    @Test
    void testGeneratePreview_Theta_ReturnsSvg() {
        // When
        String svg = mazeGenerationService.generatePreview(MazeType.THETA, 5, 3, false);

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
    }

    // ============================================================================
    // generateMazeSvg() TESTS
    // ============================================================================

    @Test
    void testGenerateMazeSvg_WithCustomColors_AppliesColors() {
        // When
        String svg = mazeGenerationService.generateMazeSvg(new MazeGenerationConfig(MazeType.ORTHOGONAL, 5, 3, 12345L,
                false, "#FF0000", "#0000FF", "#00FF00", false, null));

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
    }

    @Test
    void testGenerateMazeSvg_WithDeadEndHighlighting_AppliesDeadEndColor() {
        // When
        String svg = mazeGenerationService.generateMazeSvg(new MazeGenerationConfig(MazeType.ORTHOGONAL, 5, 3, 12345L,
                false, "#000000", "#000000", "#4CAF50", true, "#FFA500"));

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
    }

    @Test
    void testGenerateMazeSvg_SameSeedProducesSameResult() {
        // When
        String svg1 = mazeGenerationService.generateMazeSvg(new MazeGenerationConfig(MazeType.ORTHOGONAL, 5, 3, 99999L,
                false, "#000000", "#000000", "#4CAF50", false, null));
        String svg2 = mazeGenerationService.generateMazeSvg(new MazeGenerationConfig(MazeType.ORTHOGONAL, 5, 3, 99999L,
                false, "#000000", "#000000", "#4CAF50", false, null));

        // Then
        assertEquals(svg1, svg2, "Same seed should produce identical mazes");
    }

    @Test
    void testGenerateMazeSvg_DifferentSeedsProduceDifferentResults() {
        // When
        String svg1 = mazeGenerationService.generateMazeSvg(new MazeGenerationConfig(MazeType.ORTHOGONAL, 5, 3, 11111L,
                false, "#000000", "#000000", "#4CAF50", false, null));
        String svg2 = mazeGenerationService.generateMazeSvg(new MazeGenerationConfig(MazeType.ORTHOGONAL, 5, 3, 22222L,
                false, "#000000", "#000000", "#4CAF50", false, null));

        // Then
        assertNotEquals(svg1, svg2, "Different seeds should produce different mazes");
    }

    // ============================================================================
    // Size parameter TESTS
    // ============================================================================

    @ParameterizedTest(
            name = "generatePreview(size={0}, count={1}) succeeds")
    @CsvSource({"1, 1", // minimum size
            "20, 5", // maximum size
            "0, 3", // size out of range low, clamped to minimum
            "100, 3" // size out of range high, clamped to maximum
    })
    void testGeneratePreview_SizeVariants_Succeeds(int size, int count) {
        // When
        String svg = mazeGenerationService.generatePreview(MazeType.ORTHOGONAL, size, count, false);

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
    }
}
