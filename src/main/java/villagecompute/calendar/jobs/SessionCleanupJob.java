package villagecompute.calendar.jobs;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import villagecompute.calendar.services.SessionService;

/**
 * Scheduled job to clean up expired guest session calendars.
 * Runs daily at 2 AM UTC to delete calendars that have not been
 * converted to user accounts and are older than 30 days.
 */
@ApplicationScoped
public class SessionCleanupJob {

    private static final Logger LOG = Logger.getLogger(SessionCleanupJob.class);

    @Inject
    SessionService sessionService;

    /**
     * Clean up expired guest session calendars.
     * Runs daily at 2:00 AM UTC.
     *
     * Deletes all calendars that:
     * - Have a sessionId (guest calendars not converted to user)
     * - Last updated more than 30 days ago
     */
    @Scheduled(cron = "0 0 2 * * ?", identity = "session-cleanup")
    @Transactional
    public void cleanupExpiredSessions() {
        LOG.info("Starting scheduled cleanup of expired guest session calendars");

        try {
            int deletedCount = sessionService.deleteExpiredSessions();

            LOG.infof("Session cleanup job completed successfully. Deleted %d expired calendars.",
                     deletedCount);

        } catch (Exception e) {
            LOG.errorf(e, "Error during session cleanup job");
            // Don't rethrow - we want the scheduler to continue running
        }
    }
}
