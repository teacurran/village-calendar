package villagecompute.calendar.services.maze;

import villagecompute.calendar.data.models.enums.MazeType;

/** Represents a single cell in a maze grid. */
public class MazeCell {
    public final int x;
    public final int y;

    // Walls for orthogonal mazes: true means wall exists
    public boolean northWall = true;
    public boolean southWall = true;
    public boolean eastWall = true;
    public boolean westWall = true;

    // Additional walls for hexagonal (sigma) mazes - pointy-top hexagons
    // Directions: N, NE, SE, S, SW, NW (reusing northWall and southWall)
    public boolean northEastWall = true;
    public boolean southEastWall = true;
    public boolean southWestWall = true;
    public boolean northWestWall = true;

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

    /** Remove wall to neighbor for orthogonal mazes. */
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

    /**
     * Remove wall to neighbor for hexagonal (sigma) mazes. Uses offset coordinates where odd rows are shifted right.
     */
    public void removeHexWallTo(MazeCell other, MazeType type) {
        if (type != MazeType.SIGMA) {
            removeWallTo(other);
            return;
        }

        int dx = other.x - this.x;
        int dy = other.y - this.y;
        boolean evenRow = (this.y % 2) == 0;

        // Pointy-top hexagon neighbors depend on whether we're in an even or odd row
        if (dy == -1) {
            // Moving north (up)
            if (evenRow) {
                if (dx == -1) {
                    // NW neighbor
                    this.northWestWall = false;
                    other.southEastWall = false;
                } else if (dx == 0) {
                    // NE neighbor
                    this.northEastWall = false;
                    other.southWestWall = false;
                }
            } else {
                if (dx == 0) {
                    // NW neighbor
                    this.northWestWall = false;
                    other.southEastWall = false;
                } else if (dx == 1) {
                    // NE neighbor
                    this.northEastWall = false;
                    other.southWestWall = false;
                }
            }
        } else if (dy == 1) {
            // Moving south (down)
            if (evenRow) {
                if (dx == -1) {
                    // SW neighbor
                    this.southWestWall = false;
                    other.northEastWall = false;
                } else if (dx == 0) {
                    // SE neighbor
                    this.southEastWall = false;
                    other.northWestWall = false;
                }
            } else {
                if (dx == 0) {
                    // SW neighbor
                    this.southWestWall = false;
                    other.northEastWall = false;
                } else if (dx == 1) {
                    // SE neighbor
                    this.southEastWall = false;
                    other.northWestWall = false;
                }
            }
        } else if (dy == 0) {
            // Moving east/west (same row)
            if (dx == 1) {
                this.eastWall = false;
                other.westWall = false;
            } else if (dx == -1) {
                this.westWall = false;
                other.eastWall = false;
            }
        }
    }
}
