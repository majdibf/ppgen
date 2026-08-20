package com.pptxgenerator.generator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlideContent {
    private String title;
    private String subtitle;
    private BodyContent body;

    @JsonProperty("left_column")
    private ColumnContent leftColumn;

    @JsonProperty("right_column")
    private ColumnContent rightColumn;

    @JsonProperty("box_1")
    private BoxContent box1;

    @JsonProperty("box_2")
    private BoxContent box2;

    @JsonProperty("box_3")
    private BoxContent box3;

    @JsonProperty("media_description")
    private String mediaDescription;
}
