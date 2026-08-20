package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class SemanticZoneNamer {

    /**
     * Attribue des noms sémantiques aux zones (left_column, right_column, box_1, etc.)
     */
    public List<Zone> nameZones(List<Zone> zones) {
        // 1. Zones simples (title, subtitle, etc.)
        for (Zone zone : zones) {
            if (isSimpleZone(zone.getZoneType())) {
                zone.setSemanticName(zone.getZoneType().getValue());
            }
        }

        // 2. Zones body → left_column, right_column, box_1, box_2, box_3
        List<Zone> bodyZones = zones.stream()
            .filter(z -> z.getZoneType() == ZoneType.BODY)
            .sorted(Comparator.comparing((Zone z) -> z.getPolygon().get(0).getX())
                    .thenComparing(z -> z.getPolygon().get(0).getY()))
            .collect(Collectors.toList());

        if (bodyZones.size() == 1) {
            bodyZones.get(0).setSemanticName("body");
        } else if (bodyZones.size() == 2) {
            bodyZones.get(0).setSemanticName("left_column");
            bodyZones.get(1).setSemanticName("right_column");
        } else if (bodyZones.size() >= 3) {
            for (int i = 0; i < Math.min(3, bodyZones.size()); i++) {
                bodyZones.get(i).setSemanticName("box_" + (i + 1));
            }
        }

        // 3. Zones picture/chart/table → media_placeholder
        for (Zone zone : zones) {
            if (zone.getZoneType() == ZoneType.PICTURE ||
                zone.getZoneType() == ZoneType.CHART ||
                zone.getZoneType() == ZoneType.TABLE) {
                zone.setSemanticName("media_placeholder");
            }
        }

        return zones;
    }

    private boolean isSimpleZone(ZoneType zoneType) {
        return Set.of(ZoneType.TITLE, ZoneType.CENTER_TITLE, ZoneType.SUBTITLE, ZoneType.HEADER,
                     ZoneType.FOOTER, ZoneType.SLIDE_NUMBER, ZoneType.DATE).contains(zoneType);
    }
}
