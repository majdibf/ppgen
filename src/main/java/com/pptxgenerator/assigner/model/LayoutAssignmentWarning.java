package com.pptxgenerator.assigner.model;

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
public class LayoutAssignmentWarning {
    private String code;           // LAYOUT_FALLBACK, LAYOUT_LIMITED, etc.
    private String message;

    @JsonProperty("affected_slides")
    private List<Integer> affectedSlides;
}
