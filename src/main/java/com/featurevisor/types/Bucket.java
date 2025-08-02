package com.featurevisor.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

@JsonDeserialize(using = BucketDeserializer.class)
public class Bucket {
    // This class represents the BucketBy type which can be:
    // - PlainBucketBy: String (AttributeKey)
    // - AndBucketBy: List<String> (AttributeKey[])
    // - OrBucketBy: Object with "or" property

    @JsonProperty("or")
    private List<String> or;

    // For plain bucket by (single string)
    private String plainBucketBy;

    // For and bucket by (array of strings)
    private List<String> andBucketBy;

    // Constructors
    public Bucket() {}

    public Bucket(String plainBucketBy) {
        this.plainBucketBy = plainBucketBy;
    }

    public Bucket(List<String> andBucketBy, boolean isAnd) {
        if (isAnd) {
            this.andBucketBy = andBucketBy;
        } else {
            this.or = andBucketBy;
        }
    }

    // Getters and Setters
    public List<String> getOr() {
        return or;
    }

    public void setOr(List<String> or) {
        this.or = or;
    }

    public String getPlainBucketBy() {
        return plainBucketBy;
    }

    public void setPlainBucketBy(String plainBucketBy) {
        this.plainBucketBy = plainBucketBy;
    }

    public List<String> getAndBucketBy() {
        return andBucketBy;
    }

    public void setAndBucketBy(List<String> andBucketBy) {
        this.andBucketBy = andBucketBy;
    }

    // Helper methods to determine type
    public boolean isPlainBucketBy() {
        return plainBucketBy != null;
    }

    public boolean isAndBucketBy() {
        return andBucketBy != null;
    }

    public boolean isOrBucketBy() {
        return or != null;
    }

    // Get the actual value regardless of type
    public Object getValue() {
        if (isPlainBucketBy()) {
            return plainBucketBy;
        } else if (isAndBucketBy()) {
            return andBucketBy;
        } else if (isOrBucketBy()) {
            return or;
        }
        return null;
    }


}
