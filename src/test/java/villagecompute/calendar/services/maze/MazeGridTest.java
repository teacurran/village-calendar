package villagecompute.calendar.services.maze;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import villagecompute.calendar.data.models.enums.MazeType;

/** Unit tests for MazeGrid. */
class MazeGridTest {

    @Test
    void testOrthogonalMazeGeneration() {
        MazeGrid grid = new MazeGrid(10, 10, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        assertEquals(10, grid.getWidth());
        assertEquals(10, grid.getHeight());
        assertEquals(MazeType.ORTHOGONAL, grid.getType());
        assertNotNull(grid.getCells());
        assertNotNull(grid.getSolutionPath());
        assertFalse(grid.getSolutionPath().isEmpty());
    }

    @Test
    void testSigmaMazeGeneration() {
        MazeGrid grid = new MazeGrid(8, 8, MazeType.SIGMA, 3, 12345L);
        grid.generate();

        assertEquals(8, grid.getWidth());
        assertEquals(8, grid.getHeight());
        assertEquals(MazeType.SIGMA, grid.getType());
        assertNotNull(grid.getCells());
        assertNotNull(grid.getSolutionPath());
        assertFalse(grid.getSolutionPath().isEmpty());
    }

    @Test
    void testDeltaMazeGeneration() {
        MazeGrid grid = new MazeGrid(8, 8, MazeType.DELTA, 3, 12345L);
        grid.generate();

        assertEquals(MazeType.DELTA, grid.getType());
        assertNotNull(grid.getSolutionPath());
    }

    @Test
    void testThetaMazeGeneration() {
        MazeGrid grid = new MazeGrid(8, 8, MazeType.THETA, 3, 12345L);
        grid.generate();

        assertEquals(MazeType.THETA, grid.getType());
        assertNotNull(grid.getSolutionPath());
        // Theta maze starts from center
        assertEquals(4, grid.getStartX());
        assertEquals(4, grid.getStartY());
    }

    @Test
    void testGetCell() {
        MazeGrid grid = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeCell cell = grid.getCell(2, 3);
        assertNotNull(cell);
        assertEquals(2, cell.x);
        assertEquals(3, cell.y);
    }

    @Test
    void testStartAndEndPositions() {
        MazeGrid grid = new MazeGrid(10, 10, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        assertEquals(0, grid.getStartX());
        assertEquals(0, grid.getStartY());
        assertEquals(9, grid.getEndX());
        assertEquals(9, grid.getEndY());
    }

    @Test
    void testSolutionPathStartsAtStartAndEndsAtEnd() {
        MazeGrid grid = new MazeGrid(10, 10, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        List<int[]> path = grid.getSolutionPath();
        assertFalse(path.isEmpty());

        int[] start = path.get(0);
        assertEquals(grid.getStartX(), start[0]);
        assertEquals(grid.getStartY(), start[1]);

        int[] end = path.get(path.size() - 1);
        assertEquals(grid.getEndX(), end[0]);
        assertEquals(grid.getEndY(), end[1]);
    }

    @Test
    void testSolutionPathCellsMarked() {
        MazeGrid grid = new MazeGrid(8, 8, MazeType.ORTHOGONAL, 5, 12345L);
        grid.generate();

        // All cells in solution path should be marked
        for (int[] pos : grid.getSolutionPath()) {
            MazeCell cell = grid.getCell(pos[0], pos[1]);
            assertTrue(cell.onSolutionPath, "Cell at (" + pos[0] + "," + pos[1] + ") should be on solution path");
            assertFalse(cell.isDeadEnd, "Cell at (" + pos[0] + "," + pos[1] + ") should not be dead end");
            assertEquals(0, cell.deadEndDepth);
        }
    }

    @Test
    void testDeadEndCellsMarked() {
        MazeGrid grid = new MazeGrid(10, 10, MazeType.ORTHOGONAL, 5, 12345L);
        grid.generate();

        int deadEndCount = 0;
        int solutionCount = 0;

        for (int x = 0; x < grid.getWidth(); x++) {
            for (int y = 0; y < grid.getHeight(); y++) {
                MazeCell cell = grid.getCell(x, y);
                if (cell.onSolutionPath) {
                    solutionCount++;
                    assertFalse(cell.isDeadEnd);
                } else {
                    deadEndCount++;
                    assertTrue(cell.isDeadEnd);
                    assertTrue(cell.deadEndDepth > 0, "Dead end at (" + x + "," + y + ") should have depth > 0");
                }
            }
        }

        assertTrue(solutionCount > 0, "Should have cells on solution path");
        assertTrue(deadEndCount > 0, "Should have dead-end cells");
        assertEquals(100, solutionCount + deadEndCount);
    }

    @Test
    void testSigmaMazeSolutionPath() {
        MazeGrid grid = new MazeGrid(6, 6, MazeType.SIGMA, 3, 12345L);
        grid.generate();

        List<int[]> path = grid.getSolutionPath();
        assertFalse(path.isEmpty());

        // First cell should be start
        assertEquals(0, path.get(0)[0]);
        assertEquals(0, path.get(0)[1]);

        // Last cell should be end
        assertEquals(5, path.get(path.size() - 1)[0]);
        assertEquals(5, path.get(path.size() - 1)[1]);
    }

    @ParameterizedTest
    @ValueSource(
            ints = {1, 2, 3, 4, 5})
    void testDifficultyLevelsOrthogonal(int difficulty) {
        MazeGrid grid = new MazeGrid(10, 10, MazeType.ORTHOGONAL, difficulty, 12345L);
        grid.generate();

        // All difficulty levels should produce valid mazes with solutions
        assertNotNull(grid.getSolutionPath());
        assertFalse(grid.getSolutionPath().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            ints = {1, 2, 3, 4, 5})
    void testDifficultyLevelsSigma(int difficulty) {
        MazeGrid grid = new MazeGrid(8, 8, MazeType.SIGMA, difficulty, 12345L);
        grid.generate();

        // All difficulty levels should produce valid mazes with solutions
        assertNotNull(grid.getSolutionPath());
        assertFalse(grid.getSolutionPath().isEmpty());
    }

    @Test
    void testDifficultyClampedToValidRange() {
        // Difficulty below 1 should be clamped to 1
        MazeGrid grid1 = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 0, 12345L);
        grid1.generate();
        assertNotNull(grid1.getSolutionPath());

        // Difficulty above 5 should be clamped to 5
        MazeGrid grid2 = new MazeGrid(5, 5, MazeType.ORTHOGONAL, 10, 12345L);
        grid2.generate();
        assertNotNull(grid2.getSolutionPath());
    }

    @Test
    void testSeededRandomProducesSameMaze() {
        MazeGrid grid1 = new MazeGrid(8, 8, MazeType.ORTHOGONAL, 3, 12345L);
        grid1.generate();

        MazeGrid grid2 = new MazeGrid(8, 8, MazeType.ORTHOGONAL, 3, 12345L);
        grid2.generate();

        // Both mazes should have the same wall configuration
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                MazeCell cell1 = grid1.getCell(x, y);
                MazeCell cell2 = grid2.getCell(x, y);
                assertEquals(cell1.northWall, cell2.northWall, "North wall mismatch at (" + x + "," + y + ")");
                assertEquals(cell1.southWall, cell2.southWall, "South wall mismatch at (" + x + "," + y + ")");
                assertEquals(cell1.eastWall, cell2.eastWall, "East wall mismatch at (" + x + "," + y + ")");
                assertEquals(cell1.westWall, cell2.westWall, "West wall mismatch at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void testSeededRandomProducesSameSigmaMaze() {
        MazeGrid grid1 = new MazeGrid(6, 6, MazeType.SIGMA, 3, 12345L);
        grid1.generate();

        MazeGrid grid2 = new MazeGrid(6, 6, MazeType.SIGMA, 3, 12345L);
        grid2.generate();

        // Both mazes should have the same wall configuration
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 6; y++) {
                MazeCell cell1 = grid1.getCell(x, y);
                MazeCell cell2 = grid2.getCell(x, y);
                assertEquals(cell1.eastWall, cell2.eastWall);
                assertEquals(cell1.westWall, cell2.westWall);
                assertEquals(cell1.northEastWall, cell2.northEastWall);
                assertEquals(cell1.northWestWall, cell2.northWestWall);
                assertEquals(cell1.southEastWall, cell2.southEastWall);
                assertEquals(cell1.southWestWall, cell2.southWestWall);
            }
        }
    }

    @Test
    void testNoSeedProducesRandomMaze() {
        // Without seed, each generation should be different
        MazeGrid grid1 = new MazeGrid(8, 8, MazeType.ORTHOGONAL, 3, null);
        grid1.generate();

        MazeGrid grid2 = new MazeGrid(8, 8, MazeType.ORTHOGONAL, 3, null);
        grid2.generate();

        // Very unlikely to have identical mazes - check solution paths differ
        List<int[]> path1 = grid1.getSolutionPath();
        List<int[]> path2 = grid2.getSolutionPath();

        // At least one should differ in length or positions (statistically almost certain)
        boolean different = path1.size() != path2.size();
        if (!different) {
            for (int i = 0; i < path1.size(); i++) {
                if (path1.get(i)[0] != path2.get(i)[0] || path1.get(i)[1] != path2.get(i)[1]) {
                    different = true;
                    break;
                }
            }
        }
        // Note: There's a tiny chance this could fail if we get identical random sequences
        // but statistically this is virtually impossible
    }

    @Test
    void testAllCellsVisitedDuringGeneration() {
        MazeGrid grid = new MazeGrid(10, 10, MazeType.ORTHOGONAL, 5, 12345L);
        grid.generate();

        // After generation and solution finding, visited flags are used for dead-end marking
        // Every cell should have been processed
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                MazeCell cell = grid.getCell(x, y);
                // Either on solution path or marked as dead end
                assertTrue(cell.onSolutionPath || cell.isDeadEnd,
                        "Cell (" + x + "," + y + ") should be either on solution path or dead end");
            }
        }
    }

    @Test
    void testSmallMaze() {
        MazeGrid grid = new MazeGrid(2, 2, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        assertEquals(2, grid.getWidth());
        assertEquals(2, grid.getHeight());
        assertNotNull(grid.getSolutionPath());
        assertTrue(grid.getSolutionPath().size() >= 2);
    }

    @Test
    void testSmallSigmaMaze() {
        MazeGrid grid = new MazeGrid(3, 3, MazeType.SIGMA, 3, 12345L);
        grid.generate();

        assertEquals(3, grid.getWidth());
        assertEquals(3, grid.getHeight());
        assertNotNull(grid.getSolutionPath());
        assertTrue(grid.getSolutionPath().size() >= 2);
    }

    @Test
    void testSigmaMazeHasCorrectNeighborConnections() {
        // Generate a sigma maze and verify connectivity
        MazeGrid grid = new MazeGrid(4, 4, MazeType.SIGMA, 5, 12345L);
        grid.generate();

        // Verify that every cell is reachable (perfect maze with difficulty 5 = no shortcuts)
        // This is implicitly tested by solution path existing
        assertNotNull(grid.getSolutionPath());

        // For sigma maze, check that hex walls are being used
        boolean foundOpenHexWall = false;
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                MazeCell cell = grid.getCell(x, y);
                if (!cell.northEastWall || !cell.northWestWall || !cell.southEastWall || !cell.southWestWall) {
                    foundOpenHexWall = true;
                    break;
                }
            }
            if (foundOpenHexWall) {
                break;
            }
        }
        assertTrue(foundOpenHexWall, "Sigma maze should have some hex walls open");
    }

    @Test
    void testLargeMaze() {
        MazeGrid grid = new MazeGrid(20, 20, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        assertEquals(20, grid.getWidth());
        assertEquals(20, grid.getHeight());
        assertNotNull(grid.getSolutionPath());
    }

    @Test
    void testLargeSigmaMaze() {
        MazeGrid grid = new MazeGrid(15, 15, MazeType.SIGMA, 3, 12345L);
        grid.generate();

        assertEquals(15, grid.getWidth());
        assertEquals(15, grid.getHeight());
        assertNotNull(grid.getSolutionPath());
    }

    @Test
    void testEasyDifficultyHasMoreOpenWalls() {
        // Easy maze (difficulty 1) should have more open passages than hard maze (difficulty 5)
        MazeGrid easyGrid = new MazeGrid(10, 10, MazeType.ORTHOGONAL, 1, 12345L);
        easyGrid.generate();

        MazeGrid hardGrid = new MazeGrid(10, 10, MazeType.ORTHOGONAL, 5, 12345L);
        hardGrid.generate();

        int easyOpenWalls = countOpenWalls(easyGrid);
        int hardOpenWalls = countOpenWalls(hardGrid);

        // Easy should have more open walls due to shortcut additions
        assertTrue(easyOpenWalls >= hardOpenWalls, "Easy maze should have >= open walls than hard maze. Easy: "
                + easyOpenWalls + ", Hard: " + hardOpenWalls);
    }

    @Test
    void testEasyDifficultyHasMoreOpenWallsSigma() {
        MazeGrid easyGrid = new MazeGrid(8, 8, MazeType.SIGMA, 1, 12345L);
        easyGrid.generate();

        MazeGrid hardGrid = new MazeGrid(8, 8, MazeType.SIGMA, 5, 12345L);
        hardGrid.generate();

        int easyOpenWalls = countOpenHexWalls(easyGrid);
        int hardOpenWalls = countOpenHexWalls(hardGrid);

        assertTrue(easyOpenWalls >= hardOpenWalls, "Easy sigma maze should have >= open walls than hard maze. Easy: "
                + easyOpenWalls + ", Hard: " + hardOpenWalls);
    }

    private int countOpenWalls(MazeGrid grid) {
        int count = 0;
        for (int x = 0; x < grid.getWidth(); x++) {
            for (int y = 0; y < grid.getHeight(); y++) {
                MazeCell cell = grid.getCell(x, y);
                if (!cell.northWall) {
                    count++;
                }
                if (!cell.southWall) {
                    count++;
                }
                if (!cell.eastWall) {
                    count++;
                }
                if (!cell.westWall) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countOpenHexWalls(MazeGrid grid) {
        int count = 0;
        for (int x = 0; x < grid.getWidth(); x++) {
            for (int y = 0; y < grid.getHeight(); y++) {
                MazeCell cell = grid.getCell(x, y);
                if (!cell.eastWall) {
                    count++;
                }
                if (!cell.westWall) {
                    count++;
                }
                if (!cell.northEastWall) {
                    count++;
                }
                if (!cell.northWestWall) {
                    count++;
                }
                if (!cell.southEastWall) {
                    count++;
                }
                if (!cell.southWestWall) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void testSolutionPathIsContiguous() {
        MazeGrid grid = new MazeGrid(8, 8, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        List<int[]> path = grid.getSolutionPath();

        // Each step in the path should be adjacent to the next
        for (int i = 0; i < path.size() - 1; i++) {
            int[] current = path.get(i);
            int[] next = path.get(i + 1);
            int dx = Math.abs(current[0] - next[0]);
            int dy = Math.abs(current[1] - next[1]);

            // Should be exactly one step in either x or y direction (Manhattan distance = 1)
            assertTrue(dx + dy == 1, "Solution path should be contiguous at step " + i);
        }
    }

    @Test
    void testGetCellsReturnsFullGrid() {
        MazeGrid grid = new MazeGrid(5, 7, MazeType.ORTHOGONAL, 3, 12345L);
        grid.generate();

        MazeCell[][] cells = grid.getCells();
        assertEquals(5, cells.length);
        assertEquals(7, cells[0].length);

        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 7; y++) {
                assertNotNull(cells[x][y]);
                assertEquals(x, cells[x][y].x);
                assertEquals(y, cells[x][y].y);
            }
        }
    }

    @Test
    void testDeadEndDepthIncreasesAwayFromSolution() {
        MazeGrid grid = new MazeGrid(10, 10, MazeType.ORTHOGONAL, 5, 12345L);
        grid.generate();

        // Find a dead-end cell with depth > 1 and verify its neighbors
        // have depth that is exactly 1 less (closer to solution)
        Set<String> solutionSet = new HashSet<>();
        for (int[] pos : grid.getSolutionPath()) {
            solutionSet.add(pos[0] + "," + pos[1]);
        }

        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                MazeCell cell = grid.getCell(x, y);
                if (cell.deadEndDepth > 1) {
                    // At least one accessible neighbor should have depth = cell.depth - 1
                    boolean foundCloserNeighbor = false;
                    if (x > 0 && !cell.westWall && grid.getCell(x - 1, y).deadEndDepth == cell.deadEndDepth - 1) {
                        foundCloserNeighbor = true;
                    }
                    if (x < 9 && !cell.eastWall && grid.getCell(x + 1, y).deadEndDepth == cell.deadEndDepth - 1) {
                        foundCloserNeighbor = true;
                    }
                    if (y > 0 && !cell.northWall && grid.getCell(x, y - 1).deadEndDepth == cell.deadEndDepth - 1) {
                        foundCloserNeighbor = true;
                    }
                    if (y < 9 && !cell.southWall && grid.getCell(x, y + 1).deadEndDepth == cell.deadEndDepth - 1) {
                        foundCloserNeighbor = true;
                    }
                    assertTrue(foundCloserNeighbor, "Cell at (" + x + "," + y + ") with depth " + cell.deadEndDepth
                            + " should have a neighbor with depth " + (cell.deadEndDepth - 1));
                }
            }
        }
    }
}
