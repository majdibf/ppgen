package com.pptxgenerator.pipeline;

/**
 * Result of a successful pipeline execution, name-aligned with the real project
 * {@code PPTXPipelineResult}: the caller (ContentCreationService) receives the
 * rendered PPTX bytes through the Mutiny chain and performs the post-processing
 * (result upload, status update) itself.
 */
public record PPTXPipelineResult(byte[] pptxBytes) {
}