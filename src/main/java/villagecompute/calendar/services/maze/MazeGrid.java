package villagecompute.calendar.services.maze;

import java.util.*;

import villagecompute.calendar.data.models.enums.MazeType;

/** Represents a maze grid and provides generation algorithms. */
public class MazeGrid {
    private final int width;
    private final int height;
    private final MazeType type;
    private final MazeCell[][] cells;
    private final Random random;
    private final int difficulty;

    // Start and end positions
    private int startX = 0;
    private int startY = 0;
    private int endX;
    private int endY;

    // Solution path
    private List<int[]> solutionPath;

    public MazeGrid(int width, int height, MazeType type, int difficulty, Long seed) {
        this.width = width;
        this.height = height;
        this.type = type;
        this.difficulty = Math.max(1, Math.min(5, difficulty));
        this.random = seed != null ? new Random(seed) : new Random();
        this.cells = new MazeCell[width][height];
        this.endX = width - 1;
        this.endY = height - 1;

        // Initialize cells
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                cells[x][y] = new MazeCell(x, y);
            }
        }
    }

    public void generate() {
        switch (type) {
            case ORTHOGONAL :
                generateOrthogonal();
                break;
            case DELTA :
                generateDelta();
                break;
            case SIGMA :
                generateSigma();
                break;
            case THETA :
                generateTheta();
                break;
        }
        findSolution();
        markDeadEnds();
    }

    /** Generate orthogonal maze using recursive backtracker algorithm. */
    private void generateOrthogonal() {
        Stack<MazeCell> stack = new Stack<>();
        MazeCell current = cells[startX][startY];
        current.visited = true;
        stack.push(current);

        while (!stack.isEmpty()) {
            current = stack.peek();
            List<MazeCell> unvisitedNeighbors = getUnvisitedNeighbors(current);

            if (unvisitedNeighbors.isEmpty()) {
                stack.pop();
            } else {
                MazeCell next = unvisitedNeighbors.get(random.nextInt(unvisitedNeighbors.size()));
                current.removeWallTo(next);
                next.visited = true;
                stack.push(next);
            }
        }

        // Apply difficulty modifications
        applyDifficultyModifications();
    }

    /**
     * Generate delta (triangular) maze. For now, using a modified orthogonal approach with different neighbor logic.
     */
    private void generateDelta() {
        // Delta mazes use triangular cells - simplified version for now
        // Uses same algorithm but rendering will handle triangular display
        generateOrthogonal();
    }

    /** Generate sigma (hexagonal) maze using recursive backtracker algorithm with hex neighbors. */
    private void generateSigma() {
        Stack<MazeCell> stack = new Stack<>();
        MazeCell current = cells[startX][startY];
        current.visited = true;
        stack.push(current);

        while (!stack.isEmpty()) {
            current = stack.peek();
            List<MazeCell> unvisitedNeighbors = getUnvisitedHexNeighbors(current);

            if (unvisitedNeighbors.isEmpty()) {
                stack.pop();
            } else {
                MazeCell next = unvisitedNeighbors.get(random.nextInt(unvisitedNeighbors.size()));
                current.removeHexWallTo(next, type);
                next.visited = true;
                stack.push(next);
            }
        }

        // Apply difficulty modifications
        applyHexDifficultyModifications();
    }

    /** Generate theta (circular) maze. */
    private void generateTheta() {
        // Theta mazes are circular - simplified version for now
        // Start from center, end at edge
        startX = width / 2;
        startY = height / 2;
        endX = 0;
        endY = height / 2;
        generateOrthogonal();
    }

    private List<MazeCell> getUnvisitedNeighbors(MazeCell cell) {
        List<MazeCell> neighbors = new ArrayList<>();
        int x = cell.x;
        int y = cell.y;

        if (x > 0 && !cells[x - 1][y].visited)
            neighbors.add(cells[x - 1][y]);
        if (x < width - 1 && !cells[x + 1][y].visited)
            neighbors.add(cells[x + 1][y]);
        if (y > 0 && !cells[x][y - 1].visited)
            neighbors.add(cells[x][y - 1]);
        if (y < height - 1 && !cells[x][y + 1].visited)
            neighbors.add(cells[x][y + 1]);

        return neighbors;
    }

    /**
     * Get unvisited neighbors for hexagonal (sigma) maze. Uses offset coordinates where odd rows are shifted right.
     * Pointy-top hexagons have 6 neighbors: NW, NE, E, SE, SW, W.
     */
    private List<MazeCell> getUnvisitedHexNeighbors(MazeCell cell) {
        List<MazeCell> neighbors = new ArrayList<>();
        int x = cell.x;
        int y = cell.y;
        boolean evenRow = (y % 2) == 0;

        // East neighbor (same for all rows)
        if (x < width - 1 && !cells[x + 1][y].visited) {
            neighbors.add(cells[x + 1][y]);
        }
        // West neighbor (same for all rows)
        if (x > 0 && !cells[x - 1][y].visited) {
            neighbors.add(cells[x - 1][y]);
        }

        if (evenRow) {
            // Even row: NW is at (x-1, y-1), NE is at (x, y-1)
            if (y > 0) {
                if (x > 0 && !cells[x - 1][y - 1].visited) {
                    neighbors.add(cells[x - 1][y - 1]); // NW
                }
                if (!cells[x][y - 1].visited) {
                    neighbors.add(cells[x][y - 1]); // NE
                }
            }
            // Even row: SW is at (x-1, y+1), SE is at (x, y+1)
            if (y < height - 1) {
                if (x > 0 && !cells[x - 1][y + 1].visited) {
                    neighbors.add(cells[x - 1][y + 1]); // SW
                }
                if (!cells[x][y + 1].visited) {
                    neighbors.add(cells[x][y + 1]); // SE
                }
            }
        } else {
            // Odd row: NW is at (x, y-1), NE is at (x+1, y-1)
            if (y > 0) {
                if (!cells[x][y - 1].visited) {
                    neighbors.add(cells[x][y - 1]); // NW
                }
                if (x < width - 1 && !cells[x + 1][y - 1].visited) {
                    neighbors.add(cells[x + 1][y - 1]); // NE
                }
            }
            // Odd row: SW is at (x, y+1), SE is at (x+1, y+1)
            if (y < height - 1) {
                if (!cells[x][y + 1].visited) {
                    neighbors.add(cells[x][y + 1]); // SW
                }
                if (x < width - 1 && !cells[x + 1][y + 1].visited) {
                    neighbors.add(cells[x + 1][y + 1]); // SE
                }
            }
        }

        return neighbors;
    }

    /**
     * Apply difficulty-based modifications to the maze. Difficulty 1-5: 1 = Very easy (many shortcuts - about 25% of
     * cells get extra openings) 2 = Easy (some shortcuts - about 12.5% of cells get extra openings) 3 = Medium (few
     * shortcuts - about 5% of cells get extra openings) 4 = Hard (minimal shortcuts - about 2% of cells get extra
     * openings) 5 = Very hard (pure perfect maze - no shortcuts, single solution path)
     *
     * <p>
     * The recursive backtracker creates a "perfect" maze with exactly one path between any two points. Adding shortcuts
     * (removing walls) creates alternative routes that can make the solution shorter and easier to find.
     */
    private void applyDifficultyModifications() {
        int totalCells = width * height;

        switch (difficulty) {
            case 1 :
                // Very easy: Remove many walls to create multiple shortcuts
                removeWallsForShortcuts(totalCells / 4);
                break;
            case 2 :
                // Easy: Remove some walls
                removeWallsForShortcuts(totalCells / 8);
                break;
            case 3 :
                // Medium: Remove few walls
                removeWallsForShortcuts(totalCells / 20);
                break;
            case 4 :
                // Hard: Remove very few walls
                removeWallsForShortcuts(totalCells / 50);
                break;
            case 5 :
            default :
                // Very hard: Pure perfect maze, no modifications
                // The recursive backtracker already creates the hardest possible maze
                break;
        }
    }

    /** Remove walls to create shortcuts, making the maze easier. */
    private void removeWallsForShortcuts(int count) {
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            MazeCell cell = cells[x][y];

            // Only remove if cell doesn't already have all passages open
            List<MazeCell> blocked = getBlockedNeighbors(cell);
            if (!blocked.isEmpty()) {
                MazeCell neighbor = blocked.get(random.nextInt(blocked.size()));
                cell.removeWallTo(neighbor);
            }
        }
    }

    private List<MazeCell> getAccessibleNeighbors(MazeCell cell) {
        List<MazeCell> neighbors = new ArrayList<>();
        int x = cell.x;
        int y = cell.y;

        if (x > 0 && !cell.westWall)
            neighbors.add(cells[x - 1][y]);
        if (x < width - 1 && !cell.eastWall)
            neighbors.add(cells[x + 1][y]);
        if (y > 0 && !cell.northWall)
            neighbors.add(cells[x][y - 1]);
        if (y < height - 1 && !cell.southWall)
            neighbors.add(cells[x][y + 1]);

        return neighbors;
    }

    private List<MazeCell> getBlockedNeighbors(MazeCell cell) {
        List<MazeCell> neighbors = new ArrayList<>();
        int x = cell.x;
        int y = cell.y;

        if (x > 0 && cell.westWall)
            neighbors.add(cells[x - 1][y]);
        if (x < width - 1 && cell.eastWall)
            neighbors.add(cells[x + 1][y]);
        if (y > 0 && cell.northWall)
            neighbors.add(cells[x][y - 1]);
        if (y < height - 1 && cell.southWall)
            neighbors.add(cells[x][y + 1]);

        return neighbors;
    }

    /** Get accessible neighbors for hexagonal maze (cells connected by open passages). */
    private List<MazeCell> getAccessibleHexNeighbors(MazeCell cell) {
        List<MazeCell> neighbors = new ArrayList<>();
        int x = cell.x;
        int y = cell.y;
        boolean evenRow = (y % 2) == 0;

        // East neighbor
        if (x < width - 1 && !cell.eastWall) {
            neighbors.add(cells[x + 1][y]);
        }
        // West neighbor
        if (x > 0 && !cell.westWall) {
            neighbors.add(cells[x - 1][y]);
        }

        if (evenRow) {
            // NW neighbor at (x-1, y-1)
            if (y > 0 && x > 0 && !cell.northWestWall) {
                neighbors.add(cells[x - 1][y - 1]);
            }
            // NE neighbor at (x, y-1)
            if (y > 0 && !cell.northEastWall) {
                neighbors.add(cells[x][y - 1]);
            }
            // SW neighbor at (x-1, y+1)
            if (y < height - 1 && x > 0 && !cell.southWestWall) {
                neighbors.add(cells[x - 1][y + 1]);
            }
            // SE neighbor at (x, y+1)
            if (y < height - 1 && !cell.southEastWall) {
                neighbors.add(cells[x][y + 1]);
            }
        } else {
            // NW neighbor at (x, y-1)
            if (y > 0 && !cell.northWestWall) {
                neighbors.add(cells[x][y - 1]);
            }
            // NE neighbor at (x+1, y-1)
            if (y > 0 && x < width - 1 && !cell.northEastWall) {
                neighbors.add(cells[x + 1][y - 1]);
            }
            // SW neighbor at (x, y+1)
            if (y < height - 1 && !cell.southWestWall) {
                neighbors.add(cells[x][y + 1]);
            }
            // SE neighbor at (x+1, y+1)
            if (y < height - 1 && x < width - 1 && !cell.southEastWall) {
                neighbors.add(cells[x + 1][y + 1]);
            }
        }

        return neighbors;
    }

    /** Get blocked neighbors for hexagonal maze (cells separated by walls). */
    private List<MazeCell> getBlockedHexNeighbors(MazeCell cell) {
        List<MazeCell> neighbors = new ArrayList<>();
        int x = cell.x;
        int y = cell.y;
        boolean evenRow = (y % 2) == 0;

        // East neighbor
        if (x < width - 1 && cell.eastWall) {
            neighbors.add(cells[x + 1][y]);
        }
        // West neighbor
        if (x > 0 && cell.westWall) {
            neighbors.add(cells[x - 1][y]);
        }

        if (evenRow) {
            if (y > 0 && x > 0 && cell.northWestWall) {
                neighbors.add(cells[x - 1][y - 1]);
            }
            if (y > 0 && cell.northEastWall) {
                neighbors.add(cells[x][y - 1]);
            }
            if (y < height - 1 && x > 0 && cell.southWestWall) {
                neighbors.add(cells[x - 1][y + 1]);
            }
            if (y < height - 1 && cell.southEastWall) {
                neighbors.add(cells[x][y + 1]);
            }
        } else {
            if (y > 0 && cell.northWestWall) {
                neighbors.add(cells[x][y - 1]);
            }
            if (y > 0 && x < width - 1 && cell.northEastWall) {
                neighbors.add(cells[x + 1][y - 1]);
            }
            if (y < height - 1 && cell.southWestWall) {
                neighbors.add(cells[x][y + 1]);
            }
            if (y < height - 1 && x < width - 1 && cell.southEastWall) {
                neighbors.add(cells[x + 1][y + 1]);
            }
        }

        return neighbors;
    }

    /** Apply difficulty modifications for hexagonal mazes. */
    private void applyHexDifficultyModifications() {
        int totalCells = width * height;

        switch (difficulty) {
            case 1 :
                removeHexWallsForShortcuts(totalCells / 4);
                break;
            case 2 :
                removeHexWallsForShortcuts(totalCells / 8);
                break;
            case 3 :
                removeHexWallsForShortcuts(totalCells / 20);
                break;
            case 4 :
                removeHexWallsForShortcuts(totalCells / 50);
                break;
            case 5 :
            default :
                break;
        }
    }

    /** Remove walls to create shortcuts in hexagonal maze. */
    private void removeHexWallsForShortcuts(int count) {
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            MazeCell cell = cells[x][y];

            List<MazeCell> blocked = getBlockedHexNeighbors(cell);
            if (!blocked.isEmpty()) {
                MazeCell neighbor = blocked.get(random.nextInt(blocked.size()));
                cell.removeHexWallTo(neighbor, type);
            }
        }
    }

    /** Get accessible neighbors based on maze type. */
    private List<MazeCell> getAccessibleNeighborsForType(MazeCell cell) {
        if (type == MazeType.SIGMA) {
            return getAccessibleHexNeighbors(cell);
        }
        return getAccessibleNeighbors(cell);
    }

    /** Find the solution path using BFS. */
    private void findSolution() {
        // Reset visited flags
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                cells[x][y].visited = false;
                cells[x][y].parent = null;
            }
        }

        Queue<MazeCell> queue = new LinkedList<>();
        MazeCell start = cells[startX][startY];
        start.visited = true;
        queue.add(start);

        while (!queue.isEmpty()) {
            MazeCell current = queue.poll();

            if (current.x == endX && current.y == endY) {
                // Found the end - trace back the path
                solutionPath = new ArrayList<>();
                MazeCell pathCell = current;
                while (pathCell != null) {
                    pathCell.onSolutionPath = true;
                    solutionPath.add(0, new int[]{pathCell.x, pathCell.y});
                    pathCell = pathCell.parent;
                }
                return;
            }

            for (MazeCell neighbor : getAccessibleNeighborsForType(current)) {
                if (!neighbor.visited) {
                    neighbor.visited = true;
                    neighbor.parent = current;
                    queue.add(neighbor);
                }
            }
        }
    }

    /**
     * Mark all cells that are not on the solution path as dead ends. Also calculates the depth of each dead end (how
     * far from the solution path).
     */
    private void markDeadEnds() {
        // First pass: mark all non-solution cells as dead ends
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                MazeCell cell = cells[x][y];
                cell.isDeadEnd = !cell.onSolutionPath;
            }
        }

        // Second pass: calculate dead-end depth using BFS from solution path
        // Reset visited flags
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                cells[x][y].visited = false;
            }
        }

        Queue<MazeCell> queue = new LinkedList<>();

        // Start from all cells on the solution path
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                MazeCell cell = cells[x][y];
                if (cell.onSolutionPath) {
                    cell.deadEndDepth = 0;
                    cell.visited = true;
                    queue.add(cell);
                }
            }
        }

        // BFS to calculate depth of each dead-end cell
        while (!queue.isEmpty()) {
            MazeCell current = queue.poll();

            for (MazeCell neighbor : getAccessibleNeighborsForType(current)) {
                if (!neighbor.visited) {
                    neighbor.visited = true;
                    neighbor.deadEndDepth = current.deadEndDepth + 1;
                    queue.add(neighbor);
                }
            }
        }
    }

    // Getters
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public MazeType getType() {
        return type;
    }

    public MazeCell[][] getCells() {
        return cells;
    }

    public MazeCell getCell(int x, int y) {
        return cells[x][y];
    }

    public int getStartX() {
        return startX;
    }

    public int getStartY() {
        return startY;
    }

    public int getEndX() {
        return endX;
    }

    public int getEndY() {
        return endY;
    }

    public List<int[]> getSolutionPath() {
        return solutionPath;
    }
}
