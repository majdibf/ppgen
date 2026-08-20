package com.pptxgenerator.planner.model;

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
public class PresentationPlan {

    private String title;

    @JsonProperty("narrative_arc")
    private String narrativeArc;

    @JsonProperty("total_slides")
    private Integer totalSlides;

    private List<SlidePlan> slides;
}
