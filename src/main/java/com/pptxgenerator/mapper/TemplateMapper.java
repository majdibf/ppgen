package com.pptxgenerator.mapper;

import com.pptxgenerator.dto.request.CreateTemplateRequest;
import com.pptxgenerator.dto.response.TemplateResponse;
import com.pptxgenerator.entity.Template;
import jakarta.enterprise.context.ApplicationScoped;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
@ApplicationScoped
public interface TemplateMapper {
    
    @Mapping(source = "id", target = "templateId")
    TemplateResponse toResponse(Template entity);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fileUrl", ignore = true)
    @Mapping(target = "fileSizeBytes", ignore = true)
    @Mapping(target = "fileName", ignore = true)
    @Mapping(target = "templateAnalysis", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Template toEntity(CreateTemplateRequest request);
}
