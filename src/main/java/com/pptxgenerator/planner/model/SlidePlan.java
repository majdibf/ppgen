package com.pptxgenerator.planner.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlidePlan {

    @JsonProperty("slide_number")
    private Integer slideNumber;

    @JsonProperty("slide_type")
    private SlideType slideType;

    private String purpose;

    @JsonProperty("content_brief")
    private String contentBrief;

    @JsonProperty("detailed_context")
    private String detailedContext;

    @JsonProperty("section_number")
    private Integer sectionNumber;

    @JsonProperty("section_title")
    private String sectionTitle;
}
