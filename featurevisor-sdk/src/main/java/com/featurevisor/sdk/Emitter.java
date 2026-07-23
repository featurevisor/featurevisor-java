package com.featurevisor.sdk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

final class Emitter {
    private final Map<FeaturevisorEventName, List<FeaturevisorEventHandler>> listeners =
        new HashMap<>();

    FeaturevisorUnsubscribe on(
            FeaturevisorEventName eventName,
            FeaturevisorEventHandler callback) {
        listeners.computeIfAbsent(eventName, ignored -> new CopyOnWriteArrayList<>()).add(callback);
        final boolean[] active = {true};

        return () -> {
            if (!active[0]) {
                return;
            }
            active[0] = false;
            List<FeaturevisorEventHandler> currentListeners = listeners.get(eventName);
            if (currentListeners != null) {
                currentListeners.remove(callback);
            }
        };
    }

    void trigger(FeaturevisorEventName eventName, FeaturevisorEventDetails details) {
        List<FeaturevisorEventHandler> eventListeners = listeners.get(eventName);
        if (eventListeners == null) {
            return;
        }

        for (FeaturevisorEventHandler listener : eventListeners) {
            try {
                listener.handle(details);
            } catch (Exception error) {
                System.err.println("Error in event listener: " + error.getMessage());
                error.printStackTrace();
            }
        }
    }

    void trigger(FeaturevisorEventName eventName) {
        trigger(eventName, new FeaturevisorEventDetails());
    }

    void clearAll() {
        listeners.clear();
    }

    Map<FeaturevisorEventName, List<FeaturevisorEventHandler>> getListeners() {
        return new HashMap<>(listeners);
    }
}
