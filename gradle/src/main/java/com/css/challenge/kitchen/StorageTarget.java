package com.css.challenge.kitchen;

import com.css.challenge.client.Action;
import com.css.challenge.client.Order;

public enum StorageTarget {
    HEATER,
    COOLER,
    SHELF;

    public static StorageTarget idealFor(Order order) {
        if ("hot".equals(order.getTemp())) {
            return HEATER;
        }
        if ("cold".equals(order.getTemp())) {
            return COOLER;
        }
        return SHELF;
    }

    public String actionTarget() {
        switch (this) {
            case HEATER:
                return Action.HEATER;
            case COOLER:
                return Action.COOLER;
            default:
                return Action.SHELF;
        }
    }
}