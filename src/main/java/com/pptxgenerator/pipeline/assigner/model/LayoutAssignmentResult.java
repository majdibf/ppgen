package com.pptxgenerator.pipeline.assigner.model;

import com.pptxgenerator.model.LayoutAnalysis;

/**
 * Outcome of a single layout assignment: the chosen {@link LayoutAnalysis}, a human-readable
 * rationale, and an optional warning code (e.g. {@code LAYOUT_FALLBACK}) when the choice was
 * not the primary preference.
 */
public record LayoutAssignmentResult(LayoutAnalysis layout, String rationale, String warningCode) {
}
