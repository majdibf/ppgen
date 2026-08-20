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
public class PlanWithLayouts {

    @JsonProperty("plan_with_layouts")
    private PlanWithLayoutsData planWithLayouts;

    private List<LayoutAssignmentWarning> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanWithLayoutsData {
        private String title;

        @JsonProperty("narrative_arc")
        private String narrativeArc;

        @JsonProperty("total_slides")
        private Integer totalSlides;

        private List<SlidePlanWithLayout> slides;
    }
}
