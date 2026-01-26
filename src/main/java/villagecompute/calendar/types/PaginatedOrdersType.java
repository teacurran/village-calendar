package villagecompute.calendar.types;

import java.util.List;

/**
 * Paginated response for admin order listing.
 */
public class PaginatedOrdersType {

    public List<OrderSummaryType> orders;
    public int page;
    public int pageSize;
    public long totalCount;
    public int totalPages;

    public PaginatedOrdersType() {
    }

    public PaginatedOrdersType(List<OrderSummaryType> orders, int page, int pageSize, long totalCount) {
        this.orders = orders;
        this.page = page;
        this.pageSize = pageSize;
        this.totalCount = totalCount;
        this.totalPages = (int) Math.ceil((double) totalCount / pageSize);
    }
}
