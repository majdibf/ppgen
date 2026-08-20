package com.pptxgenerator.planner.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SlideType {

    TITLE("title"),
    OUTLINE("outline"),
    SECTION_TRANSITION("section_transition"),
    CONTENT("content");

    private final String value;

    SlideType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static SlideType fromValue(String value) {
        for (SlideType type : values()) {
            if (type.value.equals(value)) return type;
        }
        throw new IllegalArgumentException("SlideType inconnu: " + value);
    }
}
