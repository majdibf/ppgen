package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ImageInfo {
    private String embed;
    private String name;
    @JsonProperty("is_placeholder")
    private boolean isPlaceholder;

    public String getEmbed() { return embed; }
    public void setEmbed(String embed) { this.embed = embed; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isPlaceholder() { return isPlaceholder; }
    public void setPlaceholder(boolean isPlaceholder) { this.isPlaceholder = isPlaceholder; }
}
