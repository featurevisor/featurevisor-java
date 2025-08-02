package com.featurevisor.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;

public class EmitterTest {

    private Emitter emitter;
    private List<Emitter.EventDetails> handledDetails;

    @BeforeEach
    public void setUp() {
        emitter = new Emitter();
        handledDetails = new ArrayList<>();
    }

    private void handleDetails(Emitter.EventDetails details) {
        handledDetails.add(details);
    }

    @Test
    public void testAddListenerForEvent() {
        // Add a listener for datafile_set event
        Emitter.UnsubscribeFunction unsubscribe = emitter.on(Emitter.EventName.DATAFILE_SET, this::handleDetails);

        // Verify the listener was added
        assertTrue(emitter.getListeners().containsKey(Emitter.EventName.DATAFILE_SET));
        assertTrue(emitter.getListeners().get(Emitter.EventName.DATAFILE_SET).size() > 0);

        // Verify other events don't have listeners
        assertFalse(emitter.getListeners().containsKey(Emitter.EventName.CONTEXT_SET));
        assertFalse(emitter.getListeners().containsKey(Emitter.EventName.STICKY_SET));

        // Verify there's exactly one listener
        assertEquals(1, emitter.getListeners().get(Emitter.EventName.DATAFILE_SET).size());

        // Trigger the subscribed event
        Emitter.EventDetails details1 = new Emitter.EventDetails();
        details1.put("key", "value");
        emitter.trigger(Emitter.EventName.DATAFILE_SET, details1);

        // Verify the callback was called
        assertEquals(1, handledDetails.size());
        assertEquals("value", handledDetails.get(0).get("key"));

        // Trigger an unsubscribed event
        Emitter.EventDetails details2 = new Emitter.EventDetails();
        details2.put("key", "value2");
        emitter.trigger(Emitter.EventName.STICKY_SET, details2);

        // Verify the callback was not called for the unsubscribed event
        assertEquals(1, handledDetails.size());

        // Unsubscribe
        unsubscribe.unsubscribe();
        assertEquals(0, emitter.getListeners().get(Emitter.EventName.DATAFILE_SET).size());

        // Clear all
        emitter.clearAll();
        assertTrue(emitter.getListeners().isEmpty());
    }

    @Test
    public void testMultipleListeners() {
        List<Emitter.EventDetails> secondHandler = new ArrayList<>();

        // Add two listeners for the same event
        Emitter.UnsubscribeFunction unsubscribe1 = emitter.on(Emitter.EventName.CONTEXT_SET, this::handleDetails);
        Emitter.UnsubscribeFunction unsubscribe2 = emitter.on(Emitter.EventName.CONTEXT_SET, secondHandler::add);

        // Verify both listeners were added
        assertEquals(2, emitter.getListeners().get(Emitter.EventName.CONTEXT_SET).size());

        // Trigger the event
        Emitter.EventDetails details = new Emitter.EventDetails();
        details.put("test", "multiple");
        emitter.trigger(Emitter.EventName.CONTEXT_SET, details);

        // Verify both callbacks were called
        assertEquals(1, handledDetails.size());
        assertEquals(1, secondHandler.size());
        assertEquals("multiple", handledDetails.get(0).get("test"));
        assertEquals("multiple", secondHandler.get(0).get("test"));

        // Unsubscribe one listener
        unsubscribe1.unsubscribe();
        assertEquals(1, emitter.getListeners().get(Emitter.EventName.CONTEXT_SET).size());

        // Trigger again
        Emitter.EventDetails details2 = new Emitter.EventDetails();
        details2.put("test", "single");
        emitter.trigger(Emitter.EventName.CONTEXT_SET, details2);

        // Verify only the remaining callback was called
        assertEquals(1, handledDetails.size()); // Should still be 1
        assertEquals(2, secondHandler.size()); // Should be 2
        assertEquals("single", secondHandler.get(1).get("test"));

        // Clean up
        unsubscribe2.unsubscribe();
        emitter.clearAll();
    }

    @Test
    public void testTriggerWithoutDetails() {
        // Add a listener
        emitter.on(Emitter.EventName.STICKY_SET, this::handleDetails);

        // Trigger without details
        emitter.trigger(Emitter.EventName.STICKY_SET);

        // Verify the callback was called with empty details
        assertEquals(1, handledDetails.size());
        assertTrue(handledDetails.get(0).isEmpty());
    }

    @Test
    public void testTriggerNonExistentEvent() {
        // Try to trigger an event with no listeners
        Emitter.EventDetails details = new Emitter.EventDetails();
        details.put("key", "value");

        // This should not throw an exception
        assertDoesNotThrow(() -> {
            emitter.trigger(Emitter.EventName.DATAFILE_SET, details);
        });

        // Verify no callbacks were called
        assertEquals(0, handledDetails.size());
    }

    @Test
    public void testUnsubscribeMultipleTimes() {
        // Add a listener
        Emitter.UnsubscribeFunction unsubscribe = emitter.on(Emitter.EventName.CONTEXT_SET, this::handleDetails);

        // Unsubscribe once
        unsubscribe.unsubscribe();
        assertEquals(0, emitter.getListeners().get(Emitter.EventName.CONTEXT_SET).size());

        // Try to unsubscribe again (should be safe)
        assertDoesNotThrow(() -> {
            unsubscribe.unsubscribe();
        });

        // Verify still no listeners
        assertEquals(0, emitter.getListeners().get(Emitter.EventName.CONTEXT_SET).size());
    }

    @Test
    public void testEventNameEnum() {
        // Test enum values
        assertEquals("datafile_set", Emitter.EventName.DATAFILE_SET.getValue());
        assertEquals("context_set", Emitter.EventName.CONTEXT_SET.getValue());
        assertEquals("sticky_set", Emitter.EventName.STICKY_SET.getValue());

        // Test fromString method
        assertEquals(Emitter.EventName.DATAFILE_SET, Emitter.EventName.fromString("datafile_set"));
        assertEquals(Emitter.EventName.CONTEXT_SET, Emitter.EventName.fromString("context_set"));
        assertEquals(Emitter.EventName.STICKY_SET, Emitter.EventName.fromString("sticky_set"));

        // Test invalid event name
        assertThrows(IllegalArgumentException.class, () -> {
            Emitter.EventName.fromString("invalid_event");
        });
    }

    @Test
    public void testEventDetails() {
        // Test EventDetails constructor
        Emitter.EventDetails details = new Emitter.EventDetails();
        details.put("string", "value");
        details.put("number", 42);
        details.put("boolean", true);

        assertEquals("value", details.get("string"));
        assertEquals(42, details.get("number"));
        assertEquals(true, details.get("boolean"));

        // Test EventDetails with map constructor
        Emitter.EventDetails original = new Emitter.EventDetails();
        original.put("key", "value");

        Emitter.EventDetails copy = new Emitter.EventDetails(original);
        assertEquals("value", copy.get("key"));

        // Verify it's a copy, not a reference
        original.put("newKey", "newValue");
        assertNull(copy.get("newKey"));
    }
}
