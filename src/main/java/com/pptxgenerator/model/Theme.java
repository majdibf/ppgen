package com.pptxgenerator.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class Theme {
    private Map<String, String> colors;
    private Map<String, FontStyle> fonts;
}
