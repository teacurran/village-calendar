package villagecompute.calendar.api.dto;

import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;

import villagecompute.calendar.data.models.CalendarUser;

/**
 * Data transfer object containing basic user information. Returned to the frontend after successful authentication.
 */
@Schema(
        description = "User information")
public class UserInfo {

    @JsonProperty("id")
    @Schema(
            description = "Unique user identifier (UUID)",
            example = "550e8400-e29b-41d4-a716-446655440000")
    public UUID id;

    @JsonProperty("email")
    @Schema(
            description = "User email address",
            example = "user@example.com")
    public String email;

    @JsonProperty("displayName")
    @Schema(
            description = "User display name",
            example = "John Doe")
    public String displayName;

    @JsonProperty("profileImageUrl")
    @Schema(
            description = "URL to user's profile image",
            example = "https://example.com/avatar.jpg")
    public String profileImageUrl;

    public UserInfo() {
    }

    public UserInfo(UUID id, String email, String displayName, String profileImageUrl) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * Create a UserInfo DTO from a CalendarUser entity.
     *
     * @param user
     *            The calendar user entity
     * @return UserInfo DTO
     */
    public static UserInfo fromEntity(CalendarUser user) {
        return new UserInfo(user.id, user.email, user.displayName, user.profileImageUrl);
    }
}
