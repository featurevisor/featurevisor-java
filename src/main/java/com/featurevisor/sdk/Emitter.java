package com.featurevisor.sdk;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Event emitter for Featurevisor SDK
 * Handles event subscription and triggering
 */
public class Emitter {

    /**
     * Event names that can be emitted
     */
    public enum EventName {
        DATAFILE_SET("datafile_set"),
        CONTEXT_SET("context_set"),
        STICKY_SET("sticky_set");

        private final String value;

        EventName(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static EventName fromString(String value) {
            for (EventName eventName : EventName.values()) {
                if (eventName.value.equals(value)) {
                    return eventName;
                }
            }
            throw new IllegalArgumentException("Unknown event name: " + value);
        }
    }

    /**
     * Event details type
     */
    public static class EventDetails extends HashMap<String, Object> {
        public EventDetails() {
            super();
        }

        public EventDetails(Map<String, Object> map) {
            super(map);
        }
    }

    /**
     * Event callback interface
     */
    @FunctionalInterface
    public interface EventCallback {
        void call(EventDetails details);
    }

    /**
     * Unsubscribe function interface
     */
    @FunctionalInterface
    public interface UnsubscribeFunction {
        void unsubscribe();
    }

    private final Map<EventName, List<EventCallback>> listeners;

    public Emitter() {
        this.listeners = new HashMap<>();
    }

    /**
     * Subscribe to an event
     * @param eventName The event name to subscribe to
     * @param callback The callback function to execute when the event is triggered
     * @return An unsubscribe function to remove the listener
     */
    public UnsubscribeFunction on(EventName eventName, EventCallback callback) {
        if (!listeners.containsKey(eventName)) {
            listeners.put(eventName, new CopyOnWriteArrayList<>());
        }

        List<EventCallback> eventListeners = listeners.get(eventName);
        eventListeners.add(callback);

        // Track if the subscription is still active
        final boolean[] isActive = {true};

        return () -> {
            if (!isActive[0]) {
                return;
            }

            isActive[0] = false;

            List<EventCallback> currentListeners = listeners.get(eventName);
            if (currentListeners != null) {
                currentListeners.remove(callback);
            }
        };
    }

    /**
     * Trigger an event with optional details
     * @param eventName The event name to trigger
     * @param details Optional event details
     */
    public void trigger(EventName eventName, EventDetails details) {
        List<EventCallback> eventListeners = listeners.get(eventName);

        if (eventListeners == null) {
            return;
        }

        for (EventCallback listener : eventListeners) {
            try {
                listener.call(details);
            } catch (Exception err) {
                System.err.println("Error in event listener: " + err.getMessage());
                err.printStackTrace();
            }
        }
    }

    /**
     * Trigger an event with empty details
     * @param eventName The event name to trigger
     */
    public void trigger(EventName eventName) {
        trigger(eventName, new EventDetails());
    }

    /**
     * Clear all event listeners
     */
    public void clearAll() {
        listeners.clear();
    }

    /**
     * Get the current listeners (for testing purposes)
     * @return A copy of the listeners map
     */
    Map<EventName, List<EventCallback>> getListeners() {
        return new HashMap<>(listeners);
    }
}
