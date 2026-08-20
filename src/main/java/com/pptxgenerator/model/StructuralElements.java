package com.pptxgenerator.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StructuralElements {
    private Boolean hasHeaderBar;
    private Boolean hasFooter;
    private Boolean hasSlideNumbers;
    private Boolean hasLogo;
    private String logoPosition;
}
