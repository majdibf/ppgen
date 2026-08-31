package com.pptxgenerator.model;

import com.pptxgenerator.model.enums.ContentCapacity;
import com.pptxgenerator.model.enums.SemanticType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayoutAnalysis {
    private String layoutId;
    private String originalName;
    private SemanticType semanticType;
    private String description;
    private ContentCapacity contentCapacity;
    private List<Zone> zones;
}
