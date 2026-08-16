package com.pptxgenerator.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pptxgenerator.model.enums.Tone;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentOptions {
    @JsonProperty("num_slides")
    private NumSlides numSlides;
    private String language;
    private Tone tone;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NumSlides {
        private Integer min;
        private Integer max;
    }
}
