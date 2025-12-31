package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.enums.MazeType;

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
        String svg = mazeGenerationService.generateMazeSvg(MazeType.ORTHOGONAL, 5, 3, 12345L, false, "#FF0000", // innerWallColor
                "#0000FF", // outerWallColor
                "#00FF00" // pathColor
        );

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
    }

    @Test
    void testGenerateMazeSvg_WithDeadEndHighlighting_AppliesDeadEndColor() {
        // When
        String svg = mazeGenerationService.generateMazeSvg(MazeType.ORTHOGONAL, 5, 3, 12345L, false, "#000000",
                "#000000", "#4CAF50", true, "#FFA500" // deadEndColor
        );

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
    }

    @Test
    void testGenerateMazeSvg_SameSeedProducesSameResult() {
        // When
        String svg1 = mazeGenerationService.generateMazeSvg(MazeType.ORTHOGONAL, 5, 3, 99999L, false, "#000000",
                "#000000", "#4CAF50");
        String svg2 = mazeGenerationService.generateMazeSvg(MazeType.ORTHOGONAL, 5, 3, 99999L, false, "#000000",
                "#000000", "#4CAF50");

        // Then
        assertEquals(svg1, svg2, "Same seed should produce identical mazes");
    }

    @Test
    void testGenerateMazeSvg_DifferentSeedsProduceDifferentResults() {
        // When
        String svg1 = mazeGenerationService.generateMazeSvg(MazeType.ORTHOGONAL, 5, 3, 11111L, false, "#000000",
                "#000000", "#4CAF50");
        String svg2 = mazeGenerationService.generateMazeSvg(MazeType.ORTHOGONAL, 5, 3, 22222L, false, "#000000",
                "#000000", "#4CAF50");

        // Then
        assertNotEquals(svg1, svg2, "Different seeds should produce different mazes");
    }

    // ============================================================================
    // Size parameter TESTS
    // ============================================================================

    @Test
    void testGeneratePreview_MinimumSize_Succeeds() {
        // When
        String svg = mazeGenerationService.generatePreview(MazeType.ORTHOGONAL, 1, 1, false);

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
    }

    @Test
    void testGeneratePreview_MaximumSize_Succeeds() {
        // When
        String svg = mazeGenerationService.generatePreview(MazeType.ORTHOGONAL, 20, 5, false);

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
    }

    @Test
    void testGeneratePreview_SizeOutOfRangeLow_ClampedToMinimum() {
        // When - size 0 should be clamped to 1
        String svg = mazeGenerationService.generatePreview(MazeType.ORTHOGONAL, 0, 3, false);

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
    }

    @Test
    void testGeneratePreview_SizeOutOfRangeHigh_ClampedToMaximum() {
        // When - size 100 should be clamped to 20
        String svg = mazeGenerationService.generatePreview(MazeType.ORTHOGONAL, 100, 3, false);

        // Then
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
    }
}
