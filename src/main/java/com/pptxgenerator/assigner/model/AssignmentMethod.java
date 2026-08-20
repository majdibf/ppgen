package com.pptxgenerator.assigner.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AssignmentMethod {
    DETERMINISTIC("deterministic"),
    AI_ASSISTED("ai_assisted"),
    FALLBACK("fallback");

    private final String value;

    AssignmentMethod(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }
}
