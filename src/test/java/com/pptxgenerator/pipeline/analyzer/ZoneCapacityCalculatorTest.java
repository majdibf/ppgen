package com.pptxgenerator.pipeline.analyzer;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ZoneCapacityCalculator}.
 *
 * <p>Verifies the physical capacity math (EMU → pixels, minus padding, safety margin)
 * and the null contract for unsupported zone types.
 */
class ZoneCapacityCalculatorTest {

    private final ZoneCapacityCalculator calculator = new ZoneCapacityCalculator();

    @Test
    void nullZone_returnsNull() {
        assertThat(calculator.calculateMaxCharacters(null, 14)).isNull();
    }

    @Test
    void unsupportedType_returnsNull() {
        Zone zone = Zone.builder().zoneType(ZoneType.CENTER_TITLE)
            .width(4_000_000L).height(1_000_000L).build();
        assertThat(calculator.calculateMaxCharacters(zone, 14)).isNull();
    }

    @Test
    void zeroSize_returnsMinimumOne() {
        Zone zone = Zone.builder().zoneType(ZoneType.TITLE).width(0L).height(0L).build();
        assertThat(calculator.calculateMaxCharacters(zone, 14)).isEqualTo(1);
    }

    @Test
    void title_zone_matchesClientMath() {
        // 9144000 EMU wide × 914400 EMU tall, 14pt body font
        // usableW = 9144000/914400*96 - 40 = 960 - 40 = 920 px
        // usableH = 914400/914400*96 - 40 = 96 - 40 = 56 px
        // fontSizePx = 14 * 96/72 = 18.667 px
        // charWidth = 18.667 * 0.6 = 11.2 px
        // charsPerLine = 920 / 11.2 = 82
        // numLines = 1 (title is not BODY)
        // maxChars = 82 * 1 * 0.85 = 69 (truncated to int)
        Zone zone = Zone.builder().zoneType(ZoneType.TITLE)
            .width(9_144_000L).height(914_400L).build();
        assertThat(calculator.calculateMaxCharacters(zone, 14)).isEqualTo(69);
    }

    @Test
    void body_zone_scalesWithLines() {
        // 9144000 × 9144000 EMU, 14pt
        // usableW = 960 - 40 = 920 px, usableH = 960 - 40 = 920 px
        // charWidth = 11.2 px → charsPerLine = 82
        // fontSizePx = 18.667, lineH = 18.667 * 1.2 = 22.4 px
        // numLines = 920 / 22.4 = 41
        // maxChars = 82 * 41 * 0.85 = 2857 (truncated to int)
        Zone zone = Zone.builder().zoneType(ZoneType.BODY)
            .width(9_144_000L).height(9_144_000L).build();
        assertThat(calculator.calculateMaxCharacters(zone, 14)).isEqualTo(2857);
    }

    @Test
    void largerFontReducesCapacity() {
        Zone zone = Zone.builder().zoneType(ZoneType.BODY)
            .width(9_144_000L).height(9_144_000L).build();
        Integer small = calculator.calculateMaxCharacters(zone, 14);
        Integer large = calculator.calculateMaxCharacters(zone, 36);
        assertThat(large).isLessThan(small);
    }

    @Test
    void enrichDescription_appendsHint() {
        String result = calculator.enrichDescription("Zone de titre", 60, ZoneType.TITLE);
        assertThat(result).isEqualTo("Zone de titre [Max 60 characters]");
    }

    @Test
    void enrichDescription_body_marksTotal() {
        String result = calculator.enrichDescription("Zone de texte", 200, ZoneType.BODY);
        assertThat(result).isEqualTo("Zone de texte [Max 200 characters total]");
    }

    @Test
    void enrichDescription_nullBase_returnsHintOnly() {
        // Client-exact: the hint is appended after the (empty) base description,
        // so it keeps its leading space.
        assertThat(calculator.enrichDescription(null, 60, ZoneType.TITLE))
            .isEqualTo(" [Max 60 characters]");
    }

    @Test
    void enrichDescription_nullMaxChars_returnsDescription() {
        String description = "Zone de titre";
        assertThat(calculator.enrichDescription(description, null, ZoneType.CENTER_TITLE))
            .isEqualTo(description);
    }
}