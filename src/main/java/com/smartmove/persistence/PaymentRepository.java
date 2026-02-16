package com.smartmove.persistence;

import com.smartmove.domain.Payment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PaymentRepository {
    private final Map<String, Payment> paymentMap = new ConcurrentHashMap<>();
    private final PaymentCsvStorage storage = new PaymentCsvStorage();

    public PaymentRepository() {
        storage.loadAll().forEach(p -> paymentMap.put(p.getId(), p));
    }

    public void save(Payment payment) {
        paymentMap.put(payment.getId(), payment);
        storage.saveAll(new ArrayList<>(paymentMap.values()));
    }

    public Optional<Payment> findById(String id) {
        return Optional.ofNullable(paymentMap.get(id));
    }

    public Map<String, Payment> getAll() { return Collections.unmodifiableMap(paymentMap); }
}
