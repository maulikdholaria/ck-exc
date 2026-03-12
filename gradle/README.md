# README

Author: `maulikdholaria`

## How to run

## Overview

This project implements the order fulfillment system described in the Take Home challenge. 
The program fetches a problem from the challenge server, simulates order placement and pickup 
using the provided harness parameters, records all kitchen actions (`place`, `move`, `pickup`, 
`discard`), and submits the results for validation.

# Building the Project

The project uses **Gradle**.

Build the project:

```
./gradlew clean build
```

---

# Running the Harness

Run the program using the Gradle run task:

```
./gradlew run --args="--auth=YOUR_AUTH_TOKEN"
```

Example with additional parameters:

```
./gradlew run --args="--auth=YOUR_AUTH_TOKEN --seed=100 --rate=PT0.5S --min=PT4S --max=PT8S"
```


# Running via Docker

Build the Docker image:

```
docker build -t ck-challenge .
```

Run the program:

```
docker run --rm ck-challenge --auth=YOUR_AUTH_TOKEN
```

Example:

```
docker run --rm ck-challenge \
  --auth=YOUR_AUTH_TOKEN \
  --seed=100 \
  --rate=PT0.5S \
  --min=PT4S \
  --max=PT8S
```

# Algorithm

1. Storage.java

- map for each storage + global ( for faster + easier lookup )
```
 - globalMap: private final Map<String, OrderState> byId = new HashMap<>()
 - coolerMap: private final Map<String, OrderState> cooler = new HashMap<>()
 - heaterMap: private final Map<String, OrderState> heater = new HashMap<>()
 - shelfMap: private final Map<String, OrderState> shelf = new HashMap<>()
```


- heap for efficient discard (priority heap ordered by remaining freshness)
```
private final PriorityQueue<ShelfEntry> shelfHeap =
            new PriorityQueue<>(Comparator.comparingDouble(ShelfEntry::getRemainingFreshness));
```
Lazy validation with version numbers ensures stale heap entries are 
ignored without requiring expensive heap updates ( search & remove ).
This allows discard selection in **O(log n)** time.

# Order placement
- Ideal storage place available:
    - insert to globalMap + ( coolerMap | heaterMap | shelfMap )
    - insert to shelfHeap
- Shelf available as ideal storage is full
    - insert to globalMap + shelfMap
    - insert to shelfHeap
- Try to move from shelf to cooler or heater & then add to shelf
    - if cooler has space, find a cold order currently on shelf & move or do the same for heater + hot
    - insert to globalMap + shelfMap
    - insert to shelfHeap
- Discard order
    - find order from shelfHeap for discard
    - remove entry for order to be discarded from globalMap + ( coolerMap | heaterMap | shelfMap )
    - insert new order to globalMap + ( coolerMap | heaterMap | shelfMap )
    - insert new order to shelfHeap
    
# Order pickup
- If no order available in globalMap then return
- If order is expired then mark as discard and remove form globalMap + ( coolerMap | heaterMap | shelfMap )
- If order is fresh then mark as pickup and remove form globalMap + ( coolerMap | heaterMap | shelfMap )
