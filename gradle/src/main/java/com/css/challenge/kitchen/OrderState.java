package com.css.challenge.kitchen;

import com.css.challenge.client.Order;

import java.time.Instant;

public final class OrderState {
    private final Order order;
    private StorageTarget currentTarget;
    private final Instant placedAt;
    private Instant lastStorageChangeAt;
    private double consumedFreshnessUnits;
    private long version;

    public OrderState(Order order, StorageTarget currentTarget, Instant now) {
        this.order = order;
        this.currentTarget = currentTarget;
        this.placedAt = now;
        this.lastStorageChangeAt = now;
        this.consumedFreshnessUnits = 0.0d;
        this.version = 1L;
    }

    public Order getOrder() {
        return order;
    }

    public StorageTarget getCurrentTarget() {
        return currentTarget;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public Instant getLastStorageChangeAt() {
        return lastStorageChangeAt;
    }

    public double getConsumedFreshnessUnits() {
        return consumedFreshnessUnits;
    }

    public long getVersion() {
        return version;
    }

    public void applyFreshnessUntil(Instant now) {
        long millis = Math.max(0L, now.toEpochMilli() - lastStorageChangeAt.toEpochMilli());
        double seconds = millis / 1000.0d;
        consumedFreshnessUnits += seconds * degradationRate();
        lastStorageChangeAt = now;
    }

    public boolean isExpiredAt(Instant now) {
        double consumed = consumedFreshnessUnits;
        long millis = Math.max(0L, now.toEpochMilli() - lastStorageChangeAt.toEpochMilli());
        double seconds = millis / 1000.0d;
        consumed += seconds * degradationRate();
        return consumed >= order.getFreshness();
    }

    public double remainingFreshnessAt(Instant now) {
        double consumed = consumedFreshnessUnits;
        long millis = Math.max(0L, now.toEpochMilli() - lastStorageChangeAt.toEpochMilli());
        double seconds = millis / 1000.0d;
        consumed += seconds * degradationRate();
        return order.getFreshness() - consumed;
    }

    public void moveTo(StorageTarget newTarget, Instant now) {
        applyFreshnessUntil(now);
        currentTarget = newTarget;
        version++;
    }

    public boolean isOnShelf() {
        return currentTarget == StorageTarget.SHELF;
    }

    public boolean isIdealStorage() {
        StorageTarget ideal = StorageTarget.idealFor(order);
        return ideal == currentTarget;
    }

    private double degradationRate() {
        // 2x degradation on shelf, 1x on ideal storage
        return isIdealStorage() ? 1.0d : 2.0d;
    }
}