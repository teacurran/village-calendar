package villagecompute.calendar.data.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import villagecompute.calendar.data.models.CalendarOrder;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

/**
 * Repository for CalendarOrder entities. Provides custom query methods for order management and e-commerce operations.
 */
@ApplicationScoped
public class CalendarOrderRepository implements PanacheRepository<CalendarOrder> {

    /**
     * Find order by ID.
     *
     * @param id
     *            Order ID
     * @return Optional containing the order if found
     */
    public Optional<CalendarOrder> findById(UUID id) {
        return find("id", id).firstResultOptional();
    }

    /**
     * Find orders by status, ordered by creation date descending. This is the required custom query method from the
     * task specification.
     *
     * @param status
     *            Order status
     * @return List of orders with the specified status
     */
    public List<CalendarOrder> findByStatusOrderByCreatedDesc(String status) {
        return find("status = ?1 ORDER BY created DESC", status).list();
    }

    /**
     * Find all orders for a specific user, ordered by creation date descending.
     *
     * @param userId
     *            User ID
     * @return List of user's orders
     */
    public List<CalendarOrder> findByUser(UUID userId) {
        return find("user.id = ?1 ORDER BY created DESC", userId).list();
    }

    /**
     * Find all orders for a specific calendar.
     *
     * @param calendarId
     *            Calendar ID
     * @return List of orders for the calendar
     */
    public List<CalendarOrder> findByCalendar(UUID calendarId) {
        return find("calendar.id = ?1 ORDER BY created DESC", calendarId).list();
    }

    /**
     * Find orders by Stripe Payment Intent ID.
     *
     * @param paymentIntentId
     *            Stripe Payment Intent ID
     * @return Optional containing the order if found
     */
    public Optional<CalendarOrder> findByStripePaymentIntent(String paymentIntentId) {
        return find("stripePaymentIntentId", paymentIntentId).firstResultOptional();
    }

    /**
     * Find recent orders within a time range.
     *
     * @param since
     *            Start time
     * @return List of recent orders
     */
    public List<CalendarOrder> findRecentOrders(Instant since) {
        return find("created >= ?1 ORDER BY created DESC", since).list();
    }

    /**
     * Find pending orders (not yet paid).
     *
     * @return List of pending orders
     */
    public List<CalendarOrder> findPendingOrders() {
        return findByStatusOrderByCreatedDesc(CalendarOrder.STATUS_PENDING);
    }

    /**
     * Find paid orders that haven't been shipped yet.
     *
     * @return List of paid but unshipped orders
     */
    public List<CalendarOrder> findPaidNotShipped() {
        return find("status = ?1 ORDER BY paidAt DESC", CalendarOrder.STATUS_PAID).list();
    }

    /**
     * Find orders by user and status.
     *
     * @param userId
     *            User ID
     * @param status
     *            Order status
     * @return List of matching orders
     */
    public List<CalendarOrder> findByUserAndStatus(UUID userId, String status) {
        return find("user.id = ?1 AND status = ?2 ORDER BY created DESC", userId, status).list();
    }
}
