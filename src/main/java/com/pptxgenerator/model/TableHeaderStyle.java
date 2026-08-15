package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TableHeaderStyle {
    private String background;
    @JsonProperty("font_bold")
    private boolean fontBold;

    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }

    public boolean isFontBold() { return fontBold; }
    public void setFontBold(boolean fontBold) { this.fontBold = fontBold; }
}
