package com.pptxgenerator.dto.response;

import com.pptxgenerator.model.enums.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateResponse {
    
    private String templateId;
    private String name;
    private String description;
    private FileType fileType;
    private String fileName;
    private Long fileSizeBytes;
    private String templateAnalysis;
    private Instant createdAt;
    private Instant updatedAt;
}
