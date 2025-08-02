package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

public class DatafileContent {
    @JsonProperty("schemaVersion")
    private String schemaVersion;

    @JsonProperty("revision")
    private String revision;

    @JsonProperty("segments")
    private Map<String, Segment> segments;

    @JsonProperty("features")
    @JsonDeserialize(using = com.featurevisor.types.FeaturesDeserializer.class)
    private Map<String, Feature> features;

    // Constructors
    public DatafileContent() {}

    public DatafileContent(String schemaVersion, String revision) {
        this.schemaVersion = schemaVersion;
        this.revision = revision;
    }

    // Getters and Setters
    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public Map<String, Segment> getSegments() {
        return segments;
    }

    public void setSegments(Map<String, Segment> segments) {
        this.segments = segments;
    }

    public Map<String, Feature> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, Feature> features) {
        if (features == null) {
            this.features = new java.util.HashMap<>();
        } else {
            this.features = features;
        }
    }

    /**
     * Static method to parse JSON string into DatafileContent object
     *
     * @param jsonString The JSON string representing the datafile content
     * @return DatafileContent object parsed from the JSON
     * @throws Exception if parsing fails
     */
    public static DatafileContent fromJson(String jsonString) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(jsonString, DatafileContent.class);
    }

    /**
     * Convert DatafileContent object back to JSON string
     *
     * @return JSON string representation of the DatafileContent
     * @throws Exception if serialization fails
     */
    public String toJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(this);
    }

    /**
     * Get a specific feature by key
     *
     * @param featureKey The key of the feature to retrieve
     * @return Feature object if found, null otherwise
     */
    public Feature getFeature(String featureKey) {
        return features != null ? features.get(featureKey) : null;
    }

    /**
     * Get a specific segment by key
     *
     * @param segmentKey The key of the segment to retrieve
     * @return Segment object if found, null otherwise
     */
    public Segment getSegment(String segmentKey) {
        return segments != null ? segments.get(segmentKey) : null;
    }

    /**
     * Check if a feature exists
     *
     * @param featureKey The key of the feature to check
     * @return true if feature exists, false otherwise
     */
    public boolean hasFeature(String featureKey) {
        return features != null && features.containsKey(featureKey);
    }

    /**
     * Check if a segment exists
     *
     * @param segmentKey The key of the segment to check
     * @return true if segment exists, false otherwise
     */
    public boolean hasSegment(String segmentKey) {
        return segments != null && segments.containsKey(segmentKey);
    }

    /**
     * Get the number of features
     *
     * @return number of features
     */
    public int getFeatureCount() {
        return features != null ? features.size() : 0;
    }

    /**
     * Get the number of segments
     *
     * @return number of segments
     */
    public int getSegmentCount() {
        return segments != null ? segments.size() : 0;
    }


}
