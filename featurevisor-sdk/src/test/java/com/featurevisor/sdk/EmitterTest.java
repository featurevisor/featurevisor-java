package com.featurevisor.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;

public class EmitterTest {

    private Emitter emitter;
    private List<FeaturevisorEventDetails> handledDetails;

    @BeforeEach
    public void setUp() {
        emitter = new Emitter();
        handledDetails = new ArrayList<>();
    }

    private void handleDetails(FeaturevisorEventDetails details) {
        handledDetails.add(details);
    }

    @Test
    public void testAddListenerForEvent() {
        // Add a listener for datafile_set event
        FeaturevisorUnsubscribe unsubscribe = emitter.on(FeaturevisorEventName.DATAFILE_SET, this::handleDetails);

        // Verify the listener was added
        assertTrue(emitter.getListeners().containsKey(FeaturevisorEventName.DATAFILE_SET));
        assertTrue(emitter.getListeners().get(FeaturevisorEventName.DATAFILE_SET).size() > 0);

        // Verify other events don't have listeners
        assertFalse(emitter.getListeners().containsKey(FeaturevisorEventName.CONTEXT_SET));
        assertFalse(emitter.getListeners().containsKey(FeaturevisorEventName.STICKY_FEATURES_SET));

        // Verify there's exactly one listener
        assertEquals(1, emitter.getListeners().get(FeaturevisorEventName.DATAFILE_SET).size());

        // Trigger the subscribed event
        FeaturevisorEventDetails details1 = new FeaturevisorEventDetails();
        details1.put("key", "value");
        emitter.trigger(FeaturevisorEventName.DATAFILE_SET, details1);

        // Verify the callback was called
        assertEquals(1, handledDetails.size());
        assertEquals("value", handledDetails.get(0).get("key"));

        // Trigger an unsubscribed event
        FeaturevisorEventDetails details2 = new FeaturevisorEventDetails();
        details2.put("key", "value2");
        emitter.trigger(FeaturevisorEventName.STICKY_FEATURES_SET, details2);

        // Verify the callback was not called for the unsubscribed event
        assertEquals(1, handledDetails.size());

        // Unsubscribe
        unsubscribe.unsubscribe();
        assertEquals(0, emitter.getListeners().get(FeaturevisorEventName.DATAFILE_SET).size());

        // Clear all
        emitter.clearAll();
        assertTrue(emitter.getListeners().isEmpty());
    }

    @Test
    public void testMultipleListeners() {
        List<FeaturevisorEventDetails> secondHandler = new ArrayList<>();

        // Add two listeners for the same event
        FeaturevisorUnsubscribe unsubscribe1 = emitter.on(FeaturevisorEventName.CONTEXT_SET, this::handleDetails);
        FeaturevisorUnsubscribe unsubscribe2 = emitter.on(FeaturevisorEventName.CONTEXT_SET, secondHandler::add);

        // Verify both listeners were added
        assertEquals(2, emitter.getListeners().get(FeaturevisorEventName.CONTEXT_SET).size());

        // Trigger the event
        FeaturevisorEventDetails details = new FeaturevisorEventDetails();
        details.put("test", "multiple");
        emitter.trigger(FeaturevisorEventName.CONTEXT_SET, details);

        // Verify both callbacks were called
        assertEquals(1, handledDetails.size());
        assertEquals(1, secondHandler.size());
        assertEquals("multiple", handledDetails.get(0).get("test"));
        assertEquals("multiple", secondHandler.get(0).get("test"));

        // Unsubscribe one listener
        unsubscribe1.unsubscribe();
        assertEquals(1, emitter.getListeners().get(FeaturevisorEventName.CONTEXT_SET).size());

        // Trigger again
        FeaturevisorEventDetails details2 = new FeaturevisorEventDetails();
        details2.put("test", "single");
        emitter.trigger(FeaturevisorEventName.CONTEXT_SET, details2);

        // Verify only the remaining callback was called
        assertEquals(1, handledDetails.size()); // Should still be 1
        assertEquals(2, secondHandler.size()); // Should be 2
        assertEquals("single", secondHandler.get(1).get("test"));

        // Clean up
        unsubscribe2.unsubscribe();
        emitter.clearAll();
    }

    @Test
    public void testTriggerUsesListenerSnapshot() {
        List<String> calls = new ArrayList<>();
        final FeaturevisorUnsubscribe[] unsubscribeSecond = new FeaturevisorUnsubscribe[1];

        emitter.on(FeaturevisorEventName.STICKY_FEATURES_SET, details -> {
            calls.add("first");
            unsubscribeSecond[0].unsubscribe();
        });
        unsubscribeSecond[0] = emitter.on(FeaturevisorEventName.STICKY_FEATURES_SET, details -> calls.add("second"));

        emitter.trigger(FeaturevisorEventName.STICKY_FEATURES_SET);
        emitter.trigger(FeaturevisorEventName.STICKY_FEATURES_SET);

        assertEquals(List.of("first", "second", "first"), calls);
    }

    @Test
    public void testTriggerWithoutDetails() {
        // Add a listener
        emitter.on(FeaturevisorEventName.STICKY_FEATURES_SET, this::handleDetails);

        // Trigger without details
        emitter.trigger(FeaturevisorEventName.STICKY_FEATURES_SET);

        // Verify the callback was called with empty details
        assertEquals(1, handledDetails.size());
        assertTrue(handledDetails.get(0).isEmpty());
    }

    @Test
    public void testTriggerNonExistentEvent() {
        // Try to trigger an event with no listeners
        FeaturevisorEventDetails details = new FeaturevisorEventDetails();
        details.put("key", "value");

        // This should not throw an exception
        assertDoesNotThrow(() -> {
            emitter.trigger(FeaturevisorEventName.DATAFILE_SET, details);
        });

        // Verify no callbacks were called
        assertEquals(0, handledDetails.size());
    }

    @Test
    public void testUnsubscribeMultipleTimes() {
        // Add a listener
        FeaturevisorUnsubscribe unsubscribe = emitter.on(FeaturevisorEventName.CONTEXT_SET, this::handleDetails);

        // Unsubscribe once
        unsubscribe.unsubscribe();
        assertEquals(0, emitter.getListeners().get(FeaturevisorEventName.CONTEXT_SET).size());

        // Try to unsubscribe again (should be safe)
        assertDoesNotThrow(() -> {
            unsubscribe.unsubscribe();
        });

        // Verify still no listeners
        assertEquals(0, emitter.getListeners().get(FeaturevisorEventName.CONTEXT_SET).size());
    }

    @Test
    public void testEventNameEnum() {
        // Test enum values
        assertEquals("datafile_set", FeaturevisorEventName.DATAFILE_SET.getValue());
        assertEquals("context_set", FeaturevisorEventName.CONTEXT_SET.getValue());
        assertEquals("sticky_features_set", FeaturevisorEventName.STICKY_FEATURES_SET.getValue());

        // Test fromString method
        assertEquals(FeaturevisorEventName.DATAFILE_SET, FeaturevisorEventName.fromString("datafile_set"));
        assertEquals(FeaturevisorEventName.CONTEXT_SET, FeaturevisorEventName.fromString("context_set"));
        assertEquals(FeaturevisorEventName.STICKY_FEATURES_SET, FeaturevisorEventName.fromString("sticky_features_set"));

        // Test invalid event name
        assertThrows(IllegalArgumentException.class, () -> {
            FeaturevisorEventName.fromString("invalid_event");
        });
    }

    @Test
    public void testEventDetails() {
        // Test EventDetails constructor
        FeaturevisorEventDetails details = new FeaturevisorEventDetails();
        details.put("string", "value");
        details.put("number", 42);
        details.put("boolean", true);

        assertEquals("value", details.get("string"));
        assertEquals(42, details.get("number"));
        assertEquals(true, details.get("boolean"));

        // Test EventDetails with map constructor
        FeaturevisorEventDetails original = new FeaturevisorEventDetails();
        original.put("key", "value");

        FeaturevisorEventDetails copy = new FeaturevisorEventDetails(original);
        assertEquals("value", copy.get("key"));

        // Verify it's a copy, not a reference
        original.put("newKey", "newValue");
        assertNull(copy.get("newKey"));
    }
}
