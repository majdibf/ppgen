package com.pptxgenerator.pipeline.assigner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of the layout assignment step: every planned slide enriched with its chosen layout,
 * plus the warnings collected while assigning.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanWithLayouts {

    private String title;

    private String narrativeArc;

    private Integer totalSlides;

    private List<SlidePlanWithLayout> slides;

    private List<LayoutAssignmentWarning> warnings;
}
