package com.pptxgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateAnalysis {
    private SlideDimensions slideDimensions;
    private Theme theme;
    private List<LayoutAnalysis> layouts;
    private StructuralElements structuralElements;
}
