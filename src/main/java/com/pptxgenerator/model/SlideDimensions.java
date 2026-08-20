package com.pptxgenerator.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SlideDimensions {
    private Long width;
    private Long height;
    private String unit; // "EMU"
}
