package com.pptxgenerator.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.pptxgenerator.model.enums.Operation;
import com.pptxgenerator.model.enums.OutputFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateContentRequest {
    
    @NotNull
    private Operation operation;
    
    @NotNull
    @JsonProperty("model_id")
    private String modelId;
    
    @NotNull
    @JsonProperty("output_format")
    private OutputFormat outputFormat;
    
    private String templateId;
    
    private String instructions;
    
    private List<InputContent> inputs;
    
    @Builder.Default
    @JsonProperty("web_search")
    private Boolean webSearch = false;
    
    private ContentOptions options;
    
    // Champs spécifiques par opération
    @JsonProperty("plan_with_layouts")
    private JsonNode planWithLayouts;  // CREATION_FROM_PLAN
    private JsonNode elements;         // ADDITION
    private JsonNode edits;            // EDITION
    private JsonNode targets;          // DELETION
    @JsonProperty("new_order")
    private JsonNode newOrder;         // REORDERING
    @JsonProperty("new_template_id")
    private String newTemplateId;      // RESTYLING
}
