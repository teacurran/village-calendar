package villagecompute.calendar.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import villagecompute.calendar.data.models.UserMaze;
import villagecompute.calendar.data.models.enums.MazeType;
import villagecompute.calendar.services.maze.MazeGrid;
import villagecompute.calendar.services.maze.MazeSvgRenderer;

import java.util.List;

/**
 * Service for generating mazes and their SVG representations.
 * Size (1-20) controls the number of cells.
 * Difficulty (1-20) controls the solution path length.
 */
@ApplicationScoped
public class MazeGenerationService {

    // Printable area aspect ratio (33" x 21")
    private static final double ASPECT_RATIO = 33.0 / 21.0; // ~1.57

    @Inject
    ObjectMapper objectMapper;

    /**
     * Convert size (1-20) to grid dimensions.
     * Size 1 = ~15 cells wide (simple maze)
     * Size 20 = ~100 cells wide (complex maze)
     * Height is calculated to maintain aspect ratio.
     */
    private int[] sizeToGridDimensions(int size) {
        // Clamp size to 1-20
        size = Math.max(1, Math.min(20, size));

        // Width: 15 cells at size 1, 100 cells at size 20
        int width = 15 + (size - 1) * (100 - 15) / 19;

        // Height maintains aspect ratio of printable area
        int height = (int) Math.round(width / ASPECT_RATIO);

        return new int[]{width, height};
    }

    /**
     * Generate a maze and return the SVG representation.
     * The maze fills the 35"x23" page with 1" margins on all sides.
     *
     * @param type       Maze tessellation type
     * @param size       1-20, controls cell count
     * @param difficulty 1-20, controls solution path length
     * @param seed       Random seed (null for random)
     * @param showSolution Whether to show the solution path
     */
    public String generateMazeSvg(MazeType type, int size, int difficulty, Long seed, boolean showSolution,
                                   String innerWallColor, String outerWallColor, String pathColor) {
        int[] dims = sizeToGridDimensions(size);
        MazeGrid grid = new MazeGrid(dims[0], dims[1], type, difficulty, seed);
        grid.generate();

        MazeSvgRenderer renderer = new MazeSvgRenderer(grid, innerWallColor, outerWallColor, pathColor, showSolution);
        return renderer.render();
    }

    /**
     * Generate a maze for a UserMaze entity and update it with the SVG and solution.
     * The maze fills the 35"x23" page with 1" margins on all sides.
     */
    public void generateAndUpdateMaze(UserMaze maze) {
        int[] dims = sizeToGridDimensions(maze.size);
        MazeGrid grid = new MazeGrid(
            dims[0],
            dims[1],
            maze.mazeType,
            maze.difficulty,
            maze.seed
        );
        grid.generate();

        // Get configuration options
        boolean showSolution = getConfigBoolean(maze.configuration, "showSolution", false);
        String innerWallColor = getConfigString(maze.configuration, "innerWallColor", "#000000");
        String outerWallColor = getConfigString(maze.configuration, "outerWallColor", "#000000");
        String pathColor = getConfigString(maze.configuration, "pathColor", "#4CAF50");

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
     * Generate a preview SVG for given parameters (without persisting).
     * Uses a fixed seed for consistent preview.
     */
    public String generatePreview(MazeType type, int size, int difficulty, boolean showSolution) {
        return generateMazeSvg(type, size, difficulty, 12345L, showSolution, "#000000", "#000000", "#4CAF50");
    }

    /**
     * Regenerate maze with a new random seed.
     */
    public void regenerateMaze(UserMaze maze) {
        maze.seed = System.currentTimeMillis();
        generateAndUpdateMaze(maze);
    }

    private boolean getConfigBoolean(JsonNode config, String key, boolean defaultValue) {
        if (config == null || !config.has(key)) return defaultValue;
        return config.get(key).asBoolean(defaultValue);
    }

    private String getConfigString(JsonNode config, String key, String defaultValue) {
        if (config == null || !config.has(key)) return defaultValue;
        return config.get(key).asText(defaultValue);
    }
}
