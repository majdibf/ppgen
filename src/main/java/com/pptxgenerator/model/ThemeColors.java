package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ThemeColors {
    private String primary;
    private String secondary;
    private String background;
    private String accent1;
    private String accent2;
    @JsonProperty("text_primary")
    private String textPrimary;
    @JsonProperty("text_secondary")
    private String textSecondary;

    public String getPrimary() { return primary; }
    public void setPrimary(String primary) { this.primary = primary; }

    public String getSecondary() { return secondary; }
    public void setSecondary(String secondary) { this.secondary = secondary; }

    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }

    public String getAccent1() { return accent1; }
    public void setAccent1(String accent1) { this.accent1 = accent1; }

    public String getAccent2() { return accent2; }
    public void setAccent2(String accent2) { this.accent2 = accent2; }

    public String getTextPrimary() { return textPrimary; }
    public void setTextPrimary(String textPrimary) { this.textPrimary = textPrimary; }

    public String getTextSecondary() { return textSecondary; }
    public void setTextSecondary(String textSecondary) { this.textSecondary = textSecondary; }
}
