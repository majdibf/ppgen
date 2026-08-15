package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FontStyle {
    private String family;
    private String weight;
    @JsonProperty("size_pt")
    private int sizePt;
    @JsonProperty("color_ref")
    private String colorRef;

    public String getFamily() { return family; }
    public void setFamily(String family) { this.family = family; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public int getSizePt() { return sizePt; }
    public void setSizePt(int sizePt) { this.sizePt = sizePt; }

    public String getColorRef() { return colorRef; }
    public void setColorRef(String colorRef) { this.colorRef = colorRef; }
}
