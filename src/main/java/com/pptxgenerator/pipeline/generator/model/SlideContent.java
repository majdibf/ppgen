package com.pptxgenerator.pipeline.generator.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Generated content of a single slide, modelled as a flat map of zone keys
 * ({@code zone_type_zone_id}) to their textual value. This matches the zone-based
 * contract consumed by the renderer and produced by the AI/Deterministic content generators.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlideContent {

    @JsonIgnore
    @Builder.Default
    private Map<String, String> content = new HashMap<>();

    /** Captures any unknown (zone) property into the flat content map when deserializing. */
    @JsonAnySetter
    public void addContent(String key, String value) {
        if (content == null) {
            content = new HashMap<>();
        }
        content.put(key, value);
    }

    /** Serializes the flat content map at the root level. */
    @JsonAnyGetter
    public Map<String, String> getContent() {
        return content;
    }
}
