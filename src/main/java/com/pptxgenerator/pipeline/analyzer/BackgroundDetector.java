package com.pptxgenerator.pipeline.analyzer;

import com.pptxgenerator.model.Point;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@ApplicationScoped
public class BackgroundDetector {

    /**
     * Detects background zones by analyzing overlap with the other zones.
     * A zone is considered a background if:
     * - Its z-index is <= 1 (it is in the background)
     * - More than 60% of its surface is covered by other zones
     */
    public List<Zone> detect(List<Zone> zones) {
        List<Zone> result = new ArrayList<>(zones);

        for (int i = 0; i < result.size(); i++) {
            Zone zone = result.get(i);

            // Only text zones can be reclassified as background
            if (!isTextCapableZone(zone.getZoneType())) {
                continue;
            }

            // Only zones with a low z-index can be backgrounds
            if (zone.getZIndex() > 1) {
                continue;
            }

            if (isBackground(zone, result)) {
                zone.setZoneType(ZoneType.BACKGROUND);
                log.debug("Zone {} reclassée comme background", zone.getZoneId());
            }
        }

        return result;
    }

    private boolean isTextCapableZone(ZoneType zoneType) {
        return Set.of(ZoneType.BODY, ZoneType.LINE, ZoneType.WORD).contains(zoneType);
    }

    private boolean isBackground(Zone zone, List<Zone> allZones) {
        long zoneArea = zone.getWidth() * zone.getHeight();
        if (zoneArea == 0) return false;

        long totalOverlapArea = 0;
        for (Zone other : allZones) {
            if (other.getZoneId().equals(zone.getZoneId())) continue;
            totalOverlapArea += calculateOverlapArea(zone, other);
        }

        double coveragePercentage = (totalOverlapArea / (double) zoneArea) * 100;
        return coveragePercentage > 60.0;
    }

    /**
     * Calculates the intersection area between two rectangles (in EMU²)
     */
    private long calculateOverlapArea(Zone zone1, Zone zone2) {
        Point p1 = zone1.getPolygon().get(0); // top-left
        Point p2 = zone1.getPolygon().get(2); // bottom-right
        Point p3 = zone2.getPolygon().get(0);
        Point p4 = zone2.getPolygon().get(2);

        long xOverlap = Math.max(0, Math.min(p2.getX(), p4.getX()) - Math.max(p1.getX(), p3.getX()));
        long yOverlap = Math.max(0, Math.min(p2.getY(), p4.getY()) - Math.max(p1.getY(), p3.getY()));

        return xOverlap * yOverlap;
    }
}
