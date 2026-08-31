package com.pptxgenerator.pipeline.assigner.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pptxgenerator.pipeline.planner.model.SlideType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlidePlanWithLayout {
    @JsonProperty("slide_number")
    private Integer slideNumber;

    @JsonProperty("slide_type")
    private SlideType slideType;

    private String purpose;

    @JsonProperty("content_brief")
    private String contentBrief;

    @JsonProperty("detailed_context")
    private String detailedContext;

    private ClassifiedLayout layout;

    @JsonProperty("section_number")
    private Integer sectionNumber;

    @JsonProperty("section_title")
    private String sectionTitle;
}
