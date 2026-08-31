package com.pptxgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuralElements {
    private Boolean hasHeaderBar;
    private Boolean hasFooter;
    private Boolean hasSlideNumbers;
}
