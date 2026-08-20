package com.pptxgenerator.model;

import com.pptxgenerator.model.enums.ZoneType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
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
    private String semanticName; // "title", "body", "left_column", "right_column", "box_1", etc.
}
