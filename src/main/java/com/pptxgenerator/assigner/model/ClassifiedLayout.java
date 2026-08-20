package com.pptxgenerator.assigner.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ContentCapacity;
import com.pptxgenerator.model.enums.SemanticType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassifiedLayout {
    @JsonProperty("layout_id")
    private String layoutId;

    @JsonProperty("original_name")
    private String originalName;

    @JsonProperty("semantic_type")
    private SemanticType semanticType;

    private String description;

    @JsonProperty("content_capacity")
    private ContentCapacity contentCapacity;

    private List<Zone> zones;
}
