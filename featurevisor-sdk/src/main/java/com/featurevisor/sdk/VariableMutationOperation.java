package com.featurevisor.sdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/** A compiled variable mutation emitted by the Featurevisor datafile builder. */
public final class VariableMutationOperation {
    @JsonProperty("op")
    private String op;

    @JsonProperty("path")
    private Object path;

    private Object value;
    private boolean valueSet;

    public String getOp() { return op; }
    public void setOp(String op) { this.op = op; }
    public Object getPath() { return path; }
    public void setPath(Object path) { this.path = path; }
    public Object getValue() { return value; }
    public boolean hasValue() { return valueSet; }

    @JsonSetter(value = "value", nulls = Nulls.SET)
    public void setValue(Object value) {
        this.value = value;
        this.valueSet = true;
    }
}
