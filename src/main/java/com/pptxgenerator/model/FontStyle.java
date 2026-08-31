package com.pptxgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FontStyle {
    private String family;
    private String weight;
    private Integer sizePt;
}
