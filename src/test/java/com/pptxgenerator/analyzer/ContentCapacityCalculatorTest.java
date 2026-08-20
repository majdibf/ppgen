package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ContentCapacity;
import com.pptxgenerator.model.enums.ZoneType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentCapacityCalculatorTest {

    private final ContentCapacityCalculator calculator = new ContentCapacityCalculator();

    @Test
    void shouldReturnHigh_whenTotalBodySurfaceAbove40() {
        List<Zone> zones = List.of(
            Zone.builder().zoneType(ZoneType.TITLE).surfacePercentage(10.0).build(),
            Zone.builder().zoneType(ZoneType.BODY).surfacePercentage(45.0).build()
        );

        assertEquals(ContentCapacity.HIGH, calculator.calculate(zones));
    }

    @Test
    void shouldReturnMedium_whenTotalBodySurfaceBetween20And40() {
        List<Zone> zones = List.of(
            Zone.builder().zoneType(ZoneType.TITLE).surfacePercentage(10.0).build(),
            Zone.builder().zoneType(ZoneType.BODY).surfacePercentage(25.0).build()
        );

        assertEquals(ContentCapacity.MEDIUM, calculator.calculate(zones));
    }

    @Test
    void shouldReturnLow_whenTotalBodySurfaceBelow20() {
        List<Zone> zones = List.of(
            Zone.builder().zoneType(ZoneType.TITLE).surfacePercentage(15.0).build(),
            Zone.builder().zoneType(ZoneType.BODY).surfacePercentage(10.0).build()
        );

        assertEquals(ContentCapacity.LOW, calculator.calculate(zones));
    }
}
