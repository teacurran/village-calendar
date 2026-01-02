package villagecompute.calendar.data.models;

import java.util.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.eclipse.microprofile.graphql.Ignore;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import villagecompute.calendar.data.models.enums.MazeType;

import io.quarkus.hibernate.orm.panache.PanacheQuery;

@Entity
@Table(
        name = "user_mazes",
        indexes = {@Index(
                name = "idx_user_mazes_user",
                columnList = "user_id, created DESC"),
                @Index(
                        name = "idx_user_mazes_session",
                        columnList = "session_id, updated DESC"),
                @Index(
                        name = "idx_user_mazes_public",
                        columnList = "is_public, updated DESC")})
public class UserMaze extends DefaultPanacheEntityWithTimestamps {

    @ManyToOne(
            fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            foreignKey = @ForeignKey(
                    name = "fk_user_mazes_user"))
    @Ignore
    public CalendarUser user;

    @Size(
            max = 255)
    @Column(
            name = "session_id",
            length = 255)
    public String sessionId;

    @Column(
            name = "is_public",
            nullable = false)
    public boolean isPublic = true;

    @NotNull @Size(
            max = 255)
    @Column(
            nullable = false,
            length = 255)
    public String name;

    @NotNull @Enumerated(EnumType.STRING)
    @Column(
            name = "maze_type",
            nullable = false,
            length = 20)
    public MazeType mazeType = MazeType.ORTHOGONAL;

    /**
     * Size level from 1 to 20. Controls the number of cells in the maze. 1 = simple maze (~15 cells wide), 20 = complex
     * maze (~100 cells wide).
     */
    @NotNull @Column(
            nullable = false)
    public Integer size = 10;

    /**
     * Difficulty level from 1 to 20. Controls how long and winding the solution path is. 1 = short direct path, 20 =
     * maximum winding path.
     */
    @NotNull @Column(
            nullable = false)
    public Integer difficulty = 10;

    /** Random seed for reproducible maze generation. If null, a random seed will be used. */
    @Column(
            name = "seed")
    public Long seed;

    /**
     * Additional configuration options stored as JSON. Examples: showSolution, cellSize, wallThickness, colors
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            columnDefinition = "jsonb",
            nullable = true)
    @io.smallrye.graphql.api.AdaptWith(villagecompute.calendar.api.graphql.scalars.JsonNodeAdapter.class)
    public JsonNode configuration;

    @Column(
            name = "generated_svg",
            columnDefinition = "TEXT")
    public String generatedSvg;

    @Size(
            max = 500)
    @Column(
            name = "generated_pdf_url",
            length = 500)
    public String generatedPdfUrl;

    /** The solution path stored as JSON array of cell coordinates. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "solution_path",
            columnDefinition = "jsonb",
            nullable = true)
    public JsonNode solutionPath;

    // Helper methods (ActiveRecord pattern)

    public static PanacheQuery<UserMaze> findBySession(String sessionId) {
        return find("sessionId = ?1 ORDER BY updated DESC", sessionId);
    }

    public static PanacheQuery<UserMaze> findByUser(UUID userId) {
        return find("user.id = ?1 ORDER BY updated DESC", userId);
    }

    public static UserMaze findPublicById(UUID id) {
        return find("id = ?1 AND isPublic = true", id).firstResult();
    }

    public static PanacheQuery<UserMaze> findPublicMazes() {
        return find("isPublic = true ORDER BY updated DESC");
    }

    public UserMaze copyForSession(String newSessionId) {
        UserMaze copy = new UserMaze();
        copy.sessionId = newSessionId;
        copy.name = this.name;
        copy.mazeType = this.mazeType;
        copy.size = this.size;
        copy.difficulty = this.difficulty;
        copy.seed = this.seed;
        copy.configuration = this.configuration;
        copy.generatedSvg = this.generatedSvg;
        copy.solutionPath = this.solutionPath;
        copy.isPublic = true;
        return copy;
    }
}
