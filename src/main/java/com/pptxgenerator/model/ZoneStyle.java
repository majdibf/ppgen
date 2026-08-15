package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ZoneStyle {
    @JsonProperty("font_family")
    private String fontFamily;
    @JsonProperty("font_weight")
    private String fontWeight;
    @JsonProperty("font_size_pt")
    private int fontSizePt;
    private String color;
    private String alignment;

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public String getFontWeight() { return fontWeight; }
    public void setFontWeight(String fontWeight) { this.fontWeight = fontWeight; }

    public int getFontSizePt() { return fontSizePt; }
    public void setFontSizePt(int fontSizePt) { this.fontSizePt = fontSizePt; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getAlignment() { return alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }
}
