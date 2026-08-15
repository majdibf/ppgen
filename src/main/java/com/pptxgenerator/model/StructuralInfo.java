package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StructuralInfo {
    @JsonProperty("has_header_bar")
    private boolean hasHeaderBar;
    @JsonProperty("has_footer")
    private boolean hasFooter;
    @JsonProperty("has_slide_numbers")
    private boolean hasSlideNumbers;
    @JsonProperty("has_logo")
    private boolean hasLogo;
    @JsonProperty("logo_position")
    private String logoPosition;

    public boolean isHasHeaderBar() { return hasHeaderBar; }
    public void setHasHeaderBar(boolean hasHeaderBar) { this.hasHeaderBar = hasHeaderBar; }

    public boolean isHasFooter() { return hasFooter; }
    public void setHasFooter(boolean hasFooter) { this.hasFooter = hasFooter; }

    public boolean isHasSlideNumbers() { return hasSlideNumbers; }
    public void setHasSlideNumbers(boolean hasSlideNumbers) { this.hasSlideNumbers = hasSlideNumbers; }

    public boolean isHasLogo() { return hasLogo; }
    public void setHasLogo(boolean hasLogo) { this.hasLogo = hasLogo; }

    public String getLogoPosition() { return logoPosition; }
    public void setLogoPosition(String logoPosition) { this.logoPosition = logoPosition; }
}
