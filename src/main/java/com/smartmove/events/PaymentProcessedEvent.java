package com.smartmove.events;

import java.time.Instant; /**
 * Fired when payment is processed.
 */
public class PaymentProcessedEvent implements Event {
    private final String eventId;
    private final Instant timestamp;
    private final String paymentId;
    private final String rentalId;
    private final double amount;
    
    public PaymentProcessedEvent(String paymentId, String rentalId, double amount) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.paymentId = paymentId;
        this.rentalId = rentalId;
        this.amount = amount;
    }
    
    public String getEventId() { return eventId; }
    public Instant getTimestamp() { return timestamp; }
    public String getPaymentId() { return paymentId; }
    public String getRentalId() { return rentalId; }
    public double getAmount() { return amount; }
}
