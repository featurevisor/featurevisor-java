package com.featurevisor.sdk;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

final class SemverConformanceTest {
    @Test
    void portableComparisons() throws Exception {
        JsonNode fixture;
        try (InputStream stream = getClass().getResourceAsStream("/conformance/semver.json")) {
            assertNotNull(stream);
            fixture = new ObjectMapper().readTree(stream);
        }
        assertEquals("featurevisor.semver", fixture.get("kind").asText());
        assertEquals(1, fixture.get("version").asInt());
        assertEquals(44, fixture.get("cases").size());
        for (JsonNode entry : fixture.get("cases")) {
            for (boolean reverse : new boolean[]{false, true}) {
                String a = entry.get(reverse ? "right" : "left").asText();
                String b = entry.get(reverse ? "left" : "right").asText();
                String label = entry.get("name").asText() + " reverse=" + reverse;
                if (entry.get("order").isNull()) {
                    assertThrows(RuntimeException.class, () -> CompareVersions.compareVersions(a, b), label);
                } else {
                    int expected = entry.get("order").asInt() * (reverse ? -1 : 1);
                    assertEquals(expected, CompareVersions.compareVersions(a, b), label);
                }
            }
        }
    }
}
