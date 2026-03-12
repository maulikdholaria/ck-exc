package com.css.challenge.kitchen;

import com.css.challenge.client.Order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class Storage {
    private final int heaterCapacity;
    private final int coolerCapacity;
    private final int shelfCapacity;

    private final Map<String, OrderState> byId = new HashMap<>();
    private final Map<String, OrderState> heater = new HashMap<>();
    private final Map<String, OrderState> cooler = new HashMap<>();
    private final Map<String, OrderState> shelf = new HashMap<>();

    private final PriorityQueue<ShelfEntry> shelfHeap =
            new PriorityQueue<>(Comparator.comparingDouble(ShelfEntry::getRemainingFreshness));

    public Storage(int coolerCapacity, int heaterCapacity, int shelfCapacity) {
        this.coolerCapacity = coolerCapacity;
        this.heaterCapacity = heaterCapacity;
        this.shelfCapacity = shelfCapacity;
    }

    public OrderState get(String orderId) {
        return byId.get(orderId);
    }

    public boolean hasRoom(StorageTarget target) {
        switch (target) {
            case HEATER:
                return heater.size() < heaterCapacity;
            case COOLER:
                return cooler.size() < coolerCapacity;
            default:
                return shelf.size() < shelfCapacity;
        }
    }

    public void putNew(Order order, StorageTarget target, Instant now) {
        OrderState state = new OrderState(order, target, now);
        byId.put(order.getId(), state);
        targetMap(target).put(order.getId(), state);

        if (target == StorageTarget.SHELF) {
            addShelfHeapEntry(state, now);
        }
    }

    public void move(String orderId, StorageTarget newTarget, Instant now) {
        OrderState state = byId.get(orderId);
        if (state == null) {
            return;
        }

        StorageTarget oldTarget = state.getCurrentTarget();
        if (oldTarget == newTarget) {
            return;
        }

        targetMap(oldTarget).remove(orderId);
        state.moveTo(newTarget, now);
        targetMap(newTarget).put(orderId, state);

        if (newTarget == StorageTarget.SHELF) {
            addShelfHeapEntry(state, now);
        }
    }

    public OrderState remove(String orderId, Instant now) {
        OrderState state = byId.remove(orderId);
        if (state == null) {
            return null;
        }

        state.applyFreshnessUntil(now);
        targetMap(state.getCurrentTarget()).remove(orderId);
        return state;
    }

    public OrderState peekBestShelfDiscardCandidate() {
        while (!shelfHeap.isEmpty()) {
            ShelfEntry top = shelfHeap.peek();
            OrderState state = byId.get(top.getOrderId());

            // For invalid or stale entries, just remove and continue   
            if (state == null || !state.isOnShelf() || state.getVersion() != top.getVersion()) {
                shelfHeap.poll();
                continue;
            }
            return state;
        }
        return null;
    }

    public OrderState findMovableShelfOrderFor(StorageTarget target) {
        List<OrderState> candidates = new ArrayList<>(shelf.values());
        OrderState best = null;
        double bestRemaining = Double.NEGATIVE_INFINITY;
        Instant now = Instant.now();

        for (OrderState state : candidates) {
            if (target == StorageTarget.HEATER && !"hot".equals(state.getOrder().getTemp())) {
                continue;
            }
            if (target == StorageTarget.COOLER && !"cold".equals(state.getOrder().getTemp())) {
                continue;
            }

            double remaining = state.remainingFreshnessAt(now);
            if (remaining > bestRemaining) {
                bestRemaining = remaining;
                best = state;
            }
        }
        return best;
    }

    private void addShelfHeapEntry(OrderState state, Instant now) {
        shelfHeap.offer(new ShelfEntry(
                state.getOrder().getId(),
                state.getVersion(),
                state.remainingFreshnessAt(now)
        ));
    }

    private Map<String, OrderState> targetMap(StorageTarget target) {
        switch (target) {
            case HEATER:
                return heater;
            case COOLER:
                return cooler;
            default:
                return shelf;
        }
    }
}