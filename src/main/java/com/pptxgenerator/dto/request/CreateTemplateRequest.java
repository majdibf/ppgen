package com.pptxgenerator.dto.request;

import com.pptxgenerator.model.enums.FileType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTemplateRequest {
    
    @NotNull
    private String name;
    
    private String description;
    
    @NotNull
    private FileType fileType;
}
