package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.Point;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackgroundDetectorTest {

    private BackgroundDetector detector;

    @BeforeEach
    void setUp() {
        detector = new BackgroundDetector();
    }

    @Test
    void shouldDetectBackgroundZone_when60PercentCovered() {
        // Zone de fond : 1000x1000 EMU, z-index 0
        Zone background = Zone.builder()
            .zoneId(0).zoneType(ZoneType.BODY).zIndex(0)
            .width(1000L).height(1000L)
            .polygon(List.of(
                new Point(0L, 0L), new Point(1000L, 0L),
                new Point(1000L, 1000L), new Point(0L, 1000L)
            ))
            .build();

        // Zone qui recouvre : 800x1000 EMU (80% de la zone de fond)
        Zone cover = Zone.builder()
            .zoneId(1).zoneType(ZoneType.TITLE).zIndex(1)
            .width(800L).height(1000L)
            .polygon(List.of(
                new Point(0L, 0L), new Point(800L, 0L),
                new Point(800L, 1000L), new Point(0L, 1000L)
            ))
            .build();

        List<Zone> result = detector.detect(List.of(background, cover));

        assertEquals(ZoneType.BACKGROUND, result.get(0).getZoneType());
        assertEquals(ZoneType.TITLE, result.get(1).getZoneType());
    }

    @Test
    void shouldNotDetectBackground_whenLessThan60PercentCovered() {
        Zone background = Zone.builder()
            .zoneId(0).zoneType(ZoneType.BODY).zIndex(0)
            .width(1000L).height(1000L)
            .polygon(List.of(
                new Point(0L, 0L), new Point(1000L, 0L),
                new Point(1000L, 1000L), new Point(0L, 1000L)
            ))
            .build();

        Zone cover = Zone.builder()
            .zoneId(1).zoneType(ZoneType.TITLE).zIndex(1)
            .width(500L).height(1000L) // Seulement 50%
            .polygon(List.of(
                new Point(0L, 0L), new Point(500L, 0L),
                new Point(500L, 1000L), new Point(0L, 1000L)
            ))
            .build();

        List<Zone> result = detector.detect(List.of(background, cover));

        assertEquals(ZoneType.BODY, result.get(0).getZoneType());
    }

    @Test
    void shouldNotReclassifyNonTextZones() {
        Zone picture = Zone.builder()
            .zoneId(0).zoneType(ZoneType.PICTURE).zIndex(0)
            .width(1000L).height(1000L)
            .polygon(List.of(
                new Point(0L, 0L), new Point(1000L, 0L),
                new Point(1000L, 1000L), new Point(0L, 1000L)
            ))
            .build();

        List<Zone> result = detector.detect(List.of(picture));

        assertEquals(ZoneType.PICTURE, result.get(0).getZoneType());
    }
}
