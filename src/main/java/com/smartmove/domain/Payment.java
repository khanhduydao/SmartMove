package com.smartmove.domain;

public class Payment {
    private final String id;
    private final String rentalId;
    private final double baseAmount;
    private final double surcharges;
    private final double total;
    private final String description;

    public Payment(String id, String rentalId, double baseAmount, double surcharges, String description) {
        this.id = id;
        this.rentalId = rentalId;
        this.baseAmount = baseAmount;
        this.surcharges = surcharges;
        this.total = baseAmount + surcharges;
        this.description = description;
    }

    public String getId() { return id; }
    public String getRentalId() { return rentalId; }
    public double getBaseAmount() { return baseAmount; }
    public double getSurcharges() { return surcharges; }
    public double getTotal() { return total; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return String.format("Payment[id=%s, rental=%s, base=%.2f, surcharges=%.2f, total=%.2f, desc=%s]",
                id, rentalId, baseAmount, surcharges, total, description);
    }
}
