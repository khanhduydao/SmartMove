package com.smartmove.persistence;

import com.smartmove.domain.Payment;
import com.smartmove.domain.Rental;
import com.smartmove.domain.User;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// ─── USER REPOSITORY ────────────────────────────────────────────────────────

class UserCsvStorage extends CsvFileStorage<User> {
    UserCsvStorage() { super("data/users.csv", "id,name"); }

    @Override
    protected User fromCsv(String line) {
        String[] p = splitCsv(line);
        if (p.length < 2) return null;
        return new User(p[0].trim(), p[1].trim());
    }

    @Override
    protected String toCsv(User u) {
        return escapeCsv(u.getId()) + "," + escapeCsv(u.getName());
    }
}

public class UserRepository {
    private final Map<String, User> userMap = new ConcurrentHashMap<>();
    private final UserCsvStorage storage = new UserCsvStorage();

    public UserRepository() {
        storage.loadAll().forEach(u -> userMap.put(u.getId(), u));
    }

    public void save(User user) {
        userMap.put(user.getId(), user);
        storage.saveAll(new ArrayList<>(userMap.values()));
    }

    public Optional<User> findById(String id) {
        return Optional.ofNullable(userMap.get(id));
    }

    public Map<String, User> getAll() { return Collections.unmodifiableMap(userMap); }
}


// ─── RENTAL REPOSITORY ──────────────────────────────────────────────────────

class RentalCsvStorage extends CsvFileStorage<Rental> {
    RentalCsvStorage() { super("data/rentals.csv", "id,userId,vehicleId,startTime,endTime,active"); }

    @Override
    protected Rental fromCsv(String line) {
        String[] p = splitCsv(line);
        if (p.length < 6) return null;
        Rental r = new Rental(p[0].trim(), p[1].trim(), p[2].trim(), p[3].trim());
        String endTime = p[4].trim();
        boolean active = Boolean.parseBoolean(p[5].trim());
        if (!active && !endTime.isEmpty()) {
            r.end(endTime);
        }
        return r;
    }

    @Override
    protected String toCsv(Rental r) {
        return String.join(",",
                escapeCsv(r.getId()),
                escapeCsv(r.getUserId()),
                escapeCsv(r.getVehicleId()),
                escapeCsv(r.getStartTime()),
                escapeCsv(r.getEndTime() != null ? r.getEndTime() : ""),
                String.valueOf(r.isActive())
        );
    }
}


// ─── PAYMENT REPOSITORY ─────────────────────────────────────────────────────

class PaymentCsvStorage extends CsvFileStorage<Payment> {
    PaymentCsvStorage() {
        super("data/payments.csv", "id,rentalId,baseAmount,surcharges,total,description");
    }

    @Override
    protected Payment fromCsv(String line) {
        String[] p = splitCsv(line);
        if (p.length < 6) return null;
        return new Payment(
                p[0].trim(), p[1].trim(),
                Double.parseDouble(p[2].trim()),
                Double.parseDouble(p[3].trim()),
                p[5].trim()
        );
    }

    @Override
    protected String toCsv(Payment pay) {
        return String.join(",",
                escapeCsv(pay.getId()),
                escapeCsv(pay.getRentalId()),
                String.format("%.2f", pay.getBaseAmount()),
                String.format("%.2f", pay.getSurcharges()),
                String.format("%.2f", pay.getTotal()),
                escapeCsv(pay.getDescription())
        );
    }
}

