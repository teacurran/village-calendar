package villagecompute.calendar.services.maze;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.enums.MazeType;

/** Unit tests for MazeSvgRenderer. */
class MazeSvgRendererTest {

    @Test
    void testRenderOrthogonalMaze() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.endsWith("</svg>"));
        assertTrue(svg.contains("viewBox=\"0 0 3500 2300\""));
        assertTrue(svg.contains("width=\"3500\""));
        assertTrue(svg.contains("height=\"2300\""));
    }

    @Test
    void testRenderOrthogonalMazeHasBackground() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        assertTrue(svg.contains("fill=\"white\""));
    }

    @Test
    void testRenderOrthogonalMazeHasWalls() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        assertTrue(svg.contains("class=\"inner-walls\""));
        assertTrue(svg.contains("class=\"outer-border\""));
        assertTrue(svg.contains("<line")); // Should have wall lines
    }

    @Test
    void testRenderOrthogonalMazeHasMarkers() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        assertTrue(svg.contains("class=\"start-marker\""));
        assertTrue(svg.contains("class=\"end-marker\""));
        assertTrue(svg.contains("fill=\"#2196F3\"")); // Start color
        assertTrue(svg.contains("fill=\"#F44336\"")); // End color
    }

    @Test
    void testRenderWithSolutionPath() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#000000", "#000000", "#4CAF50", true);
        String svg = renderer.render();

        assertTrue(svg.contains("class=\"solution-path\""));
        assertTrue(svg.contains("stroke=\"#4CAF50\""));
    }

    @Test
    void testRenderWithoutSolutionPath() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#000000", "#000000", "#4CAF50", false);
        String svg = renderer.render();

        assertFalse(svg.contains("class=\"solution-path\""));
    }

    @Test
    void testRenderWithDeadEnds() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 5, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#000000", "#000000", "#4CAF50", false, true, "#9E9E9E");
        String svg = renderer.render();

        assertTrue(svg.contains("class=\"dead-end-depth\""));
        assertTrue(svg.contains("fill=\"#9E9E9E\""));
    }

    @Test
    void testRenderWithoutDeadEnds() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 5, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#000000", "#000000", "#4CAF50", false, false, "#9E9E9E");
        String svg = renderer.render();

        assertFalse(svg.contains("class=\"dead-end-depth\""));
    }

    @Test
    void testRenderWithCustomColors() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#FF0000", "#00FF00", "#0000FF", true);
        String svg = renderer.render();

        assertTrue(svg.contains("stroke=\"#FF0000\"")); // Inner wall color
        assertTrue(svg.contains("stroke=\"#00FF00\"")); // Outer wall color
        assertTrue(svg.contains("stroke=\"#0000FF\"")); // Path color
    }

    @Test
    void testRenderSigmaMaze() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.SIGMA, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.endsWith("</svg>"));
        assertTrue(svg.contains("class=\"inner-walls\""));
        assertTrue(svg.contains("class=\"outer-border\""));
        assertTrue(svg.contains("class=\"start-marker\""));
        assertTrue(svg.contains("class=\"end-marker\""));
    }

    @Test
    void testRenderSigmaMazeWithSolution() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.SIGMA, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#000000", "#000000", "#4CAF50", true);
        String svg = renderer.render();

        assertTrue(svg.contains("class=\"solution-path\""));
    }

    @Test
    void testRenderSigmaMazeWithDeadEnds() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.SIGMA, 5, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#000000", "#000000", "#4CAF50", false, true, "#AABBCC");
        String svg = renderer.render();

        assertTrue(svg.contains("class=\"dead-end-depth\""));
        assertTrue(svg.contains("fill=\"#AABBCC\""));
        // Sigma maze dead ends use path elements for hexagon shapes
        assertTrue(svg.contains("<path"));
    }

    @Test
    void testRenderDeltaMaze() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.DELTA, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        assertNotNull(svg);
        assertTrue(svg.contains("Delta maze rendering coming soon"));
        assertTrue(svg.contains("<svg"));
    }

    @Test
    void testRenderThetaMaze() {
        MazeGrid grid = new MazeGrid(8, 8, MazeType.THETA, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"));
        // Theta maze uses circles
        assertTrue(svg.contains("<circle"));
        assertTrue(svg.contains("class=\"walls\""));
    }

    @Test
    void testDefaultConstructor() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        // Should use default colors
        assertTrue(svg.contains("stroke=\"#000000\"")); // Default wall color
    }

    @Test
    void testConstructorWithNullDeadEndColor() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 5, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#000000", "#000000", "#4CAF50", false, true, null);
        String svg = renderer.render();

        // Should use default dead end color (#9E9E9E)
        assertTrue(svg.contains("fill=\"#9E9E9E\""));
    }

    @Test
    void testLargeMazeRendering() {
        MazeGrid grid = new MazeGrid(20, 20, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        assertNotNull(svg);
        assertTrue(svg.length() > 1000); // Should be a substantial SVG
    }

    @Test
    void testLargeSigmaMazeRendering() {
        MazeGrid grid = new MazeGrid(15, 15, MazeType.SIGMA, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        assertNotNull(svg);
        assertTrue(svg.length() > 1000);
    }

    @Test
    void testSmallMazeRendering() {
        MazeGrid grid = new MazeGrid(2, 2, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        assertNotNull(svg);
        assertTrue(svg.contains("start-marker"));
        assertTrue(svg.contains("end-marker"));
    }

    @Test
    void testSmallSigmaMazeRendering() {
        MazeGrid grid = new MazeGrid(3, 3, MazeType.SIGMA, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        assertNotNull(svg);
        assertTrue(svg.contains("start-marker"));
        assertTrue(svg.contains("end-marker"));
    }

    @Test
    void testSigmaMazeHasHexagonalPaths() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.SIGMA, 5, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#000000", "#000000", "#4CAF50", false, true, "#9E9E9E");
        String svg = renderer.render();

        // Sigma maze dead ends draw hexagon paths with M (moveto) and L (lineto) and Z (close)
        // The path format is: d="M... L... L... L... L... L... Z"
        assertTrue(svg.contains("d=\"M"));
        assertTrue(svg.contains(" L"));
        assertTrue(svg.contains(" Z\""));
    }

    @Test
    void testSigmaMazeOuterBorder() {
        MazeGrid grid = new MazeGrid(4, 4, MazeType.SIGMA, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#FF0000", "#00FF00", "#0000FF", false);
        String svg = renderer.render();

        // Outer border should use outer wall color
        assertTrue(svg.contains("class=\"outer-border\""));
        assertTrue(svg.contains("stroke=\"#00FF00\"")); // Outer wall color
    }

    @Test
    void testRenderWithAllFeatures() {
        MazeGrid grid = new MazeGrid(8, 8, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#111111", "#222222", "#333333", true, true, "#444444");
        String svg = renderer.render();

        assertTrue(svg.contains("class=\"dead-end-depth\""));
        assertTrue(svg.contains("class=\"solution-path\""));
        assertTrue(svg.contains("class=\"inner-walls\""));
        assertTrue(svg.contains("class=\"outer-border\""));
        assertTrue(svg.contains("class=\"start-marker\""));
        assertTrue(svg.contains("class=\"end-marker\""));
    }

    @Test
    void testSigmaRenderWithAllFeatures() {
        MazeGrid grid = new MazeGrid(6, 6, MazeType.SIGMA, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#111111", "#222222", "#333333", true, true, "#444444");
        String svg = renderer.render();

        assertTrue(svg.contains("class=\"dead-end-depth\""));
        assertTrue(svg.contains("class=\"solution-path\""));
        assertTrue(svg.contains("class=\"inner-walls\""));
        assertTrue(svg.contains("class=\"outer-border\""));
    }

    @Test
    void testSolutionPathLineAttributes() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#000000", "#000000", "#FF00FF", true);
        String svg = renderer.render();

        // Solution path should have stroke-linecap and opacity
        assertTrue(svg.contains("stroke-linecap=\"round\""));
        assertTrue(svg.contains("opacity=\"0.6\""));
    }

    @Test
    void testDeadEndOpacityVariation() {
        MazeGrid grid = new MazeGrid(10, 10, MazeType.ORTHOGONAL, 5, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, "#000000", "#000000", "#4CAF50", false, true, "#9E9E9E");
        String svg = renderer.render();

        // Should have varying opacity values for dead end depths
        assertTrue(svg.contains("opacity=\""));
    }

    @Test
    void testThetaMazeHasConcentricCircles() {
        MazeGrid grid = new MazeGrid(10, 10, MazeType.THETA, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        // Should have multiple circles for concentric rings
        int circleCount = svg.split("<circle").length - 1;
        assertTrue(circleCount >= 3, "Should have multiple circles. Found: " + circleCount);
    }

    @Test
    void testThetaMazeHasRadialLines() {
        MazeGrid grid = new MazeGrid(10, 10, MazeType.THETA, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        // Should have radial line walls
        int lineCount = svg.split("<line").length - 1;
        assertTrue(lineCount >= 8, "Should have radial lines. Found: " + lineCount);
    }

    @Test
    void testSvgIsValidXml() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        // Basic XML validity checks
        assertTrue(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""));
        assertEquals(countOccurrences(svg, "<g"), countOccurrences(svg, "</g>"), "Group tags should be balanced");
    }

    @Test
    void testSigmaSvgIsValidXml() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.SIGMA, 3, 12345L);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid);
        String svg = renderer.render();

        assertTrue(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""));
        assertEquals(countOccurrences(svg, "<g"), countOccurrences(svg, "</g>"), "Group tags should be balanced");
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
