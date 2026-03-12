package com.css.challenge;

import com.css.challenge.client.Action;
import com.css.challenge.client.Client;
import com.css.challenge.client.Order;
import com.css.challenge.client.Problem;
import com.css.challenge.kitchen.KitchenManager;
import com.css.challenge.kitchen.Storage;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.css.challenge.kitchen.DiscardPolicy;
import com.css.challenge.kitchen.EarliestExpiryDiscardPolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "challenge", showDefaultValues = true)
public class Main implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  static {
    System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT: %5$s %n");
  }

  @Option(names = "--endpoint", description = "Problem server endpoint")
  String endpoint = "https://api.cloudkitchens.com";

  @Option(names = "--auth", description = "Authentication token (required)")
  String auth = "";

  @Option(names = "--name", description = "Problem name. Leave blank (optional)")
  String name = "";

  @Option(names = "--seed", description = "Problem seed (random if zero)")
  long seed = 0;

  @Option(names = "--rate", description = "Inverse order rate")
  Duration rate = Duration.ofMillis(500);

  @Option(names = "--min", description = "Minimum pickup time")
  Duration min = Duration.ofSeconds(4);

  @Option(names = "--max", description = "Maximum pickup time")
  Duration max = Duration.ofSeconds(8);

  public static final int COOLER_CAPACITY = 6;
  public static final int HEATER_CAPACITY = 6;
  public static final int SHELF_CAPACITY  = 12;


  @Override
  public void run() {
    ScheduledExecutorService scheduler = null;
    KitchenManager kitchenManager = null;

    try {
      Client client = new Client(endpoint, auth);
      Problem problem = client.newProblem(name, seed);

      Storage storage = new Storage(COOLER_CAPACITY, HEATER_CAPACITY, SHELF_CAPACITY);
      DiscardPolicy discardPolicy = new EarliestExpiryDiscardPolicy();

      // ------ Execution harness logic goes here using rate, min and max ----
      kitchenManager = new KitchenManager(storage, discardPolicy);
      kitchenManager.start();

      final KitchenManager kitchen = kitchenManager;

      scheduler = Executors.newScheduledThreadPool(4);
      Random random = new Random();

      List<Action> actions = new ArrayList<>();
      for (Order order : problem.getOrders()) {
        LOGGER.info("Received: {}", order);

        // actions.add(new Action(Instant.now(), order.getId(), Action.PLACE, Action.COOLER));
        kitchenManager.place(order);

        long delayMillis = randomDelayMillis(random, min, max);

        scheduler.schedule(
            () -> kitchen.pickup(order.getId()),
            delayMillis,
            TimeUnit.MILLISECONDS
        );

        Thread.sleep(rate.toMillis());
      }

      LOGGER.info("Finished placing all orders");
      scheduler.shutdown();
      LOGGER.info("Waiting for scheduler termination");
      scheduler.awaitTermination(max.toMillis() + 30_000L, TimeUnit.MILLISECONDS);
      LOGGER.info("Scheduler terminated");

      LOGGER.info("Waiting for kitchen to drain");
      kitchenManager.awaitAllOrdersProcessed();
      LOGGER.info("Kitchen drained");

      actions = kitchenManager.snapshotActions();


      // ----------------------------------------------------------------------

      String result = client.solveProblem(problem.getTestId(), rate, min, max, actions);
      LOGGER.info("Result: {}", result);

    } catch (IOException | InterruptedException e) {
      LOGGER.error("Execution failed: {}", e.getMessage());
    } finally {
      if (scheduler != null && !scheduler.isShutdown()) {
          scheduler.shutdownNow();
      }

      if (kitchenManager != null) {
          kitchenManager.close();   // stops kitchen worker thread
      }
    }
  }

  private long randomDelayMillis(Random random, Duration min, Duration max) {
    long minMs = min.toMillis();
    long maxMs = max.toMillis();

    if (maxMs <= minMs) {
      return minMs;
    }

    long delta = maxMs - minMs;
    long offset = (long) (random.nextDouble() * (delta + 1));
    return minMs + offset;
  }

  public static void main(String[] args) {
    new CommandLine(new Main()).execute(args);
  }
}
