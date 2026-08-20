package com.pptxgenerator.renderer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenderResult {
    private File outputFile;
    private Integer totalSlides;
    private Long generationTimeMs;
    private List<RenderWarning> warnings;
}
