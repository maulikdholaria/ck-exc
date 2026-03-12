package com.css.challenge.kitchen;

public interface DiscardPolicy {
    OrderState selectDiscardCandidate(Storage storage);
}