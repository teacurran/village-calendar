package villagecompute.calendar.services;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.jwt.JsonWebToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import villagecompute.calendar.api.graphql.MazeGraphQL;
import villagecompute.calendar.data.models.UserMaze;
import villagecompute.calendar.data.models.enums.MazeType;
import villagecompute.calendar.data.repositories.CalendarUserRepository;

import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
public class MazeService {

    @Inject MazeGenerationService generationService;

    @Inject CalendarUserRepository userRepository;

    @Inject SecurityIdentity securityIdentity;

    @Inject JsonWebToken jwt;

    @Inject ObjectMapper objectMapper;

    public UserMaze findById(UUID id) {
        return UserMaze.findById(id);
    }

    public List<UserMaze> findMyMazes() {
        String email = jwt.getClaim("email");
        if (email == null) {
            return List.of();
        }
        return userRepository
                .findByEmail(email)
                .map(user -> UserMaze.findByUser(user.id).list())
                .orElse(List.of());
    }

    public List<UserMaze> findBySession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        return UserMaze.findBySession(sessionId).list();
    }

    @Transactional
    public UserMaze createMaze(MazeGraphQL.MazeInput input) {
        UserMaze maze = new UserMaze();

        // Set basic properties
        maze.name = input.name != null ? input.name : "My Maze";
        maze.mazeType = input.mazeType != null ? input.mazeType : MazeType.ORTHOGONAL;
        maze.size = input.size != null ? Math.max(1, Math.min(20, input.size)) : 10;
        maze.difficulty =
                input.difficulty != null ? Math.max(1, Math.min(20, input.difficulty)) : 10;
        maze.seed = input.seed != null ? input.seed : System.currentTimeMillis();

        // Set session or user
        if (input.sessionId != null) {
            maze.sessionId = input.sessionId;
        }

        // Try to associate with logged-in user
        try {
            String email = jwt.getClaim("email");
            if (email != null) {
                userRepository.findByEmail(email).ifPresent(user -> maze.user = user);
            }
        } catch (Exception ignored) {
            // Not logged in, use session
        }

        // Build configuration
        ObjectNode config = objectMapper.createObjectNode();
        if (input.showSolution != null) config.put("showSolution", input.showSolution);
        if (input.innerWallColor != null) config.put("innerWallColor", input.innerWallColor);
        if (input.outerWallColor != null) config.put("outerWallColor", input.outerWallColor);
        if (input.pathColor != null) config.put("pathColor", input.pathColor);
        maze.configuration = config;

        // Generate the maze
        generationService.generateAndUpdateMaze(maze);

        // Persist
        maze.persist();
        return maze;
    }

    @Transactional
    public UserMaze updateMaze(UUID id, MazeGraphQL.MazeInput input) {
        UserMaze maze = UserMaze.findById(id);
        if (maze == null) {
            throw new IllegalArgumentException("Maze not found: " + id);
        }

        boolean needsRegeneration = false;

        if (input.name != null) {
            maze.name = input.name;
        }
        if (input.mazeType != null && input.mazeType != maze.mazeType) {
            maze.mazeType = input.mazeType;
            needsRegeneration = true;
        }
        if (input.size != null && !input.size.equals(maze.size)) {
            maze.size = Math.max(1, Math.min(20, input.size));
            needsRegeneration = true;
        }
        if (input.difficulty != null && !input.difficulty.equals(maze.difficulty)) {
            maze.difficulty = Math.max(1, Math.min(20, input.difficulty));
            needsRegeneration = true;
        }
        if (input.seed != null) {
            maze.seed = input.seed;
            needsRegeneration = true;
        }

        // Update configuration
        ObjectNode config =
                maze.configuration != null
                        ? (ObjectNode) maze.configuration
                        : objectMapper.createObjectNode();

        if (input.showSolution != null) config.put("showSolution", input.showSolution);
        if (input.innerWallColor != null) config.put("innerWallColor", input.innerWallColor);
        if (input.outerWallColor != null) config.put("outerWallColor", input.outerWallColor);
        if (input.pathColor != null) config.put("pathColor", input.pathColor);
        maze.configuration = config;

        if (needsRegeneration) {
            generationService.generateAndUpdateMaze(maze);
        }

        return maze;
    }

    @Transactional
    public UserMaze regenerateMaze(UUID id) {
        UserMaze maze = UserMaze.findById(id);
        if (maze == null) {
            throw new IllegalArgumentException("Maze not found: " + id);
        }
        generationService.regenerateMaze(maze);
        return maze;
    }

    @Transactional
    public boolean deleteMaze(UUID id) {
        UserMaze maze = UserMaze.findById(id);
        if (maze == null) {
            return false;
        }
        maze.delete();
        return true;
    }
}
