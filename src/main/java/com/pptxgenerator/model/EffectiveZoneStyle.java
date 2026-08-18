package com.pptxgenerator.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EffectiveZoneStyle {
    String fontFamily;
    String fontWeight;
    int fontSizePt;
    String color;
    String alignment;
    Margins margins;
}
