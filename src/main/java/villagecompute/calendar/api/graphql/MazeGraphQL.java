package villagecompute.calendar.api.graphql;

import java.util.List;
import java.util.UUID;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.graphql.*;

import villagecompute.calendar.data.models.UserMaze;
import villagecompute.calendar.data.models.enums.MazeType;
import villagecompute.calendar.services.MazeGenerationService;
import villagecompute.calendar.services.MazeService;
import villagecompute.calendar.util.Roles;

@GraphQLApi
public class MazeGraphQL {

    @Inject
    MazeService mazeService;

    @Inject
    MazeGenerationService mazeGenerationService;

    // Queries

    @Query("maze")
    @PermitAll
    @Description("Get a maze by ID")
    public UserMaze getMaze(@Name("id") UUID id) {
        return mazeService.findById(id);
    }

    @Query("myMazes")
    @RolesAllowed(Roles.USER)
    @Description("Get all mazes for the current user")
    public List<UserMaze> getMyMazes() {
        return mazeService.findMyMazes();
    }

    @Query("mazes")
    @PermitAll
    @Description("Get mazes by session ID (for anonymous users)")
    public List<UserMaze> getMazesBySession(@Name("sessionId") String sessionId) {
        return mazeService.findBySession(sessionId);
    }

    @Query("mazePreview")
    @PermitAll
    @Description("Generate a preview SVG for given maze parameters")
    public String getMazePreview(@Name("type") @DefaultValue("ORTHOGONAL") MazeType type,
            @Name("size") @DefaultValue("10") int size, @Name("difficulty") @DefaultValue("3") int difficulty,
            @Name("showSolution") @DefaultValue("false") boolean showSolution,
            @Name("showDeadEnds") @DefaultValue("false") boolean showDeadEnds) {
        return mazeGenerationService.generatePreview(type, size, difficulty, showSolution, showDeadEnds);
    }

    // Mutations

    @Mutation("createMaze")
    @PermitAll
    @Transactional
    @Description("Create a new maze")
    public UserMaze createMaze(@Name("input") MazeInput input) {
        return mazeService.createMaze(input);
    }

    @Mutation("updateMaze")
    @PermitAll
    @Transactional
    @Description("Update an existing maze")
    public UserMaze updateMaze(@Name("id") UUID id, @Name("input") MazeInput input) {
        return mazeService.updateMaze(id, input);
    }

    @Mutation("regenerateMaze")
    @PermitAll
    @Transactional
    @Description("Regenerate a maze with a new random seed")
    public UserMaze regenerateMaze(@Name("id") UUID id) {
        return mazeService.regenerateMaze(id);
    }

    @Mutation("deleteMaze")
    @PermitAll
    @Transactional
    @Description("Delete a maze")
    public boolean deleteMaze(@Name("id") UUID id) {
        return mazeService.deleteMaze(id);
    }

    // Input type for creating/updating mazes
    @Input("MazeInput")
    public static class MazeInput {
        public String name;
        public MazeType mazeType;
        public Integer size; // 1-20: controls cell count
        public Integer difficulty; // 1-20: controls path length
        public Long seed;
        public String sessionId;
        public Boolean showSolution;
        public String innerWallColor;
        public String outerWallColor;
        public String pathColor;
    }
}
