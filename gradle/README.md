# README

Author: `maulikdholaria`

## How to run

# Create a README.md file with the requested content

content = """
# CloudKitchens Order Fulfillment Challenge

## Overview

This project implements the order fulfillment system described in the CloudKitchens challenge. 
The program fetches a problem from the challenge server, simulates order placement and pickup 
using the provided harness parameters, records all kitchen actions (`place`, `move`, `pickup`, 
`discard`), and submits the results for validation.

The implementation focuses on correctness, predictable concurrency behavior, and efficient 
selection of discard candidates when storage is full.

---

# Building the Project

The project uses **Gradle**.

Build the project:

./gradlew clean build

---

# Running the Harness

Run the program using the Gradle run task:

./gradlew run --args="--auth=YOUR_AUTH_TOKEN"

Example with additional parameters:

./gradlew run --args="--auth=YOUR_AUTH_TOKEN --seed=100 --rate=PT0.5S --min=PT4S --max=PT8S"

Supported parameters:

| Parameter | Description |
|---|---|
| --auth | Authentication token provided by CloudKitchens |
| --seed | Random seed for reproducible runs |
| --rate | Order arrival rate |
| --min | Minimum pickup delay |
| --max | Maximum pickup delay |
| --name | Optional identifier for submissions |

---

# Running via Docker

Build the Docker image:

docker build -t ck-challenge .

Run the program:

docker run --rm ck-challenge --auth=YOUR_AUTH_TOKEN

Example:

docker run --rm ck-challenge \
  --auth=YOUR_AUTH_TOKEN \
  --seed=100 \
  --rate=PT0.5S \
  --min=PT4S \
  --max=PT8S

---

# Discard Strategy

When the shelf is full and a new order cannot be placed in its ideal storage, the system attempts 
the following steps:

1. Move a compatible shelf order to its ideal storage if capacity exists.
2. If no move is possible, discard an order from the shelf.

The discard candidate is selected based on **earliest expiration (lowest remaining freshness)**.

Orders stored on the shelf degrade faster than those stored in their ideal storage, especially 
for hot and cold items. Selecting the order that will expire soonest minimizes wasted freshness 
and maximizes the likelihood that remaining orders will still be valid when their pickup occurs.

To efficiently select the discard candidate, shelf orders are tracked in a **priority heap ordered 
by remaining freshness**. Lazy validation with version numbers ensures stale heap entries are 
ignored without requiring expensive heap updates.

This allows discard selection in **O(log n)** time.

---

# Assumptions and Clarifications

Some aspects of the problem statement required interpretation:

### Shelf Movement Priority
When the shelf is full, the implementation attempts to **move a shelf order to its ideal storage** 
before discarding any order. This reduces unnecessary discards and preserves freshness.

### Expiration Handling
An order that has expired by the time its pickup occurs is discarded rather than picked up.

### Concurrency Model
All kitchen state mutations are handled by a **single worker thread** inside `KitchenManager`.  
External threads (such as pickup schedulers) submit commands via a queue. This ensures consistent 
ordering of actions while avoiding race conditions.

### Heap Validation
Shelf discard candidates are stored in a priority heap. Since shelf entries may move or be removed, 
stale entries are filtered using **version checks** when selecting the discard candidate.

---

# Summary

The implementation prioritizes:

- correctness of order lifecycle
- efficient discard selection
- deterministic behavior under concurrency
- compatibility with the provided challenge harness.
"""

path = "/mnt/data/README.md"
with open(path, "w") as f:
    f.write(content)

path

## How to runold 

The `Dockerfile` defines a self-contained Java/Gradle reference environment.
Build and run the program using [Docker](https://docs.docker.com/get-started/get-docker/):
```
$ docker build -t challenge .
$ docker run --rm -it challenge --auth=<token>
```
Feel free to modify the `Dockerfile` as you see fit.

If java `25` or later is installed locally, run the program directly for convenience:
```
$ ./gradlew run --args="--auth=<token>"
```

## Discard criteria

`<your chosen discard criteria and rationale here>`
