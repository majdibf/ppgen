package com.pptxgenerator.common.ai;

import com.pptxgenerator.client.dto.JsonSchemaDto;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OutputSchemaProvider}.
 */
class OutputSchemaProviderTest {

    @Test
    void createPlanSchema_exposesPresentationPlanRequiredProperty() {
        // When
        JsonSchemaDto schema = OutputSchemaProvider.createPlanSchema();

        // Then
        assertThat(schema.getType()).isEqualTo(JsonSchemaDto.TypeEnum.OBJECT);
        assertThat(schema.getProperties()).containsOnlyKeys("presentation_plan");
        assertThat(schema.getRequired()).containsExactly("presentation_plan");
        JsonSchemaDto plan = schema.getProperties().get("presentation_plan");
        assertThat(plan.getRequired()).containsExactly("title", "narrative_arc", "total_slides", "slides");
    }

    @Test
    void createLayoutSchema_requiresLayoutId() {
        // When
        JsonSchemaDto schema = OutputSchemaProvider.createLayoutSchema();

        // Then
        assertThat(schema.getType()).isEqualTo(JsonSchemaDto.TypeEnum.OBJECT);
        assertThat(schema.getProperties()).containsOnlyKeys("layout_id", "rationale");
        assertThat(schema.getRequired()).containsExactly("layout_id", "rationale");
    }

    @Test
    void createSlideContentSchema_nullZones_yieldsEmptyProperties() {
        // When
        JsonSchemaDto schema = OutputSchemaProvider.createSlideContentSchema(null);

        // Then
        assertThat(schema.getType()).isEqualTo(JsonSchemaDto.TypeEnum.OBJECT);
        assertThat(schema.getProperties()).isEmpty();
        assertThat(schema.getRequired()).isEmpty();
    }

    @Test
    void createSlideContentSchema_zones_oneStringPropertyPerZoneKey() {
        // Given
        List<Zone> zones = List.of(
            Zone.builder().zoneId(0).zoneType(ZoneType.TITLE).build(),
            Zone.builder().zoneId(1).zoneType(ZoneType.BODY).build());

        // When
        JsonSchemaDto schema = OutputSchemaProvider.createSlideContentSchema(zones);

        // Then
        assertThat(schema.getProperties()).containsOnlyKeys("title_0", "body_1");
        assertThat(schema.getProperties().get("title_0").getType()).isEqualTo(JsonSchemaDto.TypeEnum.STRING);
        assertThat(schema.getRequired()).containsExactly("title_0", "body_1");
    }
}
