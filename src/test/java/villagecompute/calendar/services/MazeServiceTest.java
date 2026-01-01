package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.api.graphql.MazeGraphQL;
import villagecompute.calendar.data.models.UserMaze;
import villagecompute.calendar.data.models.enums.MazeType;

import io.quarkus.test.junit.QuarkusTest;

/** Unit tests for MazeService. Tests maze creation, updates, and retrieval. */
@QuarkusTest
class MazeServiceTest {

    private static final String TEST_MAZE_NAME = "Test Maze";
    private static final String INNER_WALL_COLOR_KEY = "innerWallColor";
    private static final String SHOW_SOLUTION_KEY = "showSolution";
    private static final int DEFAULT_SIZE = 10;
    private static final int DEFAULT_DIFFICULTY = 5;

    @Inject
    MazeService mazeService;

    private String testSessionId;

    @BeforeEach
    void setUp() {
        testSessionId = "test-maze-session-" + UUID.randomUUID();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        UserMaze.deleteAll();
    }

    private MazeGraphQL.MazeInput createInputWithSession() {
        MazeGraphQL.MazeInput input = new MazeGraphQL.MazeInput();
        input.sessionId = testSessionId;
        return input;
    }

    private MazeGraphQL.MazeInput createBasicMazeInput() {
        MazeGraphQL.MazeInput input = createInputWithSession();
        input.name = TEST_MAZE_NAME;
        input.mazeType = MazeType.ORTHOGONAL;
        input.size = DEFAULT_SIZE;
        input.difficulty = DEFAULT_DIFFICULTY;
        return input;
    }

    private UserMaze createAndPersistBasicMaze() {
        return mazeService.createMaze(createBasicMazeInput());
    }

    // ========== FIND BY ID TESTS ==========

    @Test
    @Transactional
    void testFindById_ExistingMaze() {
        UserMaze created = createAndPersistBasicMaze();

        UserMaze found = mazeService.findById(created.id);

        assertNotNull(found);
        assertEquals(created.id, found.id);
    }

    @Test
    @Transactional
    void testFindById_NonExistentMaze() {
        UUID nonExistentId = UUID.randomUUID();

        UserMaze found = mazeService.findById(nonExistentId);

        assertNull(found);
    }

    // ========== FIND BY SESSION TESTS ==========

    @Test
    @Transactional
    void testFindBySession_WithMazes() {
        MazeGraphQL.MazeInput input1 = createBasicMazeInput();
        input1.name = "Maze 1";
        mazeService.createMaze(input1);

        MazeGraphQL.MazeInput input2 = createBasicMazeInput();
        input2.name = "Maze 2";
        mazeService.createMaze(input2);

        List<UserMaze> mazes = mazeService.findBySession(testSessionId);

        assertEquals(2, mazes.size());
    }

    @Test
    @Transactional
    void testFindBySession_EmptySession() {
        String emptySessionId = "empty-session-" + UUID.randomUUID();

        List<UserMaze> mazes = mazeService.findBySession(emptySessionId);

        assertTrue(mazes.isEmpty());
    }

    @Test
    void testFindBySession_NullSession() {
        List<UserMaze> mazes = mazeService.findBySession(null);

        assertTrue(mazes.isEmpty());
    }

    @Test
    void testFindBySession_BlankSession() {
        List<UserMaze> mazes = mazeService.findBySession("   ");

        assertTrue(mazes.isEmpty());
    }

    // ========== CREATE MAZE TESTS ==========

    @Test
    @Transactional
    void testCreateMaze_WithAllFields() {
        MazeGraphQL.MazeInput input = createInputWithSession();
        input.name = "Custom Maze";
        input.mazeType = MazeType.ORTHOGONAL;
        input.size = 15;
        input.difficulty = 8;
        input.seed = 12345L;
        input.showSolution = true;
        input.innerWallColor = "#333333";
        input.outerWallColor = "#000000";
        input.pathColor = "#FF0000";

        UserMaze maze = mazeService.createMaze(input);

        assertNotNull(maze);
        assertNotNull(maze.id);
        assertEquals("Custom Maze", maze.name);
        assertEquals(MazeType.ORTHOGONAL, maze.mazeType);
        assertEquals(15, maze.size);
        assertEquals(8, maze.difficulty);
        assertEquals(12345L, maze.seed);
        assertEquals(testSessionId, maze.sessionId);
        assertNotNull(maze.configuration);
        assertTrue(maze.configuration.get(SHOW_SOLUTION_KEY).asBoolean());
        assertEquals("#333333", maze.configuration.get(INNER_WALL_COLOR_KEY).asText());
    }

    @Test
    @Transactional
    void testCreateMaze_WithDefaultValues() {
        MazeGraphQL.MazeInput input = createInputWithSession();

        UserMaze maze = mazeService.createMaze(input);

        assertNotNull(maze);
        assertEquals("My Maze", maze.name);
        assertEquals(MazeType.ORTHOGONAL, maze.mazeType);
        assertEquals(DEFAULT_SIZE, maze.size);
        assertEquals(DEFAULT_SIZE, maze.difficulty);
        assertNotNull(maze.seed);
    }

    @Test
    @Transactional
    void testCreateMaze_SizeClampedToMin() {
        MazeGraphQL.MazeInput input = createInputWithSession();
        input.size = -5;

        UserMaze maze = mazeService.createMaze(input);

        assertEquals(1, maze.size);
    }

    @Test
    @Transactional
    void testCreateMaze_SizeClampedToMax() {
        MazeGraphQL.MazeInput input = createInputWithSession();
        input.size = 100;

        UserMaze maze = mazeService.createMaze(input);

        assertEquals(20, maze.size);
    }

    @Test
    @Transactional
    void testCreateMaze_DifficultyClampedToMin() {
        MazeGraphQL.MazeInput input = createInputWithSession();
        input.difficulty = -10;

        UserMaze maze = mazeService.createMaze(input);

        assertEquals(1, maze.difficulty);
    }

    @Test
    @Transactional
    void testCreateMaze_DifficultyClampedToMax() {
        MazeGraphQL.MazeInput input = createInputWithSession();
        input.difficulty = 50;

        UserMaze maze = mazeService.createMaze(input);

        assertEquals(20, maze.difficulty);
    }

    @Test
    @Transactional
    void testCreateMaze_GeneratesSvg() {
        UserMaze maze = createAndPersistBasicMaze();

        assertNotNull(maze.generatedSvg);
        assertTrue(maze.generatedSvg.contains("<svg"));
    }

    // ========== UPDATE MAZE TESTS ==========

    @Test
    @Transactional
    void testUpdateMaze_UpdateName() {
        UserMaze maze = createAndPersistBasicMaze();

        MazeGraphQL.MazeInput updateInput = new MazeGraphQL.MazeInput();
        updateInput.name = "Updated Maze Name";

        UserMaze updated = mazeService.updateMaze(maze.id, updateInput);

        assertEquals("Updated Maze Name", updated.name);
    }

    @Test
    @Transactional
    void testUpdateMaze_ChangeSize_TriggersRegeneration() {
        MazeGraphQL.MazeInput createInput = createBasicMazeInput();
        createInput.size = DEFAULT_SIZE;
        UserMaze maze = mazeService.createMaze(createInput);
        String originalSvg = maze.generatedSvg;

        MazeGraphQL.MazeInput updateInput = new MazeGraphQL.MazeInput();
        updateInput.size = 15;

        UserMaze updated = mazeService.updateMaze(maze.id, updateInput);

        assertEquals(15, updated.size);
        assertNotEquals(originalSvg, updated.generatedSvg);
    }

    @Test
    @Transactional
    void testUpdateMaze_ChangeMazeType_TriggersRegeneration() {
        MazeGraphQL.MazeInput createInput = createBasicMazeInput();
        createInput.mazeType = MazeType.ORTHOGONAL;
        UserMaze maze = mazeService.createMaze(createInput);

        MazeGraphQL.MazeInput updateInput = new MazeGraphQL.MazeInput();
        updateInput.mazeType = MazeType.THETA;

        UserMaze updated = mazeService.updateMaze(maze.id, updateInput);

        assertEquals(MazeType.THETA, updated.mazeType);
    }

    @Test
    @Transactional
    void testUpdateMaze_UpdateConfiguration() {
        UserMaze maze = createAndPersistBasicMaze();

        MazeGraphQL.MazeInput updateInput = new MazeGraphQL.MazeInput();
        updateInput.showSolution = true;
        updateInput.innerWallColor = "#AABBCC";

        UserMaze updated = mazeService.updateMaze(maze.id, updateInput);

        assertTrue(updated.configuration.get(SHOW_SOLUTION_KEY).asBoolean());
        assertEquals("#AABBCC", updated.configuration.get(INNER_WALL_COLOR_KEY).asText());
    }

    @Test
    @Transactional
    void testUpdateMaze_NonExistentMaze_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();
        MazeGraphQL.MazeInput updateInput = new MazeGraphQL.MazeInput();
        updateInput.name = "New Name";

        assertThrows(IllegalArgumentException.class, () -> mazeService.updateMaze(nonExistentId, updateInput));
    }

    // ========== REGENERATE MAZE TESTS ==========

    @Test
    @Transactional
    void testRegenerateMaze_Success() {
        UserMaze maze = createAndPersistBasicMaze();

        UserMaze regenerated = mazeService.regenerateMaze(maze.id);

        assertNotNull(regenerated.generatedSvg);
    }

    @Test
    @Transactional
    void testRegenerateMaze_NonExistentMaze_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> mazeService.regenerateMaze(nonExistentId));
    }

    // ========== DELETE MAZE TESTS ==========

    @Test
    @Transactional
    void testDeleteMaze_Success() {
        UserMaze maze = createAndPersistBasicMaze();
        UUID mazeId = maze.id;

        boolean deleted = mazeService.deleteMaze(mazeId);

        assertTrue(deleted);
        assertNull(mazeService.findById(mazeId));
    }

    @Test
    @Transactional
    void testDeleteMaze_NonExistentMaze_ReturnsFalse() {
        UUID nonExistentId = UUID.randomUUID();

        boolean deleted = mazeService.deleteMaze(nonExistentId);

        assertFalse(deleted);
    }
}
