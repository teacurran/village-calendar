package villagecompute.calendar.services.maze;

/**
 * Represents a single cell in a maze grid.
 */
public class MazeCell {
    public final int x;
    public final int y;

    // Walls: true means wall exists
    public boolean northWall = true;
    public boolean southWall = true;
    public boolean eastWall = true;
    public boolean westWall = true;

    public boolean visited = false;

    // For solution path finding
    public boolean onSolutionPath = false;
    public MazeCell parent = null;

    // For visualization - cells not on solution path are dead ends (false paths)
    public boolean isDeadEnd = false;
    public int deadEndDepth = 0; // How deep into a dead-end branch (0 = on solution path)

    public MazeCell(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void removeWallTo(MazeCell other) {
        int dx = other.x - this.x;
        int dy = other.y - this.y;

        if (dx == 1) {
            this.eastWall = false;
            other.westWall = false;
        } else if (dx == -1) {
            this.westWall = false;
            other.eastWall = false;
        } else if (dy == 1) {
            this.southWall = false;
            other.northWall = false;
        } else if (dy == -1) {
            this.northWall = false;
            other.southWall = false;
        }
    }
}
