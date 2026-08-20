package com.pptxgenerator.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ZoneType {
    TITLE("title"),
    CENTER_TITLE("center_title"),
    SUBTITLE("subtitle"),
    BODY("body"),
    PICTURE("picture"),
    CHART("chart"),
    TABLE("table"),
    HEADER("header"),
    FOOTER("footer"),
    SLIDE_NUMBER("slide_number"),
    DATE("date"),
    LINE("line"),
    WORD("word"),
    BACKGROUND("background"),
    UNKNOWN("unknown");

    private final String value;

    ZoneType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ZoneType fromValue(String value) {
        for (ZoneType type : values()) {
            if (type.value.equalsIgnoreCase(value)) return type;
        }
        return UNKNOWN;
    }
}
