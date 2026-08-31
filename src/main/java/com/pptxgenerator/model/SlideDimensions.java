package com.pptxgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideDimensions {
    private Long width;
    private Long height;
    private String unit; // "EMU"
}
