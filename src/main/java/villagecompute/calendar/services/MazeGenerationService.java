package villagecompute.calendar.services;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import villagecompute.calendar.data.models.UserMaze;
import villagecompute.calendar.data.models.enums.MazeType;
import villagecompute.calendar.services.maze.MazeGrid;
import villagecompute.calendar.services.maze.MazeSvgRenderer;

/**
 * Service for generating mazes and their SVG representations. Size (1-20) controls the number of cells. Difficulty
 * (1-20) controls the solution path length.
 */
@ApplicationScoped
public class MazeGenerationService {

    // Printable area aspect ratio (33" x 21")
    private static final double ASPECT_RATIO = 33.0 / 21.0; // ~1.57

    private static final String COLOR_BLACK = "#000000";
    private static final String COLOR_DEFAULT_PATH = "#4CAF50";

    @Inject
    ObjectMapper objectMapper;

    /**
     * Configuration bundle for maze generation. Groups the parameters that control maze structure, rendering, and
     * solution/dead-end highlighting so the service API stays under SonarQube's java:S107 threshold.
     *
     * @param type
     *            Maze tessellation type
     * @param size
     *            1-20, controls cell count
     * @param difficulty
     *            1-5, controls shortcuts (1=many, 5=none)
     * @param seed
     *            Random seed (null for random)
     * @param showSolution
     *            Whether to show the solution path
     * @param innerWallColor
     *            Color for inner walls
     * @param outerWallColor
     *            Color for the outer border
     * @param pathColor
     *            Color for the solution path
     * @param showDeadEnds
     *            Whether to highlight dead-end paths
     * @param deadEndColor
     *            Color for dead-end highlighting (null for default)
     */
    public record MazeGenerationConfig(MazeType type, int size, int difficulty, Long seed, boolean showSolution,
            String innerWallColor, String outerWallColor, String pathColor, boolean showDeadEnds, String deadEndColor) {
    }

    /**
     * Convert size (1-20) to grid dimensions. Size 1 = ~15 cells wide (simple maze, ~2.2" paths) Size 20 = ~132 cells
     * wide (complex maze, ~0.25" / 1/4" paths) Height is calculated to maintain aspect ratio.
     */
    private int[] sizeToGridDimensions(int size) {
        // Clamp size to 1-20
        size = Math.max(1, Math.min(20, size));

        // Width: 15 cells at size 1, 132 cells at size 20
        // At 132 cells: 3300 printable units / 132 = 25 units per cell = 0.25" (1/4 inch)
        int width = 15 + (size - 1) * (132 - 15) / 19;

        // Height maintains aspect ratio of printable area
        int height = (int) Math.round(width / ASPECT_RATIO);

        return new int[]{width, height};
    }

    /**
     * Generate a maze and return the SVG representation. The maze fills the 35"x23" page with 1" margins on all sides.
     *
     * @param config
     *            Maze generation configuration (type, size, difficulty, seed, colors, solution/dead-end options)
     */
    public String generateMazeSvg(MazeGenerationConfig config) {
        int[] dims = sizeToGridDimensions(config.size());
        MazeGrid grid = new MazeGrid(dims[0], dims[1], config.type(), config.difficulty(), config.seed());
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, config.innerWallColor(), config.outerWallColor(),
                config.pathColor(), config.showSolution(), config.showDeadEnds(), config.deadEndColor());
        return renderer.render();
    }

    /**
     * Generate a maze for a UserMaze entity and update it with the SVG and solution. The maze fills the 35"x23" page
     * with 1" margins on all sides.
     */
    public void generateAndUpdateMaze(UserMaze maze) {
        int[] dims = sizeToGridDimensions(maze.size);
        MazeGrid grid = new MazeGrid(dims[0], dims[1], maze.mazeType, maze.difficulty, maze.seed);
        grid.generate();

        // Get configuration options
        boolean showSolution = getConfigBoolean(maze.configuration, "showSolution", false);
        String innerWallColor = getConfigString(maze.configuration, "innerWallColor", COLOR_BLACK);
        String outerWallColor = getConfigString(maze.configuration, "outerWallColor", COLOR_BLACK);
        String pathColor = getConfigString(maze.configuration, "pathColor", COLOR_DEFAULT_PATH);

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, innerWallColor, outerWallColor, pathColor, showSolution);
        maze.generatedSvg = renderer.render();

        // Store solution path
        List<int[]> solution = grid.getSolutionPath();
        if (solution != null) {
            ArrayNode solutionArray = objectMapper.createArrayNode();
            for (int[] coord : solution) {
                ArrayNode point = objectMapper.createArrayNode();
                point.add(coord[0]);
                point.add(coord[1]);
                solutionArray.add(point);
            }
            maze.solutionPath = solutionArray;
        }
    }

    /**
     * Generate a preview SVG for given parameters (without persisting). Uses a fixed seed for consistent preview.
     */
    public String generatePreview(MazeType type, int size, int difficulty, boolean showSolution, boolean showDeadEnds) {
        return generateMazeSvg(new MazeGenerationConfig(type, size, difficulty, 12345L, showSolution, COLOR_BLACK,
                COLOR_BLACK, COLOR_DEFAULT_PATH, showDeadEnds, null));
    }

    /**
     * Generate a preview SVG for given parameters (without persisting, no dead-end highlighting). Uses a fixed seed for
     * consistent preview.
     */
    public String generatePreview(MazeType type, int size, int difficulty, boolean showSolution) {
        return generatePreview(type, size, difficulty, showSolution, false);
    }

    /** Regenerate maze with a new random seed. */
    public void regenerateMaze(UserMaze maze) {
        maze.seed = System.currentTimeMillis();
        generateAndUpdateMaze(maze);
    }

    private boolean getConfigBoolean(JsonNode config, String key, boolean defaultValue) {
        if (config == null || !config.has(key)) {
            return defaultValue;
        }
        return config.get(key).asBoolean(defaultValue);
    }

    private String getConfigString(JsonNode config, String key, String defaultValue) {
        if (config == null || !config.has(key)) {
            return defaultValue;
        }
        return config.get(key).asText(defaultValue);
    }
}
