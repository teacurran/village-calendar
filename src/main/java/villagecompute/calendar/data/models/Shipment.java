package villagecompute.calendar.data.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import org.eclipse.microprofile.graphql.Ignore;

/**
 * Entity representing a shipment of physical items from an order. An order can have multiple shipments (e.g., split
 * shipments).
 */
@Entity
@Table(name = "shipments", indexes = {@Index(name = "idx_shipments_order", columnList = "order_id"),
        @Index(name = "idx_shipments_tracking", columnList = "tracking_number"),
        @Index(name = "idx_shipments_status", columnList = "status")})
public class Shipment extends DefaultPanacheEntityWithTimestamps {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_shipments_order"))
    @Ignore
    public CalendarOrder order;

    /** Items included in this shipment */
    @OneToMany(mappedBy = "shipment", fetch = FetchType.LAZY)
    public List<CalendarOrderItem> items = new ArrayList<>();

    /** Shipping carrier (e.g., USPS, UPS, FedEx) */
    @Size(max = 50)
    @Column(length = 50)
    public String carrier;

    /** Tracking number */
    @Size(max = 255)
    @Column(name = "tracking_number", length = 255)
    public String trackingNumber;

    /** Tracking URL (optional - can be generated from carrier + tracking number) */
    @Size(max = 500)
    @Column(name = "tracking_url", length = 500)
    public String trackingUrl;

    /** Shipment status */
    @Size(max = 50)
    @Column(length = 50)
    public String status = STATUS_PENDING;

    /** When the shipment was created/label printed */
    @Column(name = "label_created_at")
    public Instant labelCreatedAt;

    /** When the shipment was actually shipped */
    @Column(name = "shipped_at")
    public Instant shippedAt;

    /** When the shipment was delivered */
    @Column(name = "delivered_at")
    public Instant deliveredAt;

    /** Admin notes about the shipment */
    @Column(columnDefinition = "TEXT")
    public String notes;

    // Status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_LABEL_CREATED = "LABEL_CREATED";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_IN_TRANSIT = "IN_TRANSIT";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_EXCEPTION = "EXCEPTION";

    // Common carrier constants
    public static final String CARRIER_USPS = "USPS";
    public static final String CARRIER_UPS = "UPS";
    public static final String CARRIER_FEDEX = "FEDEX";

    /** Mark shipment as shipped */
    public void markAsShipped() {
        this.status = STATUS_SHIPPED;
        this.shippedAt = Instant.now();

        // Update all items in this shipment
        for (CalendarOrderItem item : items) {
            item.itemStatus = CalendarOrderItem.STATUS_SHIPPED;
            item.persist();
        }

        persist();
    }

    /** Mark shipment as delivered */
    public void markAsDelivered() {
        this.status = STATUS_DELIVERED;
        this.deliveredAt = Instant.now();

        // Update all items in this shipment
        for (CalendarOrderItem item : items) {
            item.itemStatus = CalendarOrderItem.STATUS_DELIVERED;
            item.persist();
        }

        persist();
    }

    /** Add an item to this shipment */
    public void addItem(CalendarOrderItem item) {
        item.shipment = this;
        item.persist();
        if (!items.contains(item)) {
            items.add(item);
        }
    }

    /** Generate tracking URL based on carrier */
    public String generateTrackingUrl() {
        if (trackingNumber == null || trackingNumber.isEmpty()) {
            return null;
        }

        return switch (carrier) {
            case CARRIER_USPS -> "https://tools.usps.com/go/TrackConfirmAction?tLabels=" + trackingNumber;
            case CARRIER_UPS -> "https://www.ups.com/track?tracknum=" + trackingNumber;
            case CARRIER_FEDEX -> "https://www.fedex.com/fedextrack/?trknbr=" + trackingNumber;
            default -> null;
        };
    }
}
