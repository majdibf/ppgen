package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ContentCapacity;
import com.pptxgenerator.model.enums.ZoneType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
public class ContentCapacityCalculator {

    /**
     * Calcule la capacité de contenu d'un layout (HIGH/MEDIUM/LOW)
     */
    public ContentCapacity calculate(List<Zone> zones) {
        double totalBodySurface = zones.stream()
            .filter(z -> z.getZoneType() == ZoneType.BODY)
            .mapToDouble(Zone::getSurfacePercentage)
            .sum();

        if (totalBodySurface >= 40) {
            return ContentCapacity.HIGH;
        } else if (totalBodySurface >= 20) {
            return ContentCapacity.MEDIUM;
        } else {
            return ContentCapacity.LOW;
        }
    }
}
