package com.featurevisor.sdk;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;
import java.util.ArrayList;

/**
 * Utility class for comparing semantic versions
 * Based on the TypeScript implementation from: https://github.com/omichelsen/compare-versions
 */
public class CompareVersions {

    private static final Pattern SEMVER_PATTERN = Pattern.compile(
        "^[v^~<>=]*?(\\d+)(?:\\.([x*]|\\d+)(?:\\.([x*]|\\d+)(?:\\.([x*]|\\d+))?(?:-([\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?(?:\\+[\\da-z\\-]+(?:\\.[\\da-z\\-]+)*)?)?)?$",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Compare two semantic version strings
     * @param v1 First version string
     * @param v2 Second version string
     * @return -1 if v1 < v2, 0 if equal, 1 if v1 > v2
     */
    public static int compareVersions(String v1, String v2) {
        // validate input and split into segments
        List<String> n1 = validateAndParse(v1);
        List<String> n2 = validateAndParse(v2);

        // pop off the patch
        String p1 = n1.size() > 0 ? n1.remove(n1.size() - 1) : null;
        String p2 = n2.size() > 0 ? n2.remove(n2.size() - 1) : null;

        // validate numbers
        int r = compareSegments(n1, n2);
        if (r != 0) return r;

        // validate pre-release
        if (p1 != null && p2 != null) {
            return compareSegments(splitByDot(p1), splitByDot(p2));
        } else if (p1 != null || p2 != null) {
            return p1 != null ? -1 : 1;
        }

        return 0;
    }

    private static List<String> validateAndParse(String version) {
        if (version == null) {
            throw new TypeError("Invalid argument expected string");
        }

        Matcher match = SEMVER_PATTERN.matcher(version);
        if (!match.matches()) {
            throw new Error("Invalid argument not valid semver ('" + version + "' received)");
        }

        List<String> result = new ArrayList<>();
        for (int i = 1; i <= match.groupCount(); i++) {
            String group = match.group(i);
            if (group != null) {
                result.add(group);
            }
        }
        return result;
    }

    private static boolean isWildcard(String s) {
        return "*".equals(s) || "x".equalsIgnoreCase(s) || "X".equals(s);
    }

    private static Object[] forceType(Object a, Object b) {
        if (a.getClass() != b.getClass()) {
            return new Object[]{String.valueOf(a), String.valueOf(b)};
        }
        return new Object[]{a, b};
    }

    private static Object tryParse(String v) {
        try {
            int n = Integer.parseInt(v);
            return n;
        } catch (NumberFormatException e) {
            return v;
        }
    }

    private static int compareStrings(String a, String b) {
        if (isWildcard(a) || isWildcard(b)) return 0;

        Object[] forced = forceType(tryParse(a), tryParse(b));
        Object ap = forced[0];
        Object bp = forced[1];

        if (ap instanceof Number && bp instanceof Number) {
            int aNum = ((Number) ap).intValue();
            int bNum = ((Number) bp).intValue();
            if (aNum > bNum) return 1;
            if (aNum < bNum) return -1;
            return 0;
        } else {
            return ap.toString().compareTo(bp.toString());
        }
    }

    private static int compareSegments(List<String> a, List<String> b) {
        int maxLength = Math.max(a.size(), b.size());

        for (int i = 0; i < maxLength; i++) {
            String aVal = i < a.size() ? a.get(i) : "0";
            String bVal = i < b.size() ? b.get(i) : "0";

            int r = compareStrings(aVal, bVal);
            if (r != 0) return r;
        }

        return 0;
    }

    private static List<String> splitByDot(String str) {
        List<String> result = new ArrayList<>();
        if (str != null) {
            String[] parts = str.split("\\.");
            for (String part : parts) {
                result.add(part);
            }
        }
        return result;
    }

    // Custom exception classes to match TypeScript behavior
    public static class TypeError extends RuntimeException {
        public TypeError(String message) {
            super(message);
        }
    }

    public static class Error extends RuntimeException {
        public Error(String message) {
            super(message);
        }
    }
}
