package com.pptxgenerator.renderer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenderWarning {
    private String code;
    private String message;

    @JsonProperty("affected_slides")
    private List<Integer> affectedSlides;
}
