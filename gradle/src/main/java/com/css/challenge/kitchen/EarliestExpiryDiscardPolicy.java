package com.css.challenge.kitchen;

public final class EarliestExpiryDiscardPolicy implements DiscardPolicy {
    @Override
    public OrderState selectDiscardCandidate(Storage storage) {
        return storage.peekBestShelfDiscardCandidate();
    }
}