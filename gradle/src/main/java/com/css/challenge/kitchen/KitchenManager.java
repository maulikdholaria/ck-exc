package com.css.challenge.kitchen;

import com.css.challenge.client.Action;
import com.css.challenge.client.Order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class KitchenManager {
    private final Storage storage;
    private final DiscardPolicy discardPolicy;

    private final BlockingQueue<Command> orderPlacePickUpQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Action> ledger = new ArrayList<>();

    private volatile CountDownLatch drainedLatch = new CountDownLatch(1);
    private int inFlight = 0;
    private Thread worker;

    public KitchenManager(Storage storage, DiscardPolicy discardPolicy) {
        this.storage = Objects.requireNonNull(storage);
        this.discardPolicy = Objects.requireNonNull(discardPolicy);
    }

    
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worker = new Thread(this::runLoop, "kitchen-manager");
        worker.start();
    }

    public void place(Order order) {
        orderPlacePickUpQueue.offer(new PlaceCommand(order));
    }

    public void pickup(String orderId) {
        orderPlacePickUpQueue.offer(new PickupCommand(orderId));
    }

    public List<Action> snapshotActions() {
        synchronized (ledger) {
            return new ArrayList<>(ledger);
        }
    }

    public void awaitAllOrdersProcessed() throws InterruptedException {
        CountDownLatch latch = drainedLatch;
        latch.await();
    }

    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        orderPlacePickUpQueue.offer(new ShutdownCommand());
        if (worker != null) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runLoop() {
        while (true) {
            try {
                Command cmd = orderPlacePickUpQueue.take();
                if (cmd instanceof ShutdownCommand) {
                    return;
                }
                if (cmd instanceof PlaceCommand) {
                    handlePlace(((PlaceCommand) cmd).order);
                } else if (cmd instanceof PickupCommand) {
                    handlePickup(((PickupCommand) cmd).orderId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void handlePlace(Order order) {

        Instant now = Instant.now();
        StorageTarget ideal = StorageTarget.idealFor(order);

        if (storage.hasRoom(ideal)) {
            placeInternal(order, ideal, now);
            return;
        }

        if (storage.hasRoom(StorageTarget.SHELF)) {
            placeInternal(order, StorageTarget.SHELF, now);
            return;
        }

        if (tryMoveShelfOrder(now)) {
            placeInternal(order, StorageTarget.SHELF, now);
            return;
        }

        OrderState discardCandidate = discardPolicy.selectDiscardCandidate(storage);
        if (discardCandidate != null) {
            discardInternal(discardCandidate, now);
            placeInternal(order, StorageTarget.SHELF, now);
            return;
        }

        // placeInternal(order, StorageTarget.SHELF, now); 
        // fallback safety -- this should never happen
        // throw new IllegalStateException("No space to place order");
    }

    private boolean tryMoveShelfOrder(Instant now) {
        if (storage.hasRoom(StorageTarget.HEATER)) {
            OrderState movableHot = storage.findMovableShelfOrderFor(StorageTarget.HEATER);
            if (movableHot != null) {
                moveInternal(movableHot, StorageTarget.HEATER, now);
                return true;
            }
        }

        if (storage.hasRoom(StorageTarget.COOLER)) {
            OrderState movableCold = storage.findMovableShelfOrderFor(StorageTarget.COOLER);
            if (movableCold != null) {
                moveInternal(movableCold, StorageTarget.COOLER, now);
                return true;
            }
        }

        return false;
    }

    private void handlePickup(String orderId) {
        OrderState state = storage.get(orderId);
        if (state == null) {
            return;
        }

        Instant now = Instant.now();
        if (state.isExpiredAt(now)) {
            discardInternal(state, now);
        } else {
            pickupInternal(state, now);
        }
    }

    private void placeInternal(Order order, StorageTarget target, Instant now) {
        storage.putNew(order, target, now);
        markPlaced();
        record(new Action(now, order.getId(), Action.PLACE, target.actionTarget()));
        System.out.printf("%s place   id=%s target=%s%n", now, order.getId(), target.actionTarget());
    }

    private void moveInternal(OrderState state, StorageTarget target, Instant now) {
        storage.move(state.getOrder().getId(), target, now);
        record(new Action(now, state.getOrder().getId(), Action.MOVE, target.actionTarget()));
        System.out.printf("%s move    id=%s target=%s%n", now, state.getOrder().getId(), target.actionTarget());
    }

    private void pickupInternal(OrderState state, Instant now) {
        storage.remove(state.getOrder().getId(), now);
        record(new Action(now, state.getOrder().getId(), Action.PICKUP, state.getCurrentTarget().actionTarget()));
        System.out.printf("%s pickup  id=%s target=%s%n", now, state.getOrder().getId(), state.getCurrentTarget().actionTarget());
        markPickupOrDiscard();
    }

    private void discardInternal(OrderState state, Instant now) {
        storage.remove(state.getOrder().getId(), now);
        record(new Action(now, state.getOrder().getId(), Action.DISCARD, state.getCurrentTarget().actionTarget()));
        System.out.printf("%s discard id=%s target=%s%n", now, state.getOrder().getId(), state.getCurrentTarget().actionTarget());
        markPickupOrDiscard();
    }

    private void markPlaced() {
        inFlight++;
        drainedLatch = new CountDownLatch(1);
    }

    private void markPickupOrDiscard() {
        inFlight--;
        if (inFlight <= 0) {
            drainedLatch.countDown();
        }
    }

    private void record(Action action) {
        synchronized (ledger) {
            ledger.add(action);
        }
    }

    private interface Command { }

    private static final class PlaceCommand implements Command {
        private final Order order;

        private PlaceCommand(Order order) {
            this.order = order;
        }
    }

    private static final class PickupCommand implements Command {
        private final String orderId;

        private PickupCommand(String orderId) {
            this.orderId = orderId;
        }
    }

    private static final class ShutdownCommand implements Command { }
}