package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SlideDimensions {
    private long width;
    @JsonProperty("width_inches")
    private double widthInches;
    private long height;
    @JsonProperty("height_inches")
    private double heightInches;
    private String unit = "EMU";

    public long getWidth() { return width; }
    public void setWidth(long width) { this.width = width; }

    public double getWidthInches() { return widthInches; }
    public void setWidthInches(double widthInches) { this.widthInches = widthInches; }

    public long getHeight() { return height; }
    public void setHeight(long height) { this.height = height; }

    public double getHeightInches() { return heightInches; }
    public void setHeightInches(double heightInches) { this.heightInches = heightInches; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
