package com.css.challenge.kitchen;

public final class ShelfEntry {
    private final String orderId;
    private final long version;
    private final double remainingFreshness;

    public ShelfEntry(String orderId, long version, double remainingFreshness) {
        this.orderId = orderId;
        this.version = version;
        this.remainingFreshness = remainingFreshness;
    }

    public String getOrderId() {
        return orderId;
    }

    public long getVersion() {
        return version;
    }

    public double getRemainingFreshness() {
        return remainingFreshness;
    }
}