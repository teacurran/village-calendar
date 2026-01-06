package villagecompute.calendar.api.graphql;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import villagecompute.calendar.api.graphql.inputs.CalendarInput;
import villagecompute.calendar.api.graphql.inputs.CalendarUpdateInput;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.AuthenticationService;
import villagecompute.calendar.services.CalendarService;
import villagecompute.calendar.util.Roles;

import io.quarkus.security.identity.SecurityIdentity;

/**
 * Unit tests for CalendarGraphQL. Tests all query and mutation methods with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class CalendarGraphQLUnitTest {

    @InjectMocks
    CalendarGraphQL calendarGraphQL;

    @Mock
    JsonWebToken jwt;

    @Mock
    SecurityIdentity securityIdentity;

    @Mock
    AuthenticationService authService;

    @Mock
    CalendarService calendarService;

    private CalendarUser testUser;
    private CalendarUser adminUser;
    private UserCalendar testCalendar;

    @BeforeEach
    void setUp() {
        testUser = new CalendarUser();
        testUser.id = UUID.randomUUID();
        testUser.email = "test@example.com";
        testUser.displayName = "Test User";
        testUser.isAdmin = false;

        adminUser = new CalendarUser();
        adminUser.id = UUID.randomUUID();
        adminUser.email = "admin@example.com";
        adminUser.displayName = "Admin User";
        adminUser.isAdmin = true;

        testCalendar = new UserCalendar();
        testCalendar.id = UUID.randomUUID();
        testCalendar.user = testUser;
        testCalendar.name = "Test Calendar";
        testCalendar.year = 2025;
        testCalendar.isPublic = true;
    }

    // ========================================================================
    // me() query tests
    // ========================================================================

    @Nested
    class MeQueryTests {

        @Test
        void me_NullJwt_ReturnsNull() {
            // JWT is null and securityIdentity principal is not a JWT
            when(securityIdentity.getPrincipal()).thenReturn(null);

            CalendarUser result = calendarGraphQL.me();

            assertNull(result);
        }

        @Test
        void me_JwtWithNullSubject_ReturnsNull() {
            when(jwt.getSubject()).thenReturn(null);

            CalendarUser result = calendarGraphQL.me();

            assertNull(result);
        }

        @Test
        void me_ValidJwt_UserNotFound_ReturnsNull() {
            when(jwt.getSubject()).thenReturn(testUser.id.toString());
            when(authService.getCurrentUser(jwt)).thenReturn(Optional.empty());

            CalendarUser result = calendarGraphQL.me();

            assertNull(result);
        }

        @Test
        void me_ValidJwt_UserFound_ReturnsUser() {
            when(jwt.getSubject()).thenReturn(testUser.id.toString());
            when(authService.getCurrentUser(jwt)).thenReturn(Optional.of(testUser));

            CalendarUser result = calendarGraphQL.me();

            assertNotNull(result);
            assertEquals(testUser.id, result.id);
            assertEquals(testUser.email, result.email);
        }

        @Test
        void me_JwtFromSecurityIdentityPrincipal_ReturnsUser() {
            // Simulate JWT being null but available via SecurityIdentity principal
            JsonWebToken principalJwt = mock(JsonWebToken.class);
            when(jwt.getSubject()).thenReturn(null);
            when(securityIdentity.getPrincipal()).thenReturn(principalJwt);
            when(principalJwt.getSubject()).thenReturn(testUser.id.toString());
            when(authService.getCurrentUser(principalJwt)).thenReturn(Optional.of(testUser));

            CalendarUser result = calendarGraphQL.me();

            assertNotNull(result);
            assertEquals(testUser.id, result.id);
        }
    }

    // ========================================================================
    // myCalendars() query tests
    // ========================================================================

    @Nested
    class MyCalendarsQueryTests {

        @Test
        void myCalendars_NoYearFilter_ReturnsAllCalendars() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(calendarService.listCalendars(eq(testUser.id), isNull(), eq(0), anyInt(), eq(testUser)))
                    .thenReturn(List.of(testCalendar));

            List<UserCalendar> result = calendarGraphQL.myCalendars(null);

            assertEquals(1, result.size());
            assertEquals(testCalendar.id, result.get(0).id);
        }

        @Test
        void myCalendars_WithYearFilter_ReturnsFilteredCalendars() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(calendarService.listCalendars(eq(testUser.id), eq(2025), eq(0), anyInt(), eq(testUser)))
                    .thenReturn(List.of(testCalendar));

            List<UserCalendar> result = calendarGraphQL.myCalendars(2025);

            assertEquals(1, result.size());
            verify(calendarService).listCalendars(eq(testUser.id), eq(2025), eq(0), anyInt(), eq(testUser));
        }

        @Test
        void myCalendars_NoCalendars_ReturnsEmptyList() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(calendarService.listCalendars(any(), any(), anyInt(), anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            List<UserCalendar> result = calendarGraphQL.myCalendars(null);

            assertTrue(result.isEmpty());
        }
    }

    // ========================================================================
    // calendars() query tests
    // ========================================================================

    @Nested
    class CalendarsQueryTests {

        @Test
        void calendars_NoUserId_ReturnsCurrentUserCalendars() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(calendarService.listCalendars(eq(testUser.id), isNull(), eq(0), anyInt(), eq(testUser)))
                    .thenReturn(List.of(testCalendar));

            List<UserCalendar> result = calendarGraphQL.calendars(null, null);

            assertEquals(1, result.size());
        }

        @Test
        void calendars_EmptyUserId_ReturnsCurrentUserCalendars() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(calendarService.listCalendars(eq(testUser.id), isNull(), eq(0), anyInt(), eq(testUser)))
                    .thenReturn(List.of(testCalendar));

            List<UserCalendar> result = calendarGraphQL.calendars("", null);

            assertEquals(1, result.size());
        }

        @Test
        void calendars_AdminQueriesOtherUser_ReturnsOtherUserCalendars() {
            UUID otherUserId = UUID.randomUUID();
            UserCalendar otherCalendar = new UserCalendar();
            otherCalendar.id = UUID.randomUUID();
            otherCalendar.name = "Other Calendar";

            when(authService.requireCurrentUser(jwt)).thenReturn(adminUser);
            when(jwt.getGroups()).thenReturn(Set.of(Roles.ADMIN));
            when(calendarService.listCalendars(eq(otherUserId), isNull(), eq(0), anyInt(), eq(adminUser)))
                    .thenReturn(List.of(otherCalendar));

            List<UserCalendar> result = calendarGraphQL.calendars(otherUserId.toString(), null);

            assertEquals(1, result.size());
            assertEquals(otherCalendar.id, result.get(0).id);
        }

        @Test
        void calendars_NonAdminQueriesOtherUser_ThrowsSecurityException() {
            UUID otherUserId = UUID.randomUUID();
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(jwt.getGroups()).thenReturn(Set.of(Roles.USER));

            assertThrows(SecurityException.class, () -> calendarGraphQL.calendars(otherUserId.toString(), null));
        }

        @Test
        void calendars_WithYearFilter_FiltersCalendars() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(calendarService.listCalendars(eq(testUser.id), eq(2025), eq(0), anyInt(), eq(testUser)))
                    .thenReturn(List.of(testCalendar));

            List<UserCalendar> result = calendarGraphQL.calendars(null, 2025);

            assertEquals(1, result.size());
            verify(calendarService).listCalendars(eq(testUser.id), eq(2025), eq(0), anyInt(), eq(testUser));
        }
    }

    // ========================================================================
    // calendar() query tests
    // ========================================================================

    @Nested
    class CalendarQueryTests {

        @Test
        void calendar_ValidId_ReturnsCalendar() {
            when(jwt.getSubject()).thenReturn(testUser.id.toString());
            when(authService.getCurrentUser(jwt)).thenReturn(Optional.of(testUser));
            when(calendarService.getCalendar(testCalendar.id, testUser)).thenReturn(testCalendar);

            UserCalendar result = calendarGraphQL.calendar(testCalendar.id.toString());

            assertNotNull(result);
            assertEquals(testCalendar.id, result.id);
        }

        @Test
        void calendar_NotFound_ReturnsNull() {
            UUID nonExistentId = UUID.randomUUID();
            when(jwt.getSubject()).thenReturn(testUser.id.toString());
            when(authService.getCurrentUser(jwt)).thenReturn(Optional.of(testUser));
            when(calendarService.getCalendar(nonExistentId, testUser))
                    .thenThrow(new IllegalArgumentException("Not found"));

            UserCalendar result = calendarGraphQL.calendar(nonExistentId.toString());

            assertNull(result);
        }

        @Test
        void calendar_AccessDenied_ReturnsNull() {
            when(jwt.getSubject()).thenReturn(testUser.id.toString());
            when(authService.getCurrentUser(jwt)).thenReturn(Optional.of(testUser));
            when(calendarService.getCalendar(testCalendar.id, testUser))
                    .thenThrow(new SecurityException("Access denied"));

            UserCalendar result = calendarGraphQL.calendar(testCalendar.id.toString());

            assertNull(result);
        }

        @Test
        void calendar_InvalidUuid_ThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> calendarGraphQL.calendar("not-a-valid-uuid"));
        }

        @Test
        void calendar_AnonymousAccessToPublicCalendar_ReturnsCalendar() {
            when(jwt.getSubject()).thenReturn(null);
            when(calendarService.getCalendar(testCalendar.id, null)).thenReturn(testCalendar);

            UserCalendar result = calendarGraphQL.calendar(testCalendar.id.toString());

            assertNotNull(result);
            assertEquals(testCalendar.id, result.id);
        }
    }

    // ========================================================================
    // createCalendar() mutation tests
    // ========================================================================

    @Nested
    class CreateCalendarMutationTests {

        @Test
        void createCalendar_ValidInput_ReturnsCreatedCalendar() {
            UUID templateId = UUID.randomUUID();
            CalendarInput input = new CalendarInput();
            input.name = "New Calendar";
            input.year = 2025;
            input.templateId = templateId.toString();
            input.isPublic = true;

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(calendarService.createCalendar(eq("New Calendar"), eq(2025), eq(templateId), isNull(), eq(true),
                    eq(testUser), isNull())).thenReturn(testCalendar);

            UserCalendar result = calendarGraphQL.createCalendar(input);

            assertNotNull(result);
            assertEquals(testCalendar.id, result.id);
        }
    }

    // ========================================================================
    // updateCalendar() mutation tests
    // ========================================================================

    @Nested
    class UpdateCalendarMutationTests {

        @Test
        void updateCalendar_ValidInput_ReturnsUpdatedCalendar() {
            CalendarUpdateInput input = new CalendarUpdateInput();
            input.name = "Updated Name";
            input.isPublic = false;

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(calendarService.updateCalendar(eq(testCalendar.id), eq("Updated Name"), isNull(), eq(false),
                    eq(testUser))).thenReturn(testCalendar);

            UserCalendar result = calendarGraphQL.updateCalendar(testCalendar.id.toString(), input);

            assertNotNull(result);
        }

        @Test
        void updateCalendar_InvalidUuid_ThrowsException() {
            CalendarUpdateInput input = new CalendarUpdateInput();
            input.name = "Updated";

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);

            assertThrows(IllegalArgumentException.class, () -> calendarGraphQL.updateCalendar("invalid-uuid", input));
        }
    }

    // ========================================================================
    // deleteCalendar() mutation tests
    // ========================================================================

    @Nested
    class DeleteCalendarMutationTests {

        @Test
        void deleteCalendar_ValidId_ReturnsTrue() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(calendarService.deleteCalendar(testCalendar.id, testUser)).thenReturn(true);

            Boolean result = calendarGraphQL.deleteCalendar(testCalendar.id.toString());

            assertTrue(result);
            verify(calendarService).deleteCalendar(testCalendar.id, testUser);
        }

        @Test
        void deleteCalendar_InvalidUuid_ThrowsException() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);

            assertThrows(IllegalArgumentException.class,
                    () -> calendarGraphQL.deleteCalendar("invalid-uuid"));
        }
    }

    // ========================================================================
    // convertGuestSession() mutation tests
    // ========================================================================

    @Nested
    class ConvertGuestSessionMutationTests {

        @Test
        void convertGuestSession_ValidSession_ReturnsUser() {
            String sessionId = "test-session-123";
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(calendarService.convertSessionToUser(sessionId, testUser)).thenReturn(2);

            CalendarUser result = calendarGraphQL.convertGuestSession(sessionId);

            assertNotNull(result);
            assertEquals(testUser.id, result.id);
            verify(calendarService).convertSessionToUser(sessionId, testUser);
        }
    }

    // ========================================================================
    // allUsers() query tests
    // ========================================================================

    // Note: allUsers() uses CalendarUser.findAll() which is a static Panache method
    // that requires Quarkus context. See CalendarGraphQLTest for integration tests
    // covering allUsers functionality.

    // ========================================================================
    // updateUserAdmin() mutation tests
    // ========================================================================

    @Nested
    class UpdateUserAdminMutationTests {

        @Test
        void updateUserAdmin_RemoveOwnAdminStatus_ThrowsIllegalArgumentException() {
            // Admin cannot remove their own admin status (lockout prevention)
            when(authService.requireCurrentUser(jwt)).thenReturn(adminUser);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> calendarGraphQL.updateUserAdmin(adminUser.id.toString(), false));

            assertEquals("Cannot remove admin status from yourself", exception.getMessage());
        }

        @Test
        void updateUserAdmin_InvalidUuid_ThrowsException() {
            when(authService.requireCurrentUser(jwt)).thenReturn(adminUser);

            assertThrows(IllegalArgumentException.class,
                    () -> calendarGraphQL.updateUserAdmin("invalid-uuid", true));
        }

        // Note: Tests for updateUserAdmin with valid UUID require CalendarUser.findByIdOptional()
        // which is a static Panache method. See CalendarGraphQLTest for integration tests.
    }
}
