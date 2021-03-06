package com.atomiccomics.crusoe.player;

import com.atomiccomics.crusoe.Handler;
import com.atomiccomics.crusoe.RegisteredComponent;
import com.atomiccomics.crusoe.item.Item;
import com.google.inject.Singleton;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Singleton
@RegisteredComponent
public final class Holder {

    private final Set<Item> inventory = new CopyOnWriteArraySet<>();

    @Handler(ItemPickedUp.class)
    public void handleItemPickedUp(final ItemPickedUp event) {
        inventory.add(event.item());
    }

    @Handler(ItemDropped.class)
    public void handleItemDropped(final ItemDropped event) {
        inventory.add(event.item());
    }

    public boolean hasItems() {
        return !inventory.isEmpty();
    }

    public boolean has(final Item item) {
        return inventory.contains(item);
    }

}
