package com.pptxgenerator.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result payload of the content download endpoint, aligned with the real project:
 * the raw generated file bytes plus the persisted output file name.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentResultDto {

    private String id;

    private byte[] content;

    private String fileName;
}
