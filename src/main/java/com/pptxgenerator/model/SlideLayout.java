package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SlideLayout {
    @JsonProperty("layout_id")
    private String layoutId;
    @JsonProperty("original_name")
    private String originalName;
    @JsonProperty("model_slide")
    private String modelSlide;
    @JsonProperty("semantic_type")
    private String semanticType;
    private String description;
    private java.util.List<Zone> zones;
    @JsonProperty("structural_info")
    private StructuralInfo structuralInfo;

    public String getLayoutId() { return layoutId; }
    public void setLayoutId(String layoutId) { this.layoutId = layoutId; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getModelSlide() { return modelSlide; }
    public void setModelSlide(String modelSlide) { this.modelSlide = modelSlide; }

    public String getSemanticType() { return semanticType; }
    public void setSemanticType(String semanticType) { this.semanticType = semanticType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public java.util.List<Zone> getZones() { return zones; }
    public void setZones(java.util.List<Zone> zones) { this.zones = zones; }

    public StructuralInfo getStructuralInfo() { return structuralInfo; }
    public void setStructuralInfo(StructuralInfo structuralInfo) { this.structuralInfo = structuralInfo; }
}
