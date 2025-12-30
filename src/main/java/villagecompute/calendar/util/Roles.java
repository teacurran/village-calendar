package villagecompute.calendar.util;

/**
 * Role constants for the application.
 *
 * <p>These constants are used with {@code @RolesAllowed} annotations for authorization. They must
 * be compile-time constants (public static final String) to be used in annotations.
 */
public final class Roles {

    private Roles() {}

    /** Standard authenticated user role */
    public static final String USER = "USER";

    /** Administrator role with elevated privileges */
    public static final String ADMIN = "ADMIN";

    /** Manager role for business operations */
    public static final String MANAGER = "MANAGER";
}
