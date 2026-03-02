package com.featurevisor.sdk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NamespaceGuardTest {
    private static final String LEGACY_NAMESPACE = "com.featurevisor." + "types";

    @Test
    public void noLegacyTypesNamespaceReferencesRemain() throws IOException {
        List<Path> roots = List.of(
            Path.of("src/main/java"),
            Path.of("src/test/java")
        );

        List<String> violations;
        try (Stream<Path> paths = roots.stream().flatMap(root -> {
            try {
                return Files.walk(root);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        })) {
            violations = paths
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("NamespaceGuardTest.java"))
                .flatMap(path -> {
                    try {
                        List<String> lines = Files.readAllLines(path);
                        return lines.stream()
                            .filter(line -> line.contains(LEGACY_NAMESPACE))
                            .map(line -> path + " -> " + line.trim());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }

        assertTrue(
            violations.isEmpty(),
            "Legacy namespace references found:\n" + String.join("\n", violations)
        );
    }
}
