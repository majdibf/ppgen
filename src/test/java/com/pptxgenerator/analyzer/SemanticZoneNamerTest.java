package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.Point;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticZoneNamerTest {

    private final SemanticZoneNamer namer = new SemanticZoneNamer();

    @Test
    void shouldNameSingleBodyAsBody() {
        List<Zone> zones = List.of(
            Zone.builder().zoneId(0).zoneType(ZoneType.TITLE).build(),
            Zone.builder().zoneId(1).zoneType(ZoneType.BODY)
                .polygon(List.of(new Point(100L, 200L), new Point(900L, 200L),
                                 new Point(900L, 600L), new Point(100L, 600L)))
                .build()
        );

        List<Zone> result = namer.nameZones(zones);

        assertEquals("title", result.get(0).getSemanticName());
        assertEquals("body", result.get(1).getSemanticName());
    }

    @Test
    void shouldNameTwoBodiesAsLeftAndRightColumn() {
        List<Zone> zones = List.of(
            Zone.builder().zoneId(0).zoneType(ZoneType.BODY)
                .polygon(List.of(new Point(100L, 200L), new Point(450L, 200L),
                                 new Point(450L, 600L), new Point(100L, 600L)))
                .build(),
            Zone.builder().zoneId(1).zoneType(ZoneType.BODY)
                .polygon(List.of(new Point(500L, 200L), new Point(900L, 200L),
                                 new Point(900L, 600L), new Point(500L, 600L)))
                .build()
        );

        List<Zone> result = namer.nameZones(zones);

        assertEquals("left_column", result.get(0).getSemanticName());
        assertEquals("right_column", result.get(1).getSemanticName());
    }

    @Test
    void shouldNameThreeBodiesAsBoxes() {
        List<Zone> zones = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            zones.add(Zone.builder().zoneId(i).zoneType(ZoneType.BODY)
                .polygon(List.of(new Point((long)(100 + i * 300), 200L),
                                 new Point((long)(350 + i * 300), 200L),
                                 new Point((long)(350 + i * 300), 600L),
                                 new Point((long)(100 + i * 300), 600L)))
                .build());
        }

        List<Zone> result = namer.nameZones(zones);

        assertEquals("box_1", result.get(0).getSemanticName());
        assertEquals("box_2", result.get(1).getSemanticName());
        assertEquals("box_3", result.get(2).getSemanticName());
    }
}
