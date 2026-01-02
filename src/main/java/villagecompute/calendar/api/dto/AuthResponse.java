package villagecompute.calendar.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response object returned after successful OAuth authentication. Contains the JWT token and basic user information.
 */
@Schema(
        description = "Authentication response containing JWT token and user information")
public class AuthResponse {

    @JsonProperty("token")
    @Schema(
            description = "JWT token for API authentication",
            examples = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
    public String token;

    @JsonProperty("user")
    @Schema(
            description = "Basic user information")
    public UserInfo user;

    public AuthResponse() {
    }

    public AuthResponse(String token, UserInfo user) {
        this.token = token;
        this.user = user;
    }

    public static AuthResponse of(String token, UserInfo user) {
        return new AuthResponse(token, user);
    }
}
