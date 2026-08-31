package com.pptxgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Theme {
    private Map<String, String> colors;
    private Map<String, FontStyle> fonts;
}
