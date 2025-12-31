package villagecompute.calendar.data.models.enums;

/**
 * Calendar generation status tracking. The calendar service generates PDF files asynchronously.
 *
 * <p>
 * Status flow:
 *
 * <pre>
 * DRAFT -> GENERATING -> READY
 *              |
 *              v
 *           FAILED
 * </pre>
 */
public enum CalendarStatus {
    /** Calendar created but PDF not yet generated. */
    DRAFT,

    /** PDF generation in progress. */
    GENERATING,

    /** PDF successfully generated and available for download. */
    READY,

    /** PDF generation failed (error details in logs). */
    FAILED
}
