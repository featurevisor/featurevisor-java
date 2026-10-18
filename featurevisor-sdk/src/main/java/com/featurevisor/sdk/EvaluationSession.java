package com.featurevisor.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Request local active chain and completed prerequisite getter values. */
final class EvaluationSession {
    static final class Cycle extends IllegalStateException {
        Cycle() { super("Circular dependency"); }
    }

    static final class RequirementFailure extends IllegalStateException {
        RequirementFailure(RuntimeException cause) { super(cause.getMessage(), cause); }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Object UNSUPPORTED = new Object();
    private static final int MAX_SNAPSHOT_DEPTH = 64;
    private static final int MAX_SNAPSHOT_NODES = 4096;

    /** Memoisation is optional; host graphs must not cause unbounded traversal. */
    private static final class Traversal {
        private final IdentityHashMap<Object, Boolean> ancestors = new IdentityHashMap<>();
        private int remaining = MAX_SNAPSHOT_NODES;

        boolean visit(int depth) { return depth <= MAX_SNAPSHOT_DEPTH && remaining-- > 0; }
        boolean enter(Object value) { return ancestors.put(value, Boolean.TRUE) == null; }
        void leave(Object value) { ancestors.remove(value); }
    }

    private static final class Frame {
        private final DependencyOf requested;
        private DependencyOf current;
        private Frame(DependencyOf identity) { requested = identity; current = identity; }
    }

    private static final class MemoKey {
        private final InstanceEvaluationDataProvider data;
        private final String snapshot;
        private final Map<String, Object> stickyFeatures;
        private final Map<String, Object> stickyVariables;
        MemoKey(InstanceEvaluationDataProvider data, String snapshot,
                Map<String, Object> stickyFeatures, Map<String, Object> stickyVariables) {
            this.data = data;
            this.snapshot = snapshot;
            this.stickyFeatures = stickyFeatures;
            this.stickyVariables = stickyVariables;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof MemoKey)) return false;
            MemoKey value = (MemoKey) other;
            return data == value.data && stickyFeatures == value.stickyFeatures
                && stickyVariables == value.stickyVariables && snapshot.equals(value.snapshot);
        }
        @Override public int hashCode() {
            int hash = 31 * System.identityHashCode(data) + snapshot.hashCode();
            return 31 * (31 * hash + System.identityHashCode(stickyFeatures))
                + System.identityHashCode(stickyVariables);
        }
    }

    private DependencyOf rootRequested;
    private DependencyOf rootCurrent;
    private List<Frame> frames;
    private Map<MemoKey, Object> memo;
    private java.util.Set<String> segments;
    private EvaluateOptions rootOptions;
    private List<EvaluateOptions> callbackOptions;
    private boolean cycleDetected;
    private RequirementFailure requirementFailure;

    boolean beginRoot(DependencyOf identity) {
        if (rootRequested != null) return false;
        rootRequested = rootCurrent = identity;
        return true;
    }

    void endRoot() {
        if (rootOptions != null) rootOptions.clearRequestSource();
        if (callbackOptions != null) {
            for (EvaluateOptions options : callbackOptions) options.clearRequestSource();
        }
        rootOptions = null;
        callbackOptions = null;
        rootRequested = rootCurrent = null;
        frames = null;
        memo = null;
        segments = null;
        cycleDetected = false;
        requirementFailure = null;
    }

    void retainCallbackOptions(EvaluateOptions options) {
        if (rootOptions == null) rootOptions = options;
        else if (rootOptions != options) {
            if (callbackOptions == null) callbackOptions = new ArrayList<>();
            callbackOptions.add(options);
        }
    }

    DependencyOf current() { return frames == null ? rootCurrent : frames.get(frames.size() - 1).current; }

    void updateCurrent(DependencyOf identity) {
        if (frames == null) rootCurrent = identity;
        else frames.get(frames.size() - 1).current = identity;
    }

    private void prepareFrames() {
        if (frames != null) return;
        frames = new ArrayList<>();
        if (rootRequested != null) {
            Frame root = new Frame(rootRequested);
            root.current = rootCurrent;
            frames.add(root);
        }
    }

    boolean hasDependencyStorage() { return frames != null || memo != null || segments != null; }

    void recordRequirementFailure(RequirementFailure error) { requirementFailure = error; }

    void throwIfFatal() {
        if (cycleDetected) throw new Cycle();
        if (requirementFailure != null) throw requirementFailure;
    }

    <T> T segment(String key, Supplier<T> evaluate) {
        prepareFrames();
        if (segments == null) segments = new java.util.HashSet<>();
        if (segments.contains(key) || segments.size() + frames.size() - 1 >= 256) {
            cycleDetected = true;
            throw new Cycle();
        }
        segments.add(key);
        try { return evaluate.get(); }
        finally { segments.remove(key); }
    }

    Object read(EvaluateOptions requested, Supplier<Object> evaluate) {
        prepareFrames();
        if (memo == null) memo = new HashMap<>();
        if (frames.size() + (segments == null ? 0 : segments.size()) > 256) { cycleDetected = true; throw new Cycle(); }
        boolean borrowed = "composition".equals(requested.getDependencyPurpose());
        DependencyOf identity = DependencyOf.from(requested);
        for (Frame frame : frames) {
            if (frame.requested.equals(identity) || frame.current.equals(identity)) {
                cycleDetected = true;
                throw new Cycle();
            }
        }
        DependencyOf parent = current();
        MemoKey key = key(requested, parent);
        if (key != null && memo.containsKey(key)) {
            Object cached = borrowed ? memo.get(key) : copy(memo.get(key));
            if (cached != UNSUPPORTED) return cached;
            memo.remove(key);
        }
        frames.add(new Frame(identity));
        try {
            Object result = evaluate.get();
            throwIfFatal();
            if (key != null) {
                Object safe = borrowed ? result : copy(result);
                if (safe != UNSUPPORTED && key.equals(key(requested, parent))) memo.put(key, safe);
            }
            return result;
        } finally {
            frames.remove(frames.size() - 1);
        }
    }

    private static MemoKey key(EvaluateOptions options, DependencyOf parent) {
        try {
            StringBuilder out = new StringBuilder();
            Traversal traversal = new Traversal();
            DependencyOf identity = DependencyOf.from(options);
            Object[] parts = {
                identity.getType(), identity.getFeatureKey(), identity.getVariableKey(), options.getDependencyPurpose(),
                parent == null ? null : parent.getType(),
                parent == null ? null : parent.getFeatureKey(),
                parent == null ? null : parent.getVariableKey(),
                options.getContext(),
                options.getSourceGeneration()
            };
            for (Object part : parts) if (!append(out, part, traversal, 0)) return null;
            List<String> moduleIds = new ArrayList<>();
            if (options.getModulesManager() != null) {
                for (FeaturevisorModule module : options.getModulesManager().phaseModules()) moduleIds.add(module.getId());
            }
            if (!append(out, moduleIds, traversal, 0)
                || !append(out, options.getModulesManager() == null ? 0 : options.getModulesManager().getRevision(), traversal, 0)) return null;
            // Sticky maps and nested values are read only inputs. Identity plus
            // the owner's generation detects replacements without walking them.
            return new MemoKey(options.getInstanceEvaluationDataProvider(), out.toString(),
                options.getStickyFeatures(), options.getStickyVariables());
        } catch (RuntimeException error) {
            // A host collection may reject traversal or change while being inspected.
            return null;
        }
    }

    private static boolean append(StringBuilder out, Object value, Traversal traversal, int depth) {
        StringBuilder part = new StringBuilder();
        if (!encode(part, value, traversal, depth)) return false;
        out.append(part.length()).append(':').append(part);
        return true;
    }

    private static boolean immutableNumber(Object value) {
        Class<?> type = value.getClass();
        if (type == Double.class) return Double.isFinite((Double) value);
        if (type == Float.class) return Float.isFinite((Float) value);
        return type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
            || type == BigInteger.class || type == BigDecimal.class;
    }

    private static boolean encode(StringBuilder out, Object value, Traversal traversal, int depth) {
        if (!traversal.visit(depth)) return false;
        if (value == null) { out.append('n'); return true; }
        if (value instanceof String) {
            try { out.append('s').append(JSON.writeValueAsString(value)); return true; }
            catch (JsonProcessingException error) { return false; }
        }
        if (value instanceof Boolean) { out.append((Boolean) value ? 't' : 'f'); return true; }
        if (value instanceof Number) {
            if (!immutableNumber(value)) return false;
            // Preserve class, floating signed zero, and BigDecimal scale for hooks.
            out.append('d').append(value.getClass().getName()).append(':').append(value.toString());
            return true;
        }
        if (value.getClass() == Date.class) {
            out.append('D').append(((Date) value).getTime());
            return true;
        }
        if (!(value instanceof List) && !(value instanceof Map) && !value.getClass().isArray()) return false;
        if (!traversal.enter(value)) return false;
        try {
            out.append(value.getClass().getName()).append(':');
            if (value instanceof List) {
                List<?> values = (List<?>) value;
                if (values.size() > traversal.remaining) return false;
                out.append('[');
                for (Object item : values) if (!append(out, item, traversal, depth + 1)) return false;
                out.append(']');
                return true;
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                if (length > traversal.remaining) return false;
                out.append('a');
                for (int index = 0; index < length; index++) {
                    if (!append(out, Array.get(value, index), traversal, depth + 1)) return false;
                }
                return true;
            }
            Map<?, ?> values = (Map<?, ?>) value;
            if (values.size() > traversal.remaining / 2) return false;
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String)) return false;
                sorted.put((String) entry.getKey(), entry.getValue());
            }
            out.append('{');
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!append(out, entry.getKey(), traversal, depth + 1)
                    || !append(out, entry.getValue(), traversal, depth + 1)) return false;
            }
            out.append('}');
            return true;
        } finally {
            traversal.leave(value);
        }
    }

    private static Object copy(Object value) {
        try {
            return copy(value, new Traversal(), 0);
        } catch (RuntimeException error) {
            return UNSUPPORTED;
        }
    }

    private static Object copy(Object value, Traversal traversal, int depth) {
        if (!traversal.visit(depth)) return UNSUPPORTED;
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Number) return immutableNumber(value) ? value : UNSUPPORTED;
        if (value.getClass() == Date.class) return new Date(((Date) value).getTime());
        if (!(value instanceof List) && !(value instanceof Map)) return UNSUPPORTED;
        if (!traversal.enter(value)) return UNSUPPORTED;
        try {
            if (value instanceof List) {
                List<?> values = (List<?>) value;
                if (values.size() > traversal.remaining) return UNSUPPORTED;
                List<Object> result = new ArrayList<>();
                for (Object item : values) {
                    Object copied = copy(item, traversal, depth + 1);
                    if (copied == UNSUPPORTED) return UNSUPPORTED;
                    result.add(copied);
                }
                return result;
            }
            Map<?, ?> values = (Map<?, ?>) value;
            if (values.size() > traversal.remaining / 2) return UNSUPPORTED;
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String)) return UNSUPPORTED;
                if (!traversal.visit(depth + 1)) return UNSUPPORTED;
                Object copied = copy(entry.getValue(), traversal, depth + 1);
                if (copied == UNSUPPORTED) return UNSUPPORTED;
                result.put((String) entry.getKey(), copied);
            }
            return result;
        } finally {
            traversal.leave(value);
        }
    }
}
