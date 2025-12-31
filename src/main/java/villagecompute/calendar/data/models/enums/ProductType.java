package villagecompute.calendar.data.models.enums;

/** Product types available for purchase. Maps to product codes in ProductService. */
public enum ProductType {
    /** Printed 35" x 23" poster calendar, shipped to customer. */
    PRINT("print"),

    /** Digital PDF download for self-printing. */
    PDF("pdf");

    private final String productCode;

    ProductType(String productCode) {
        this.productCode = productCode;
    }

    /** Get the product code used by ProductService for pricing. */
    public String getProductCode() {
        return productCode;
    }
}
