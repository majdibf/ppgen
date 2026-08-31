package com.pptxgenerator.pipeline.generator;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.ZoneKeys;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a JSON-schema describing the expected structured output for a content slide: one string
 * property per zone key ({@code zone_type_zone_id}). Used as the AI request output schema.
 */
public final class OutputSchemaProvider {

    private OutputSchemaProvider() {
    }

    public static Map<String, Object> createSlideContentSchema(List<Zone> layoutZones) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (layoutZones != null) {
            for (Zone zone : layoutZones) {
                String zoneKey = ZoneKeys.key(zone);
                properties.put(zoneKey, Map.of("type", "string"));
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("additionalProperties", false);
        return schema;
    }
}
