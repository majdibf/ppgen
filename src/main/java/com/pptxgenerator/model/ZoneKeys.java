package com.pptxgenerator.model;

/**
 * Single source of truth for the stable zone key used across the pipeline:
 * {@code zone_type_zone_id} (e.g. {@code title_0}, {@code body_1}).
 *
 * <p>Both the content generators (which produce the keyed text map) and the renderer
 * (which fills placeholders by key) must use this exact format, otherwise zones silently
 * fail to match. The key is lower-case snake_case, matching the JSON snake_case convention
 * of the project and the AI output schema.
 */
public final class ZoneKeys {

    private ZoneKeys() {
    }

    public static String key(Zone zone) {
        return zone.getZoneType().getValue() + "_" + zone.getZoneId();
    }
}
