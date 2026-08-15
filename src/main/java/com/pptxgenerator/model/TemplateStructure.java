package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TemplateStructure {
    private Theme theme;
    @JsonProperty("slide_dimensions")
    private SlideDimensions slideDimensions;
    private java.util.List<SlideLayout> layouts;
    @JsonProperty("structural_elements")
    private StructuralElements structuralElements;
    private Metadata metadata;

    public Theme getTheme() { return theme; }
    public void setTheme(Theme theme) { this.theme = theme; }

    public SlideDimensions getSlideDimensions() { return slideDimensions; }
    public void setSlideDimensions(SlideDimensions slideDimensions) { this.slideDimensions = slideDimensions; }

    public java.util.List<SlideLayout> getLayouts() { return layouts; }
    public void setLayouts(java.util.List<SlideLayout> layouts) { this.layouts = layouts; }

    public StructuralElements getStructuralElements() { return structuralElements; }
    public void setStructuralElements(StructuralElements structuralElements) { this.structuralElements = structuralElements; }

    public Metadata getMetadata() { return metadata; }
    public void setMetadata(Metadata metadata) { this.metadata = metadata; }
}
