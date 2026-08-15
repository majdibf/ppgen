package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PlaceholderInfo {
    private String type;
    private int idx;
    private String name;
    @JsonProperty("has_text")
    private boolean hasText;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getIdx() { return idx; }
    public void setIdx(int idx) { this.idx = idx; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isHasText() { return hasText; }
    public void setHasText(boolean hasText) { this.hasText = hasText; }
}
