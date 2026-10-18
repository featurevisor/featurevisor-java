package com.featurevisor.sdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable interpreter for compiled variable override operations. */
final class VariableMutations {
    private static final List<String> OPERATIONS = List.of(
        "set", "append", "prepend", "appendUnique", "removeValue", "before", "after", "remove"
    );
    private static final List<String> DANGEROUS_KEYS = List.of("__proto__", "prototype", "constructor");

    private VariableMutations() {}

    static Object apply(Object value, List<VariableMutationOperation> operations) {
        if (operations == null) throw new IllegalArgumentException("Invalid variable mutation operations");
        Object current = value;
        for (VariableMutationOperation operation : operations) {
            validate(operation);
            current = updateAtPath(current, operation, 0);
        }
        return current;
    }

    private static void validate(VariableMutationOperation operation) {
        if (operation == null || operation.getOp() == null || !(operation.getPath() instanceof List)
                || !OPERATIONS.contains(operation.getOp())) {
            throw new IllegalArgumentException("Invalid variable mutation operation");
        }
        if (!"remove".equals(operation.getOp()) && !operation.hasValue()) {
            throw new IllegalArgumentException("Variable mutation operation requires a value");
        }
        if (("appendUnique".equals(operation.getOp()) || "removeValue".equals(operation.getOp()))
                && (!isPrimitive(operation.getValue()) || isNonFinite(operation.getValue()))) {
            throw new IllegalArgumentException("Variable mutation operation requires a primitive value");
        }
        for (Object part : (List<?>) operation.getPath()) {
            String property = null;
            if (part instanceof String) {
                property = (String) part;
            } else if (part instanceof Number && isIndex(part)) {
                // Valid numeric array index.
            } else if (part instanceof Map) {
                property = selectorProperty(part);
            } else {
                throw new IllegalArgumentException("Invalid variable mutation path");
            }
            if (property != null && DANGEROUS_KEYS.contains(property)) {
                throw new IllegalArgumentException("Unsafe variable mutation key");
            }
        }
    }

    private static boolean isPrimitive(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private static boolean isNonFinite(Object value) {
        return value instanceof Number && !Double.isFinite(((Number) value).doubleValue());
    }

    private static boolean isIndex(Object value) {
        if (!(value instanceof Number)) return false;
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) && number >= 0 && number == Math.rint(number) && number <= Integer.MAX_VALUE;
    }

    private static String selectorProperty(Object part) {
        if (!(part instanceof Map)) throw new IllegalArgumentException("Invalid variable mutation path");
        Object selectValue = ((Map<?, ?>) part).get("select");
        if (!(selectValue instanceof Map)) throw new IllegalArgumentException("Invalid variable mutation path");
        Map<?, ?> select = (Map<?, ?>) selectValue;
        Object property = select.get("property");
        Object values = select.get("values");
        if (!(property instanceof String) || !(values instanceof List)) {
            throw new IllegalArgumentException("Invalid variable mutation path");
        }
        for (Object value : (List<?>) values) {
            if (!isPrimitive(value) || isNonFinite(value)) {
                throw new IllegalArgumentException("Invalid variable mutation path");
            }
        }
        return (String) property;
    }

    private static int selectedIndex(List<?> list, Object part) {
        Map<?, ?> select = (Map<?, ?>) ((Map<?, ?>) part).get("select");
        String property = (String) select.get("property");
        List<?> values = (List<?>) select.get("values");
        for (int index = 0; index < list.size(); index++) {
            Object candidate = list.get(index);
            if (!(candidate instanceof Map) || !((Map<?, ?>) candidate).containsKey(property)) continue;
            Object propertyValue = ((Map<?, ?>) candidate).get(property);
            if (!isPrimitive(propertyValue)) continue;
            for (Object value : values) if (scalarEquals(propertyValue, value)) return index;
        }
        return -1;
    }

    private static Object key(Object container, Object part) {
        if (part instanceof String) {
            if (!(container instanceof Map)) throw new IllegalArgumentException("Variable mutation expected an object");
            return part;
        }
        if (!(container instanceof List)) throw new IllegalArgumentException("Variable mutation expected an array");
        return part instanceof Number ? ((Number) part).intValue() : selectedIndex((List<?>) container, part);
    }

    private static boolean exists(Object container, Object key) {
        return key instanceof String
            ? ((Map<?, ?>) container).containsKey(key)
            : (Integer) key >= 0 && (Integer) key < ((List<?>) container).size();
    }

    private static Object child(Object container, Object key) {
        return key instanceof String
            ? ((Map<?, ?>) container).get(key)
            : ((List<?>) container).get((Integer) key);
    }

    private static Object replace(Object container, Object key, Object value) {
        if (container instanceof List) {
            List<Object> next = new ArrayList<>((List<?>) container);
            int index = (Integer) key;
            if (index == next.size()) next.add(value); else next.set(index, value);
            return next;
        }
        Map<String, Object> next = new LinkedHashMap<>();
        ((Map<?, ?>) container).forEach((mapKey, mapValue) -> next.put(String.valueOf(mapKey), mapValue));
        next.put((String) key, value);
        return next;
    }

    private static Object updateArray(List<?> input, String op, Object item) {
        if ("removeValue".equals(op)) {
            boolean found = input.stream().anyMatch(value -> scalarEquals(value, item));
            if (!found) return input;
            List<Object> next = new ArrayList<>();
            for (Object value : input) if (!scalarEquals(value, item)) next.add(value);
            return next;
        }
        if ("appendUnique".equals(op) && input.stream().anyMatch(value -> scalarEquals(value, item))) return input;
        List<Object> next = new ArrayList<>(input.size() + 1);
        if ("prepend".equals(op)) next.add(item);
        next.addAll(input);
        if (!"prepend".equals(op)) next.add(item);
        return next;
    }

    private static Object updateFinal(Object container, Object part, VariableMutationOperation operation) {
        if (container == null) return null;
        String op = operation.getOp();
        if (("before".equals(op) || "after".equals(op)) && part instanceof String) {
            throw new IllegalArgumentException("Variable mutation before/after requires an array element path");
        }
        Object key = key(container, part);
        boolean found = exists(container, key);
        if ("remove".equals(op)) {
            if (!found) return container;
            if (container instanceof List) {
                List<Object> next = new ArrayList<>((List<?>) container);
                next.remove((int) (Integer) key);
                return next;
            }
            Map<String, Object> next = new LinkedHashMap<>();
            ((Map<?, ?>) container).forEach((mapKey, value) -> next.put(String.valueOf(mapKey), value));
            next.remove(key);
            return next;
        }
        Object item = operation.getValue();
        if ("set".equals(op)) {
            if (key instanceof Integer && (Integer) key > ((List<?>) container).size()) return container;
            if (found && child(container, key) == item) return container;
            return replace(container, key, item);
        }
        if ("before".equals(op) || "after".equals(op)) {
            if (!found) return container;
            List<Object> next = new ArrayList<>((List<?>) container);
            next.add((Integer) key + ("after".equals(op) ? 1 : 0), item);
            return next;
        }
        Object current = found ? child(container, key) : null;
        if (current == null && key instanceof Integer) return container;
        if (current == null && "removeValue".equals(op)) return container;
        if (current != null && !(current instanceof List)) {
            throw new IllegalArgumentException("Variable mutation expected an array");
        }
        Object next = updateArray(current == null ? List.of() : (List<?>) current, op, item);
        return next == current ? container : replace(container, key, next);
    }

    private static Object updateAtPath(Object value, VariableMutationOperation operation, int offset) {
        List<?> path = (List<?>) operation.getPath();
        if (offset == path.size()) {
            if ("set".equals(operation.getOp())) return operation.getValue();
            if ("remove".equals(operation.getOp())) throw new IllegalArgumentException("Cannot remove the root variable value");
            if ("before".equals(operation.getOp()) || "after".equals(operation.getOp())) {
                throw new IllegalArgumentException("Variable mutation before/after requires an array element path");
            }
            if (value == null) return null;
            if (!(value instanceof List)) throw new IllegalArgumentException("Variable mutation expected an array");
            return updateArray((List<?>) value, operation.getOp(), operation.getValue());
        }
        if (offset == path.size() - 1) return updateFinal(value, path.get(offset), operation);
        if (value == null) return null;
        Object key = key(value, path.get(offset));
        if (!exists(value, key)) return value;
        Object current = child(value, key);
        if (current == null) return value;
        Object next = updateAtPath(current, operation, offset + 1);
        return next == current ? value : replace(value, key, next);
    }

    private static boolean scalarEquals(Object left, Object right) {
        if (left == null || right == null) return left == right;
        if (left instanceof Number && right instanceof Number) {
            double a = ((Number) left).doubleValue();
            double b = ((Number) right).doubleValue();
            return !Double.isNaN(a) && !Double.isNaN(b) && a == b;
        }
        return left.getClass() == right.getClass() && left.equals(right);
    }
}
