package villagecompute.calendar.services.maze;

/** Renders a MazeGrid to SVG format. Designed for 35" x 23" page with 1" margins on all sides. */
public class MazeSvgRenderer {

    /** Geometry parameters for laying out a pointy-top hexagonal grid. */
    private record HexGeometry(double hexWidth, double vertSpacing, double offsetX, double offsetY, double hexSize) {
    }

    /** Grid dimensions in cells. */
    private record GridDimensions(int width, int height) {
    }

    /** SVG closing tag for groups */
    private static final String SVG_GROUP_CLOSE = "  </g>";

    /** SVG document closing tag */
    private static final String SVG_DOC_CLOSE = "</svg>";

    /** Format fragment with width/height attributes that closes the opening tag */
    private static final String SVG_WIDTH_HEIGHT_OPEN_TAIL = " width=\"%d\" height=\"%d\">%n";

    /** Format string for the white background rectangle */
    private static final String SVG_BACKGROUND_RECT_FORMAT = "  <rect width=\"%d\" height=\"%d\" fill=\"white\"/>%n";

    /** Format fragment with stroke color and width attributes */
    private static final String SVG_STROKE_ATTRS_TAIL = " stroke=\"%s\" stroke-width=\"%d\"/>%n";

    /** Format fragment for the start-marker class attribute */
    private static final String SVG_START_MARKER_CLASS_TAIL = " class=\"start-marker\"/>%n";

    /** Format fragment for the end-marker class attribute */
    private static final String SVG_END_MARKER_CLASS_TAIL = " class=\"end-marker\"/>%n";

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
        this(grid, DEFAULT_INNER_WALL_COLOR, DEFAULT_OUTER_WALL_COLOR, DEFAULT_PATH_COLOR, false, false,
                DEFAULT_DEAD_END_COLOR);
    }

    public MazeSvgRenderer(MazeGrid grid, String innerWallColor, String outerWallColor, String pathColor,
            boolean showSolution) {
        this(grid, innerWallColor, outerWallColor, pathColor, showSolution, false, DEFAULT_DEAD_END_COLOR);
    }

    public MazeSvgRenderer(MazeGrid grid, String innerWallColor, String outerWallColor, String pathColor,
            boolean showSolution, boolean showDeadEnds, String deadEndColor) {
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
        svg.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\"" + SVG_WIDTH_HEIGHT_OPEN_TAIL,
                PAGE_WIDTH, PAGE_HEIGHT, PAGE_WIDTH, PAGE_HEIGHT));

        // Background
        svg.append(String.format(SVG_BACKGROUND_RECT_FORMAT, PAGE_WIDTH, PAGE_HEIGHT));

        // Draw dead-end depth visualization if enabled (before walls so it appears behind)
        appendOrthogonalDeadEnds(svg, gridWidth, gridHeight);

        // Draw solution path if enabled
        appendOrthogonalSolutionPath(svg);

        // Draw internal walls first (thin)
        appendOrthogonalInnerWalls(svg, gridWidth, gridHeight);

        // Draw outer border (thick rectangle)
        svg.append("  <g class=\"outer-border\">").append(System.lineSeparator());
        svg.append(String.format(
                "    <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"none\"" + SVG_STROKE_ATTRS_TAIL, offsetX,
                offsetY, mazeWidth, mazeHeight, outerWallColor, OUTER_WALL_THICKNESS));
        svg.append(SVG_GROUP_CLOSE).append(System.lineSeparator());

        // Draw start/end markers
        appendOrthogonalMarkers(svg);

        svg.append("</svg>");
        return svg.toString();
    }

    private void appendOrthogonalDeadEnds(StringBuilder svg, int gridWidth, int gridHeight) {
        if (!showDeadEnds) {
            return;
        }
        svg.append("  <g class=\"dead-end-depth\">").append(System.lineSeparator());

        int maxDepth = findMaxDeadEndDepth(gridWidth, gridHeight);

        // Draw cells colored by depth - deeper dead ends are more visible
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                appendDeadEndCell(svg, x, y, maxDepth);
            }
        }
        svg.append(SVG_GROUP_CLOSE).append(System.lineSeparator());
    }

    private int findMaxDeadEndDepth(int gridWidth, int gridHeight) {
        int maxDepth = 1;
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                MazeCell cell = grid.getCell(x, y);
                if (cell.deadEndDepth > maxDepth) {
                    maxDepth = cell.deadEndDepth;
                }
            }
        }
        return maxDepth;
    }

    private void appendDeadEndCell(StringBuilder svg, int x, int y, int maxDepth) {
        MazeCell cell = grid.getCell(x, y);
        if (!cell.isDeadEnd || cell.deadEndDepth <= 0) {
            return;
        }
        int cellX = offsetX + x * cellSize;
        int cellY = offsetY + y * cellSize;
        // Deeper = more opaque (worse wrong turns)
        double opacity = 0.1 + (0.5 * cell.deadEndDepth / maxDepth);
        svg.append(String.format(
                "    <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"" + " fill=\"%s\" opacity=\"%.2f\"/>%n", cellX,
                cellY, cellSize, cellSize, deadEndColor, opacity));
    }

    private void appendOrthogonalSolutionPath(StringBuilder svg) {
        if (!showSolution || grid.getSolutionPath() == null) {
            return;
        }
        svg.append("  <g class=\"solution-path\">").append(System.lineSeparator());
        var path = grid.getSolutionPath();
        int pathWidth = Math.max(cellSize / 4, 6);
        for (int i = 0; i < path.size() - 1; i++) {
            int[] from = path.get(i);
            int[] to = path.get(i + 1);
            int x1 = offsetX + from[0] * cellSize + cellSize / 2;
            int y1 = offsetY + from[1] * cellSize + cellSize / 2;
            int x2 = offsetX + to[0] * cellSize + cellSize / 2;
            int y2 = offsetY + to[1] * cellSize + cellSize / 2;
            svg.append(String.format(
                    "    <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\""
                            + " stroke-width=\"%d\" stroke-linecap=\"round\"" + " opacity=\"0.6\"/>%n",
                    x1, y1, x2, y2, pathColor, pathWidth));
        }
        svg.append(SVG_GROUP_CLOSE).append(System.lineSeparator());
    }

    private void appendOrthogonalInnerWalls(StringBuilder svg, int gridWidth, int gridHeight) {
        svg.append("  <g class=\"inner-walls\">").append(System.lineSeparator());
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                appendInnerWallsForCell(svg, x, y, gridWidth, gridHeight);
            }
        }
        svg.append(SVG_GROUP_CLOSE).append(System.lineSeparator());
    }

    private void appendInnerWallsForCell(StringBuilder svg, int x, int y, int gridWidth, int gridHeight) {
        MazeCell cell = grid.getCell(x, y);
        int cellX = offsetX + x * cellSize;
        int cellY = offsetY + y * cellSize;

        // East wall (only if not on right edge - outer border handles that)
        boolean drawEast = cell.eastWall && x < gridWidth - 1;
        if (drawEast) {
            svg.append(String.format(
                    "    <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\"" + " stroke=\"%s\" stroke-width=\"%d\""
                            + " stroke-linecap=\"square\"/>%n",
                    cellX + cellSize, cellY, cellX + cellSize, cellY + cellSize, innerWallColor, INNER_WALL_THICKNESS));
        }

        // South wall (only if not on bottom edge - outer border handles that)
        boolean drawSouth = cell.southWall && y < gridHeight - 1;
        if (drawSouth) {
            svg.append(String.format(
                    "    <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\"" + " stroke=\"%s\" stroke-width=\"%d\""
                            + " stroke-linecap=\"square\"/>%n",
                    cellX, cellY + cellSize, cellX + cellSize, cellY + cellSize, innerWallColor, INNER_WALL_THICKNESS));
        }
    }

    private void appendOrthogonalMarkers(StringBuilder svg) {
        // Calculate marker size based on cell size
        int markerRadius = Math.max(cellSize / 5, 8);

        // Draw start marker
        int startX = offsetX + grid.getStartX() * cellSize + cellSize / 2;
        int startY = offsetY + grid.getStartY() * cellSize + cellSize / 2;
        svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\"" + SVG_START_MARKER_CLASS_TAIL,
                startX, startY, markerRadius, DEFAULT_START_COLOR));

        // Draw end marker
        int endX = offsetX + grid.getEndX() * cellSize + cellSize / 2;
        int endY = offsetY + grid.getEndY() * cellSize + cellSize / 2;
        svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\"" + SVG_END_MARKER_CLASS_TAIL, endX,
                endY, markerRadius, DEFAULT_END_COLOR));
    }

    private String renderDelta() {
        // Triangular maze rendering - for now, use orthogonal
        return renderOrthogonal().replace("<svg", "<!-- Delta maze rendering coming soon -->%n<svg");
    }

    private String renderSigma() {
        GridDimensions dims = new GridDimensions(grid.getWidth(), grid.getHeight());

        // Calculate hexagon dimensions for pointy-top hexagons
        // For a pointy-top hex with "size" being the distance from center to vertex:
        // width = size * sqrt(3), height = size * 2
        // Horizontal spacing = width, vertical spacing = height * 3/4
        double hexSize = calculateHexSize(dims.width(), dims.height());
        double hexWidth = hexSize * Math.sqrt(3);
        double hexHeight = hexSize * 2;
        double vertSpacing = hexHeight * 0.75;

        // Calculate actual maze dimensions
        double actualMazeWidth = dims.width() * hexWidth + hexWidth / 2; // Extra for odd row offset
        double actualMazeHeight = (dims.height() - 1) * vertSpacing + hexHeight;

        // Center the maze
        double hexOffsetX = MARGIN + (PRINTABLE_WIDTH - actualMazeWidth) / 2;
        double hexOffsetY = MARGIN + (PRINTABLE_HEIGHT - actualMazeHeight) / 2;

        HexGeometry geom = new HexGeometry(hexWidth, vertSpacing, hexOffsetX, hexOffsetY, hexSize);

        StringBuilder svg = new StringBuilder();
        svg.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\"" + SVG_WIDTH_HEIGHT_OPEN_TAIL,
                PAGE_WIDTH, PAGE_HEIGHT, PAGE_WIDTH, PAGE_HEIGHT));

        // Background
        svg.append(String.format(SVG_BACKGROUND_RECT_FORMAT, PAGE_WIDTH, PAGE_HEIGHT));

        appendSigmaDeadEnds(svg, dims, geom);
        appendSigmaSolutionPath(svg, geom);
        appendSigmaInnerWalls(svg, dims, geom);

        // Draw outer border (hexagon outline for edge cells)
        svg.append("  <g class=\"outer-border\">").append(System.lineSeparator());
        drawHexOuterBorder(svg, dims, geom);
        svg.append(SVG_GROUP_CLOSE).append(System.lineSeparator());

        appendSigmaMarkers(svg, geom);

        svg.append("</svg>");
        return svg.toString();
    }

    /** Append dead-end depth visualization for sigma maze if enabled. */
    private void appendSigmaDeadEnds(StringBuilder svg, GridDimensions dims, HexGeometry geom) {
        if (!showDeadEnds) {
            return;
        }
        svg.append("  <g class=\"dead-end-depth\">").append(System.lineSeparator());
        int maxDepth = findMaxDeadEndDepth(dims.width(), dims.height());
        for (int y = 0; y < dims.height(); y++) {
            for (int x = 0; x < dims.width(); x++) {
                MazeCell cell = grid.getCell(x, y);
                if (!cell.isDeadEnd || cell.deadEndDepth <= 0) {
                    continue;
                }
                double[] center = getHexCenter(x, y, geom);
                double opacity = 0.1 + (0.5 * cell.deadEndDepth / maxDepth);
                String hexPath = getHexPath(center[0], center[1], geom.hexSize());
                svg.append(String.format("    <path d=\"%s\" fill=\"%s\" opacity=\"%.2f\"/>%n", hexPath, deadEndColor,
                        opacity));
            }
        }
        svg.append(SVG_GROUP_CLOSE).append(System.lineSeparator());
    }

    /** Append solution path lines for sigma maze if enabled. */
    private void appendSigmaSolutionPath(StringBuilder svg, HexGeometry geom) {
        if (!showSolution || grid.getSolutionPath() == null) {
            return;
        }
        svg.append("  <g class=\"solution-path\">").append(System.lineSeparator());
        var path = grid.getSolutionPath();
        int pathWidth = Math.max((int) (geom.hexSize() / 3), 6);
        for (int i = 0; i < path.size() - 1; i++) {
            int[] from = path.get(i);
            int[] to = path.get(i + 1);
            double[] fromCenter = getHexCenter(from[0], from[1], geom);
            double[] toCenter = getHexCenter(to[0], to[1], geom);
            svg.append(String.format(
                    "    <line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"%s\""
                            + " stroke-width=\"%d\" stroke-linecap=\"round\" opacity=\"0.6\"/>%n",
                    fromCenter[0], fromCenter[1], toCenter[0], toCenter[1], pathColor, pathWidth));
        }
        svg.append(SVG_GROUP_CLOSE).append(System.lineSeparator());
    }

    /** Append inner hexagon walls for sigma maze. */
    private void appendSigmaInnerWalls(StringBuilder svg, GridDimensions dims, HexGeometry geom) {
        svg.append("  <g class=\"inner-walls\">").append(System.lineSeparator());
        for (int y = 0; y < dims.height(); y++) {
            for (int x = 0; x < dims.width(); x++) {
                MazeCell cell = grid.getCell(x, y);
                double[] center = getHexCenter(x, y, geom);
                drawHexWalls(svg, cell, x, y, center, geom.hexSize(), dims);
            }
        }
        svg.append(SVG_GROUP_CLOSE).append(System.lineSeparator());
    }

    /** Append start and end markers for sigma maze. */
    private void appendSigmaMarkers(StringBuilder svg, HexGeometry geom) {
        int markerRadius = Math.max((int) (geom.hexSize() / 3), 8);
        double[] startCenter = getHexCenter(grid.getStartX(), grid.getStartY(), geom);
        svg.append(String.format("  <circle cx=\"%.1f\" cy=\"%.1f\" r=\"%d\" fill=\"%s\"" + SVG_START_MARKER_CLASS_TAIL,
                startCenter[0], startCenter[1], markerRadius, DEFAULT_START_COLOR));
        double[] endCenter = getHexCenter(grid.getEndX(), grid.getEndY(), geom);
        svg.append(String.format("  <circle cx=\"%.1f\" cy=\"%.1f\" r=\"%d\" fill=\"%s\"" + SVG_END_MARKER_CLASS_TAIL,
                endCenter[0], endCenter[1], markerRadius, DEFAULT_END_COLOR));
    }

    /** Calculate hex size to fit grid in printable area. */
    private double calculateHexSize(int gridWidth, int gridHeight) {
        // For pointy-top: width = size * sqrt(3), height = size * 2
        // Total width = gridWidth * hexWidth + hexWidth/2 (for offset)
        // Total height = (gridHeight - 1) * (hexHeight * 0.75) + hexHeight
        double maxSizeByWidth = PRINTABLE_WIDTH / (gridWidth * Math.sqrt(3) + Math.sqrt(3) / 2);
        double maxSizeByHeight = PRINTABLE_HEIGHT / ((gridHeight - 1) * 1.5 + 2);
        return Math.min(maxSizeByWidth, maxSizeByHeight);
    }

    /** Get center point of a hexagon at grid position (x, y). */
    private double[] getHexCenter(int x, int y, HexGeometry geom) {
        boolean oddRow = (y % 2) == 1;
        double cx = geom.offsetX() + x * geom.hexWidth() + geom.hexWidth() / 2 + (oddRow ? geom.hexWidth() / 2 : 0);
        double cy = geom.offsetY() + y * geom.vertSpacing() + geom.hexSize();
        return new double[]{cx, cy};
    }

    /** Generate SVG path for a hexagon centered at (cx, cy). */
    private String getHexPath(double cx, double cy, double size) {
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 6 + i * Math.PI / 3; // Start from top vertex
            double vx = cx + size * Math.cos(angle);
            double vy = cy - size * Math.sin(angle);
            if (i == 0) {
                path.append(String.format("M%.1f,%.1f", vx, vy));
            } else {
                path.append(String.format(" L%.1f,%.1f", vx, vy));
            }
        }
        path.append(" Z");
        return path.toString();
    }

    /** Draw walls for a single hexagon cell. */
    private void drawHexWalls(StringBuilder svg, MazeCell cell, int x, int y, double[] center, double hexSize,
            GridDimensions dims) {
        // Calculate vertex positions for pointy-top hexagon
        double[][] vertices = new double[6][2];
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 6 + i * Math.PI / 3;
            vertices[i][0] = center[0] + hexSize * Math.cos(angle);
            vertices[i][1] = center[1] - hexSize * Math.sin(angle);
        }
        // Vertices: 0=top, 1=top-right, 2=bottom-right, 3=bottom, 4=bottom-left, 5=top-left

        boolean evenRow = (y % 2) == 0;

        // Draw internal walls only (edges shared between cells)
        // NE wall: vertices 0-1 (if wall exists and not at edge)
        if (cell.northEastWall && hasNENeighbor(x, y, dims.width(), evenRow)) {
            svg.append(String.format(
                    "    <line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"" + SVG_STROKE_ATTRS_TAIL, vertices[0][0],
                    vertices[0][1], vertices[1][0], vertices[1][1], innerWallColor, INNER_WALL_THICKNESS));
        }

        // E wall: vertices 1-2 (if wall exists and not at right edge)
        if (cell.eastWall && x < dims.width() - 1) {
            svg.append(String.format(
                    "    <line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"" + SVG_STROKE_ATTRS_TAIL, vertices[1][0],
                    vertices[1][1], vertices[2][0], vertices[2][1], innerWallColor, INNER_WALL_THICKNESS));
        }

        // SE wall: vertices 2-3 (if wall exists and not at edge)
        if (cell.southEastWall && hasSENeighbor(x, y, dims, evenRow)) {
            svg.append(String.format(
                    "    <line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"" + SVG_STROKE_ATTRS_TAIL, vertices[2][0],
                    vertices[2][1], vertices[3][0], vertices[3][1], innerWallColor, INNER_WALL_THICKNESS));
        }

        // We don't draw SW, W, NW walls here - they will be drawn by neighboring cells
        // This prevents double-drawing internal walls
    }

    /** Check if cell has NE neighbor. */
    private boolean hasNENeighbor(int x, int y, int gridWidth, boolean evenRow) {
        if (y == 0) {
            return false;
        }
        if (evenRow) {
            return true; // NE is at (x, y-1)
        } else {
            return x < gridWidth - 1; // NE is at (x+1, y-1)
        }
    }

    /** Check if cell has SE neighbor. */
    private boolean hasSENeighbor(int x, int y, GridDimensions dims, boolean evenRow) {
        if (y >= dims.height() - 1) {
            return false;
        }
        if (evenRow) {
            return true; // SE is at (x, y+1)
        } else {
            return x < dims.width() - 1; // SE is at (x+1, y+1)
        }
    }

    /** Draw outer border for hexagonal maze. */
    private void drawHexOuterBorder(StringBuilder svg, GridDimensions dims, HexGeometry geom) {
        // Draw border walls for edge cells
        for (int y = 0; y < dims.height(); y++) {
            for (int x = 0; x < dims.width(); x++) {
                double[][] vertices = computeHexVertices(x, y, geom);
                drawHexCellBorderWalls(svg, x, y, dims, vertices);
            }
        }
    }

    /** Compute the six vertex coordinates of a hexagon cell. */
    private double[][] computeHexVertices(int x, int y, HexGeometry geom) {
        double[] center = getHexCenter(x, y, geom);
        double[][] vertices = new double[6][2];
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 6 + i * Math.PI / 3;
            vertices[i][0] = center[0] + geom.hexSize() * Math.cos(angle);
            vertices[i][1] = center[1] - geom.hexSize() * Math.sin(angle);
        }
        return vertices;
    }

    /** Draw any outer-border walls for a single hex cell at (x, y). */
    private void drawHexCellBorderWalls(StringBuilder svg, int x, int y, GridDimensions dims, double[][] vertices) {
        boolean evenRow = (y % 2) == 0;
        boolean leftColumn = (x == 0);
        boolean rightColumn = (x == dims.width() - 1);

        if (y == 0) {
            drawHexTopBorder(svg, vertices, evenRow, leftColumn, rightColumn);
        }
        if (y == dims.height() - 1) {
            drawHexBottomBorder(svg, vertices, evenRow, leftColumn, rightColumn);
        }
        if (leftColumn) {
            appendHexBorderLine(svg, vertices, 4, 5);
        }
        if (rightColumn) {
            appendHexBorderLine(svg, vertices, 1, 2);
        }
    }

    /** Draw top-edge walls (vertices 5-0 and 0-1) when needed. */
    private void drawHexTopBorder(StringBuilder svg, double[][] vertices, boolean evenRow, boolean leftColumn,
            boolean rightColumn) {
        if (evenRow || leftColumn) {
            appendHexBorderLine(svg, vertices, 5, 0);
        }
        if (evenRow || rightColumn) {
            appendHexBorderLine(svg, vertices, 0, 1);
        }
    }

    /** Draw bottom-edge walls (vertices 4-3 and 3-2) when needed. */
    private void drawHexBottomBorder(StringBuilder svg, double[][] vertices, boolean evenRow, boolean leftColumn,
            boolean rightColumn) {
        if (evenRow || leftColumn) {
            appendHexBorderLine(svg, vertices, 4, 3);
        }
        if (evenRow || rightColumn) {
            appendHexBorderLine(svg, vertices, 3, 2);
        }
    }

    /** Append a single SVG line element between two hexagon vertices. */
    private void appendHexBorderLine(StringBuilder svg, double[][] vertices, int from, int to) {
        svg.append(
                String.format(
                        "    <line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\""
                                + " stroke=\"%s\" stroke-width=\"%d\"/>%n",
                        vertices[from][0], vertices[from][1], vertices[to][0], vertices[to][1], outerWallColor,
                        OUTER_WALL_THICKNESS));
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
        svg.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\"" + SVG_WIDTH_HEIGHT_OPEN_TAIL,
                PAGE_WIDTH, PAGE_HEIGHT, PAGE_WIDTH, PAGE_HEIGHT));

        // Background
        svg.append(String.format(SVG_BACKGROUND_RECT_FORMAT, PAGE_WIDTH, PAGE_HEIGHT));

        // Draw concentric circles and radial walls
        svg.append("  <g class=\"walls\">").append(System.lineSeparator());
        for (int ring = 1; ring <= rings; ring++) {
            int radius = ring * ringSpacing;
            svg.append(String.format(
                    "    <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"none\" stroke=\"%s\"" + " stroke-width=\"%d\"/>%n",
                    centerX, centerY, radius, innerWallColor, INNER_WALL_THICKNESS));
        }

        // Draw radial walls (simplified)
        int segments = 8 + rings * 2;
        for (int i = 0; i < segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            int x2 = centerX + (int) (maxRadius * Math.cos(angle));
            int y2 = centerY + (int) (maxRadius * Math.sin(angle));
            svg.append(String.format(
                    "    <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\"" + " stroke-width=\"%d\"/>%n",
                    centerX, centerY, x2, y2, innerWallColor, INNER_WALL_THICKNESS));
        }
        svg.append(SVG_GROUP_CLOSE).append(System.lineSeparator());

        // Marker size
        int markerRadius = Math.max(ringSpacing / 4, 12);

        // Start marker (center)
        svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\"" + SVG_START_MARKER_CLASS_TAIL,
                centerX, centerY, markerRadius, DEFAULT_START_COLOR));

        // End marker (edge)
        svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\"" + SVG_END_MARKER_CLASS_TAIL,
                centerX + maxRadius - ringSpacing / 2, centerY, markerRadius, DEFAULT_END_COLOR));

        svg.append(SVG_DOC_CLOSE);
        return svg.toString();
    }
}
