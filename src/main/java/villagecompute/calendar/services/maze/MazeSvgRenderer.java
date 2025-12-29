package villagecompute.calendar.services.maze;

/** Renders a MazeGrid to SVG format. Designed for 35" x 23" page with 1" margins on all sides. */
public class MazeSvgRenderer {

    // Page dimensions at 100 DPI
    private static final int PAGE_WIDTH = 3500; // 35 inches
    private static final int PAGE_HEIGHT = 2300; // 23 inches
    private static final int MARGIN = 100; // 1 inch margins

    // Printable area (page minus margins on all sides)
    private static final int PRINTABLE_WIDTH = PAGE_WIDTH - (2 * MARGIN); // 3300 units (33")
    private static final int PRINTABLE_HEIGHT = PAGE_HEIGHT - (2 * MARGIN); // 2100 units (21")

    private static final int OUTER_WALL_THICKNESS = 8; // Thick outer border
    private static final int INNER_WALL_THICKNESS = 2; // Thin inner walls
    private static final String DEFAULT_INNER_WALL_COLOR = "#000000";
    private static final String DEFAULT_OUTER_WALL_COLOR = "#000000";
    private static final String DEFAULT_PATH_COLOR = "#4CAF50";
    private static final String DEFAULT_START_COLOR = "#2196F3";
    private static final String DEFAULT_END_COLOR = "#F44336";
    private static final String DEFAULT_DEAD_END_COLOR = "#9E9E9E"; // Light gray for dead-end paths

    private final MazeGrid grid;
    private final String innerWallColor;
    private final String outerWallColor;
    private final String pathColor;
    private final boolean showSolution;
    private final boolean showDeadEnds;
    private final String deadEndColor;

    // Calculated dimensions
    private final int cellSize;
    private final int mazeWidth;
    private final int mazeHeight;
    private final int offsetX;
    private final int offsetY;

    public MazeSvgRenderer(MazeGrid grid) {
        this(
                grid,
                DEFAULT_INNER_WALL_COLOR,
                DEFAULT_OUTER_WALL_COLOR,
                DEFAULT_PATH_COLOR,
                false,
                false,
                DEFAULT_DEAD_END_COLOR);
    }

    public MazeSvgRenderer(
            MazeGrid grid,
            String innerWallColor,
            String outerWallColor,
            String pathColor,
            boolean showSolution) {
        this(
                grid,
                innerWallColor,
                outerWallColor,
                pathColor,
                showSolution,
                false,
                DEFAULT_DEAD_END_COLOR);
    }

    public MazeSvgRenderer(
            MazeGrid grid,
            String innerWallColor,
            String outerWallColor,
            String pathColor,
            boolean showSolution,
            boolean showDeadEnds,
            String deadEndColor) {
        this.grid = grid;
        this.innerWallColor = innerWallColor;
        this.outerWallColor = outerWallColor;
        this.pathColor = pathColor;
        this.showSolution = showSolution;
        this.showDeadEnds = showDeadEnds;
        this.deadEndColor = deadEndColor != null ? deadEndColor : DEFAULT_DEAD_END_COLOR;

        // Calculate cell size to fit the printable area while maintaining square cells
        int maxCellWidth = PRINTABLE_WIDTH / grid.getWidth();
        int maxCellHeight = PRINTABLE_HEIGHT / grid.getHeight();
        this.cellSize = Math.min(maxCellWidth, maxCellHeight);

        // Calculate actual maze dimensions
        this.mazeWidth = cellSize * grid.getWidth();
        this.mazeHeight = cellSize * grid.getHeight();

        // Center the maze within the page (including margins)
        this.offsetX = MARGIN + (PRINTABLE_WIDTH - mazeWidth) / 2;
        this.offsetY = MARGIN + (PRINTABLE_HEIGHT - mazeHeight) / 2;
    }

    public String render() {
        return switch (grid.getType()) {
            case ORTHOGONAL -> renderOrthogonal();
            case DELTA -> renderDelta();
            case SIGMA -> renderSigma();
            case THETA -> renderTheta();
        };
    }

    private String renderOrthogonal() {
        int gridWidth = grid.getWidth();
        int gridHeight = grid.getHeight();

        StringBuilder svg = new StringBuilder();
        svg.append(
                String.format(
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\""
                                + " width=\"%d\" height=\"%d\">\n",
                        PAGE_WIDTH, PAGE_HEIGHT, PAGE_WIDTH, PAGE_HEIGHT));

        // Background
        svg.append(
                String.format(
                        "  <rect width=\"%d\" height=\"%d\" fill=\"white\"/>\n",
                        PAGE_WIDTH, PAGE_HEIGHT));

        // Draw dead-end depth visualization if enabled (before walls so it appears behind)
        if (showDeadEnds) {
            svg.append("  <g class=\"dead-end-depth\">\n");

            // Find maximum depth for gradient calculation
            int maxDepth = 1;
            for (int x = 0; x < gridWidth; x++) {
                for (int y = 0; y < gridHeight; y++) {
                    MazeCell cell = grid.getCell(x, y);
                    if (cell.deadEndDepth > maxDepth) {
                        maxDepth = cell.deadEndDepth;
                    }
                }
            }

            // Draw cells colored by depth - deeper dead ends are more visible
            for (int x = 0; x < gridWidth; x++) {
                for (int y = 0; y < gridHeight; y++) {
                    MazeCell cell = grid.getCell(x, y);
                    if (cell.isDeadEnd && cell.deadEndDepth > 0) {
                        int cellX = offsetX + x * cellSize;
                        int cellY = offsetY + y * cellSize;
                        // Deeper = more opaque (worse wrong turns)
                        double opacity = 0.1 + (0.5 * cell.deadEndDepth / maxDepth);
                        svg.append(
                                String.format(
                                        "    <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\""
                                                + " fill=\"%s\" opacity=\"%.2f\"/>\n",
                                        cellX, cellY, cellSize, cellSize, deadEndColor, opacity));
                    }
                }
            }
            svg.append("  </g>\n");
        }

        // Draw solution path if enabled
        if (showSolution && grid.getSolutionPath() != null) {
            svg.append("  <g class=\"solution-path\">\n");
            var path = grid.getSolutionPath();
            int pathWidth = Math.max(cellSize / 4, 6);
            for (int i = 0; i < path.size() - 1; i++) {
                int[] from = path.get(i);
                int[] to = path.get(i + 1);
                int x1 = offsetX + from[0] * cellSize + cellSize / 2;
                int y1 = offsetY + from[1] * cellSize + cellSize / 2;
                int x2 = offsetX + to[0] * cellSize + cellSize / 2;
                int y2 = offsetY + to[1] * cellSize + cellSize / 2;
                svg.append(
                        String.format(
                                "    <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\""
                                        + " stroke-width=\"%d\" stroke-linecap=\"round\""
                                        + " opacity=\"0.6\"/>\n",
                                x1, y1, x2, y2, pathColor, pathWidth));
            }
            svg.append("  </g>\n");
        }

        // Draw internal walls first (thin)
        svg.append("  <g class=\"inner-walls\">\n");
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                MazeCell cell = grid.getCell(x, y);
                int cellX = offsetX + x * cellSize;
                int cellY = offsetY + y * cellSize;

                // East wall (only if not on right edge - outer border handles that)
                if (cell.eastWall && x < gridWidth - 1) {
                    svg.append(
                            String.format(
                                    "    <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\""
                                            + " stroke=\"%s\" stroke-width=\"%d\""
                                            + " stroke-linecap=\"square\"/>\n",
                                    cellX + cellSize,
                                    cellY,
                                    cellX + cellSize,
                                    cellY + cellSize,
                                    innerWallColor,
                                    INNER_WALL_THICKNESS));
                }

                // South wall (only if not on bottom edge - outer border handles that)
                if (cell.southWall && y < gridHeight - 1) {
                    svg.append(
                            String.format(
                                    "    <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\""
                                            + " stroke=\"%s\" stroke-width=\"%d\""
                                            + " stroke-linecap=\"square\"/>\n",
                                    cellX,
                                    cellY + cellSize,
                                    cellX + cellSize,
                                    cellY + cellSize,
                                    innerWallColor,
                                    INNER_WALL_THICKNESS));
                }
            }
        }
        svg.append("  </g>\n");

        // Draw outer border (thick rectangle)
        svg.append("  <g class=\"outer-border\">\n");
        svg.append(
                String.format(
                        "    <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"none\""
                                + " stroke=\"%s\" stroke-width=\"%d\"/>\n",
                        offsetX,
                        offsetY,
                        mazeWidth,
                        mazeHeight,
                        outerWallColor,
                        OUTER_WALL_THICKNESS));
        svg.append("  </g>\n");

        // Calculate marker size based on cell size
        int markerRadius = Math.max(cellSize / 5, 8);

        // Draw start marker
        int startX = offsetX + grid.getStartX() * cellSize + cellSize / 2;
        int startY = offsetY + grid.getStartY() * cellSize + cellSize / 2;
        svg.append(
                String.format(
                        "  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\""
                                + " class=\"start-marker\"/>\n",
                        startX, startY, markerRadius, DEFAULT_START_COLOR));

        // Draw end marker
        int endX = offsetX + grid.getEndX() * cellSize + cellSize / 2;
        int endY = offsetY + grid.getEndY() * cellSize + cellSize / 2;
        svg.append(
                String.format(
                        "  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\""
                                + " class=\"end-marker\"/>\n",
                        endX, endY, markerRadius, DEFAULT_END_COLOR));

        svg.append("</svg>");
        return svg.toString();
    }

    private String renderDelta() {
        // Triangular maze rendering - for now, use orthogonal
        return renderOrthogonal()
                .replace("<svg", "<!-- Delta maze rendering coming soon -->\n<svg");
    }

    private String renderSigma() {
        // Hexagonal maze rendering - for now, use orthogonal
        return renderOrthogonal()
                .replace("<svg", "<!-- Sigma maze rendering coming soon -->\n<svg");
    }

    private String renderTheta() {
        // Circular maze rendering
        int rings = Math.min(grid.getWidth(), grid.getHeight()) / 2;

        // Calculate ring spacing to fill the printable area
        int maxRadius = Math.min(PRINTABLE_WIDTH, PRINTABLE_HEIGHT) / 2;
        int ringSpacing = maxRadius / rings;

        int centerX = PAGE_WIDTH / 2;
        int centerY = PAGE_HEIGHT / 2;

        StringBuilder svg = new StringBuilder();
        svg.append(
                String.format(
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\""
                                + " width=\"%d\" height=\"%d\">\n",
                        PAGE_WIDTH, PAGE_HEIGHT, PAGE_WIDTH, PAGE_HEIGHT));

        // Background
        svg.append(
                String.format(
                        "  <rect width=\"%d\" height=\"%d\" fill=\"white\"/>\n",
                        PAGE_WIDTH, PAGE_HEIGHT));

        // Draw concentric circles and radial walls
        svg.append("  <g class=\"walls\">\n");
        for (int ring = 1; ring <= rings; ring++) {
            int radius = ring * ringSpacing;
            svg.append(
                    String.format(
                            "    <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"none\" stroke=\"%s\""
                                    + " stroke-width=\"%d\"/>\n",
                            centerX, centerY, radius, innerWallColor, INNER_WALL_THICKNESS));
        }

        // Draw radial walls (simplified)
        int segments = 8 + rings * 2;
        for (int i = 0; i < segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            int x2 = centerX + (int) (maxRadius * Math.cos(angle));
            int y2 = centerY + (int) (maxRadius * Math.sin(angle));
            svg.append(
                    String.format(
                            "    <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\""
                                    + " stroke-width=\"%d\"/>\n",
                            centerX, centerY, x2, y2, innerWallColor, INNER_WALL_THICKNESS));
        }
        svg.append("  </g>\n");

        // Marker size
        int markerRadius = Math.max(ringSpacing / 4, 12);

        // Start marker (center)
        svg.append(
                String.format(
                        "  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\""
                                + " class=\"start-marker\"/>\n",
                        centerX, centerY, markerRadius, DEFAULT_START_COLOR));

        // End marker (edge)
        svg.append(
                String.format(
                        "  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\""
                                + " class=\"end-marker\"/>\n",
                        centerX + maxRadius - ringSpacing / 2,
                        centerY,
                        markerRadius,
                        DEFAULT_END_COLOR));

        svg.append("</svg>");
        return svg.toString();
    }
}
