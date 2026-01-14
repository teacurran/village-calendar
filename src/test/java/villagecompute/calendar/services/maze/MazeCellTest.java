package villagecompute.calendar.services.maze;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.enums.MazeType;

/** Unit tests for MazeCell. */
class MazeCellTest {

    @Test
    void testConstructor() {
        MazeCell cell = new MazeCell(5, 10);

        assertEquals(5, cell.x);
        assertEquals(10, cell.y);
    }

    @Test
    void testInitialOrthogonalWallState() {
        MazeCell cell = new MazeCell(0, 0);

        assertTrue(cell.northWall);
        assertTrue(cell.southWall);
        assertTrue(cell.eastWall);
        assertTrue(cell.westWall);
    }

    @Test
    void testInitialHexagonalWallState() {
        MazeCell cell = new MazeCell(0, 0);

        assertTrue(cell.northEastWall);
        assertTrue(cell.southEastWall);
        assertTrue(cell.southWestWall);
        assertTrue(cell.northWestWall);
    }

    @Test
    void testInitialVisitedState() {
        MazeCell cell = new MazeCell(0, 0);

        assertFalse(cell.visited);
        assertFalse(cell.onSolutionPath);
        assertFalse(cell.isDeadEnd);
        assertEquals(0, cell.deadEndDepth);
        assertNull(cell.parent);
    }

    @Test
    void testRemoveWallToEast() {
        MazeCell cell = new MazeCell(0, 0);
        MazeCell east = new MazeCell(1, 0);

        cell.removeWallTo(east);

        assertFalse(cell.eastWall);
        assertFalse(east.westWall);
        assertTrue(cell.westWall);
        assertTrue(east.eastWall);
    }

    @Test
    void testRemoveWallToWest() {
        MazeCell cell = new MazeCell(1, 0);
        MazeCell west = new MazeCell(0, 0);

        cell.removeWallTo(west);

        assertFalse(cell.westWall);
        assertFalse(west.eastWall);
        assertTrue(cell.eastWall);
        assertTrue(west.westWall);
    }

    @Test
    void testRemoveWallToNorth() {
        MazeCell cell = new MazeCell(0, 1);
        MazeCell north = new MazeCell(0, 0);

        cell.removeWallTo(north);

        assertFalse(cell.northWall);
        assertFalse(north.southWall);
        assertTrue(cell.southWall);
        assertTrue(north.northWall);
    }

    @Test
    void testRemoveWallToSouth() {
        MazeCell cell = new MazeCell(0, 0);
        MazeCell south = new MazeCell(0, 1);

        cell.removeWallTo(south);

        assertFalse(cell.southWall);
        assertFalse(south.northWall);
        assertTrue(cell.northWall);
        assertTrue(south.southWall);
    }

    @Test
    void testRemoveHexWallToEast() {
        MazeCell cell = new MazeCell(0, 0);
        MazeCell east = new MazeCell(1, 0);

        cell.removeHexWallTo(east, MazeType.SIGMA);

        assertFalse(cell.eastWall);
        assertFalse(east.westWall);
    }

    @Test
    void testRemoveHexWallToWest() {
        MazeCell cell = new MazeCell(1, 0);
        MazeCell west = new MazeCell(0, 0);

        cell.removeHexWallTo(west, MazeType.SIGMA);

        assertFalse(cell.westWall);
        assertFalse(west.eastWall);
    }

    @Test
    void testRemoveHexWallToNorthWestEvenRow() {
        // Even row (y=0): NW neighbor is at (x-1, y-1)
        MazeCell cell = new MazeCell(1, 2);
        MazeCell nw = new MazeCell(0, 1);

        cell.removeHexWallTo(nw, MazeType.SIGMA);

        assertFalse(cell.northWestWall);
        assertFalse(nw.southEastWall);
    }

    @Test
    void testRemoveHexWallToNorthEastEvenRow() {
        // Even row (y=0): NE neighbor is at (x, y-1)
        MazeCell cell = new MazeCell(1, 2);
        MazeCell ne = new MazeCell(1, 1);

        cell.removeHexWallTo(ne, MazeType.SIGMA);

        assertFalse(cell.northEastWall);
        assertFalse(ne.southWestWall);
    }

    @Test
    void testRemoveHexWallToSouthWestEvenRow() {
        // Even row (y=0): SW neighbor is at (x-1, y+1)
        MazeCell cell = new MazeCell(1, 0);
        MazeCell sw = new MazeCell(0, 1);

        cell.removeHexWallTo(sw, MazeType.SIGMA);

        assertFalse(cell.southWestWall);
        assertFalse(sw.northEastWall);
    }

    @Test
    void testRemoveHexWallToSouthEastEvenRow() {
        // Even row (y=0): SE neighbor is at (x, y+1)
        MazeCell cell = new MazeCell(1, 0);
        MazeCell se = new MazeCell(1, 1);

        cell.removeHexWallTo(se, MazeType.SIGMA);

        assertFalse(cell.southEastWall);
        assertFalse(se.northWestWall);
    }

    @Test
    void testRemoveHexWallToNorthWestOddRow() {
        // Odd row (y=1): NW neighbor is at (x, y-1)
        MazeCell cell = new MazeCell(1, 1);
        MazeCell nw = new MazeCell(1, 0);

        cell.removeHexWallTo(nw, MazeType.SIGMA);

        assertFalse(cell.northWestWall);
        assertFalse(nw.southEastWall);
    }

    @Test
    void testRemoveHexWallToNorthEastOddRow() {
        // Odd row (y=1): NE neighbor is at (x+1, y-1)
        MazeCell cell = new MazeCell(1, 1);
        MazeCell ne = new MazeCell(2, 0);

        cell.removeHexWallTo(ne, MazeType.SIGMA);

        assertFalse(cell.northEastWall);
        assertFalse(ne.southWestWall);
    }

    @Test
    void testRemoveHexWallToSouthWestOddRow() {
        // Odd row (y=1): SW neighbor is at (x, y+1)
        MazeCell cell = new MazeCell(1, 1);
        MazeCell sw = new MazeCell(1, 2);

        cell.removeHexWallTo(sw, MazeType.SIGMA);

        assertFalse(cell.southWestWall);
        assertFalse(sw.northEastWall);
    }

    @Test
    void testRemoveHexWallToSouthEastOddRow() {
        // Odd row (y=1): SE neighbor is at (x+1, y+1)
        MazeCell cell = new MazeCell(1, 1);
        MazeCell se = new MazeCell(2, 2);

        cell.removeHexWallTo(se, MazeType.SIGMA);

        assertFalse(cell.southEastWall);
        assertFalse(se.northWestWall);
    }

    @Test
    void testRemoveHexWallToFallsBackForOrthogonal() {
        MazeCell cell = new MazeCell(0, 0);
        MazeCell east = new MazeCell(1, 0);

        // When called with ORTHOGONAL type, should use orthogonal wall removal
        cell.removeHexWallTo(east, MazeType.ORTHOGONAL);

        assertFalse(cell.eastWall);
        assertFalse(east.westWall);
    }

    @Test
    void testRemoveHexWallToFallsBackForDelta() {
        MazeCell cell = new MazeCell(0, 0);
        MazeCell south = new MazeCell(0, 1);

        cell.removeHexWallTo(south, MazeType.DELTA);

        assertFalse(cell.southWall);
        assertFalse(south.northWall);
    }

    @Test
    void testRemoveHexWallToFallsBackForTheta() {
        MazeCell cell = new MazeCell(0, 0);
        MazeCell east = new MazeCell(1, 0);

        cell.removeHexWallTo(east, MazeType.THETA);

        assertFalse(cell.eastWall);
        assertFalse(east.westWall);
    }

    @Test
    void testMutableFlags() {
        MazeCell cell = new MazeCell(0, 0);

        cell.visited = true;
        assertTrue(cell.visited);

        cell.onSolutionPath = true;
        assertTrue(cell.onSolutionPath);

        cell.isDeadEnd = true;
        assertTrue(cell.isDeadEnd);

        cell.deadEndDepth = 5;
        assertEquals(5, cell.deadEndDepth);

        MazeCell parent = new MazeCell(1, 1);
        cell.parent = parent;
        assertEquals(parent, cell.parent);
    }
}
