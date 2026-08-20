package com.pptxgenerator.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TemplateAnalysis {
    private SlideDimensions slideDimensions;
    private Theme theme;
    private List<LayoutAnalysis> layouts;
    private StructuralElements structuralElements;
}
