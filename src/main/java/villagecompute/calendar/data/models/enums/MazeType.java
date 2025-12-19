package villagecompute.calendar.data.models.enums;

/**
 * Types of maze tessellations.
 */
public enum MazeType {
    /**
     * Standard rectangular grid where cells have passages intersecting at right angles.
     * Also known as Gamma Maze in tessellation terminology.
     * Each cell can have up to 4 passages (N, S, E, W).
     */
    ORTHOGONAL,

    /**
     * Maze composed of interlocking triangles.
     * Each cell may have up to 3 passages connected to it.
     */
    DELTA,

    /**
     * Maze composed of interlocking hexagons.
     * Each cell may have up to 6 passages connected to it.
     */
    SIGMA,

    /**
     * Maze composed of concentric circles of passages.
     * Start or finish is in the center, the other on the outer edge.
     * Cells usually have 4 possible passage connections, but may have more
     * due to the greater number of cells in outer passage rings.
     */
    THETA
}
