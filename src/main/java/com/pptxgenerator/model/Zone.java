package com.pptxgenerator.model;

import com.pptxgenerator.model.enums.ZoneType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Zone {
    private Integer zoneId;
    private ZoneType zoneType;
    private Long width;
    private Long height;
    private List<Point> polygon;
    private Double surfacePercentage;
    private Integer zIndex;
    private String position;
    private String zoneDescription;
    private Long idx; // OOXML idx of the placeholder in the layout, used for render mapping

    @com.fasterxml.jackson.annotation.JsonProperty("max_characters")
    private Integer maxCharacters;
}
