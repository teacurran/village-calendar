package villagecompute.calendar.api.graphql;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import villagecompute.calendar.data.models.UserMaze;
import villagecompute.calendar.data.models.enums.MazeType;
import villagecompute.calendar.services.MazeGenerationService;
import villagecompute.calendar.services.MazeService;

/**
 * Unit tests for MazeGraphQL. Tests all query and mutation methods with mocked dependencies. Verifies the GraphQL
 * resolver delegates correctly to MazeService and MazeGenerationService without exercising any database, security, or
 * rendering side effects.
 */
@ExtendWith(MockitoExtension.class)
class MazeGraphQLTest {

    @InjectMocks
    MazeGraphQL mazeGraphQL;

    @Mock
    MazeService mazeService;

    @Mock
    MazeGenerationService mazeGenerationService;

    private UserMaze sampleMaze;
    private UUID sampleId;

    @BeforeEach
    void setUp() {
        sampleId = UUID.randomUUID();
        sampleMaze = new UserMaze();
        sampleMaze.id = sampleId;
        sampleMaze.name = "Sample Maze";
        sampleMaze.mazeType = MazeType.ORTHOGONAL;
        sampleMaze.size = 10;
        sampleMaze.difficulty = 5;
        sampleMaze.seed = 42L;
    }

    @Nested
    class GetMazeQueryTests {

        @Test
        void getMaze_ValidId_ReturnsMaze() {
            when(mazeService.findById(sampleId)).thenReturn(sampleMaze);

            UserMaze result = mazeGraphQL.getMaze(sampleId);

            assertNotNull(result);
            assertEquals(sampleId, result.id);
            assertEquals("Sample Maze", result.name);
            verify(mazeService).findById(sampleId);
        }

        @Test
        void getMaze_NotFound_ReturnsNull() {
            UUID missingId = UUID.randomUUID();
            when(mazeService.findById(missingId)).thenReturn(null);

            UserMaze result = mazeGraphQL.getMaze(missingId);

            assertNull(result);
            verify(mazeService).findById(missingId);
        }
    }

    @Nested
    class GetMyMazesQueryTests {

        @Test
        void getMyMazes_AuthenticatedUserWithMazes_ReturnsList() {
            when(mazeService.findMyMazes()).thenReturn(List.of(sampleMaze));

            List<UserMaze> result = mazeGraphQL.getMyMazes();

            assertEquals(1, result.size());
            assertEquals(sampleId, result.get(0).id);
            verify(mazeService).findMyMazes();
        }

        @Test
        void getMyMazes_NoMazes_ReturnsEmptyList() {
            when(mazeService.findMyMazes()).thenReturn(Collections.emptyList());

            List<UserMaze> result = mazeGraphQL.getMyMazes();

            assertTrue(result.isEmpty());
            verify(mazeService).findMyMazes();
        }
    }

    @Nested
    class GetMazesBySessionQueryTests {

        @Test
        void getMazesBySession_ValidSession_ReturnsMazes() {
            String sessionId = "anon-session-123";
            when(mazeService.findBySession(sessionId)).thenReturn(List.of(sampleMaze));

            List<UserMaze> result = mazeGraphQL.getMazesBySession(sessionId);

            assertEquals(1, result.size());
            assertEquals(sampleId, result.get(0).id);
            verify(mazeService).findBySession(sessionId);
        }

        @Test
        void getMazesBySession_BlankSession_ReturnsEmptyList() {
            when(mazeService.findBySession("")).thenReturn(Collections.emptyList());

            List<UserMaze> result = mazeGraphQL.getMazesBySession("");

            assertTrue(result.isEmpty());
            verify(mazeService).findBySession("");
        }

        @Test
        void getMazesBySession_NullSession_DelegatesToService() {
            when(mazeService.findBySession(null)).thenReturn(Collections.emptyList());

            List<UserMaze> result = mazeGraphQL.getMazesBySession(null);

            assertTrue(result.isEmpty());
            verify(mazeService).findBySession(null);
        }
    }

    @Nested
    class GetMazePreviewQueryTests {

        @Test
        void getMazePreview_DefaultParameters_ReturnsSvgString() {
            String svg = "<svg>preview</svg>";
            when(mazeGenerationService.generatePreview(MazeType.ORTHOGONAL, 10, 3, false, false)).thenReturn(svg);

            String result = mazeGraphQL.getMazePreview(MazeType.ORTHOGONAL, 10, 3, false, false);

            assertEquals(svg, result);
            verify(mazeGenerationService).generatePreview(MazeType.ORTHOGONAL, 10, 3, false, false);
        }

        @Test
        void getMazePreview_CustomParameters_PassesAllArguments() {
            String svg = "<svg>delta</svg>";
            when(mazeGenerationService.generatePreview(MazeType.DELTA, 15, 8, true, true)).thenReturn(svg);

            String result = mazeGraphQL.getMazePreview(MazeType.DELTA, 15, 8, true, true);

            assertEquals(svg, result);
            verify(mazeGenerationService).generatePreview(MazeType.DELTA, 15, 8, true, true);
        }

        @Test
        void getMazePreview_ServiceThrows_PropagatesException() {
            when(mazeGenerationService.generatePreview(any(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                    .thenThrow(new IllegalArgumentException("Invalid maze parameters"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> mazeGraphQL.getMazePreview(MazeType.SIGMA, 5, 5, false, false));

            assertTrue(ex.getMessage().contains("Invalid maze parameters"));
        }
    }

    @Nested
    class CreateMazeMutationTests {

        @Test
        void createMaze_ValidInput_ReturnsCreatedMaze() {
            MazeGraphQL.MazeInput input = new MazeGraphQL.MazeInput();
            input.name = "New Maze";
            input.mazeType = MazeType.ORTHOGONAL;
            input.size = 12;
            input.difficulty = 5;
            input.sessionId = "session-xyz";

            when(mazeService.createMaze(input)).thenReturn(sampleMaze);

            UserMaze result = mazeGraphQL.createMaze(input);

            assertNotNull(result);
            assertEquals(sampleId, result.id);
            verify(mazeService).createMaze(input);
        }

        @Test
        void createMaze_ServiceThrows_PropagatesException() {
            MazeGraphQL.MazeInput input = new MazeGraphQL.MazeInput();
            input.name = "Bad";

            when(mazeService.createMaze(input)).thenThrow(new IllegalArgumentException("Invalid input"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> mazeGraphQL.createMaze(input));

            assertTrue(ex.getMessage().contains("Invalid input"));
        }
    }

    @Nested
    class UpdateMazeMutationTests {

        @Test
        void updateMaze_ValidInput_ReturnsUpdatedMaze() {
            MazeGraphQL.MazeInput input = new MazeGraphQL.MazeInput();
            input.name = "Updated";
            input.size = 15;

            when(mazeService.updateMaze(sampleId, input)).thenReturn(sampleMaze);

            UserMaze result = mazeGraphQL.updateMaze(sampleId, input);

            assertNotNull(result);
            assertEquals(sampleId, result.id);
            verify(mazeService).updateMaze(sampleId, input);
        }

        @Test
        void updateMaze_NotFound_PropagatesException() {
            UUID missingId = UUID.randomUUID();
            MazeGraphQL.MazeInput input = new MazeGraphQL.MazeInput();

            when(mazeService.updateMaze(missingId, input))
                    .thenThrow(new IllegalArgumentException("Maze not found: " + missingId));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> mazeGraphQL.updateMaze(missingId, input));

            assertTrue(ex.getMessage().contains("not found"));
        }
    }

    @Nested
    class RegenerateMazeMutationTests {

        @Test
        void regenerateMaze_ValidId_ReturnsRegeneratedMaze() {
            when(mazeService.regenerateMaze(sampleId)).thenReturn(sampleMaze);

            UserMaze result = mazeGraphQL.regenerateMaze(sampleId);

            assertNotNull(result);
            assertEquals(sampleId, result.id);
            verify(mazeService).regenerateMaze(sampleId);
        }

        @Test
        void regenerateMaze_NotFound_PropagatesException() {
            UUID missingId = UUID.randomUUID();
            when(mazeService.regenerateMaze(missingId))
                    .thenThrow(new IllegalArgumentException("Maze not found: " + missingId));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> mazeGraphQL.regenerateMaze(missingId));

            assertTrue(ex.getMessage().contains("not found"));
        }
    }

    @Nested
    class DeleteMazeMutationTests {

        @Test
        void deleteMaze_ExistingId_ReturnsTrue() {
            when(mazeService.deleteMaze(sampleId)).thenReturn(true);

            boolean result = mazeGraphQL.deleteMaze(sampleId);

            assertTrue(result);
            verify(mazeService).deleteMaze(sampleId);
        }

        @Test
        void deleteMaze_NonExistentId_ReturnsFalse() {
            UUID missingId = UUID.randomUUID();
            when(mazeService.deleteMaze(missingId)).thenReturn(false);

            boolean result = mazeGraphQL.deleteMaze(missingId);

            assertFalse(result);
            verify(mazeService).deleteMaze(missingId);
        }

        @Test
        void deleteMaze_ServiceThrows_PropagatesException() {
            when(mazeService.deleteMaze(sampleId)).thenThrow(new IllegalStateException("DB error"));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> mazeGraphQL.deleteMaze(sampleId));

            assertTrue(ex.getMessage().contains("DB error"));
        }
    }

    @Nested
    class MazeInputTests {

        @Test
        void mazeInput_AllFieldsAssignable() {
            MazeGraphQL.MazeInput input = new MazeGraphQL.MazeInput();
            input.name = "Test";
            input.mazeType = MazeType.THETA;
            input.size = 7;
            input.difficulty = 9;
            input.seed = 123L;
            input.sessionId = "sid";
            input.showSolution = true;
            input.innerWallColor = "#111111";
            input.outerWallColor = "#222222";
            input.pathColor = "#333333";

            assertEquals("Test", input.name);
            assertEquals(MazeType.THETA, input.mazeType);
            assertEquals(7, input.size);
            assertEquals(9, input.difficulty);
            assertEquals(123L, input.seed);
            assertEquals("sid", input.sessionId);
            assertTrue(input.showSolution);
            assertEquals("#111111", input.innerWallColor);
            assertEquals("#222222", input.outerWallColor);
            assertEquals("#333333", input.pathColor);
        }
    }
}
