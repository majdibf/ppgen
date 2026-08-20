package com.pptxgenerator.model;

import com.pptxgenerator.model.enums.ContentCapacity;
import com.pptxgenerator.model.enums.SemanticType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LayoutAnalysis {
    private String layoutId;
    private String originalName;
    private SemanticType semanticType;
    private String description;
    private ContentCapacity contentCapacity;
    private List<Zone> zones;
}
