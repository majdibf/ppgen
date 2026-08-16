package com.pptxgenerator.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pptxgenerator.dto.request.ContentOptions;
import com.pptxgenerator.dto.request.CreateContentRequest;
import com.pptxgenerator.dto.request.InputContent;
import com.pptxgenerator.dto.response.ContentResponse;
import com.pptxgenerator.dto.response.Warning;
import com.pptxgenerator.entity.Content;
import jakarta.enterprise.context.ApplicationScoped;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "cdi")
@ApplicationScoped
public abstract class ContentMapper {
    
    protected ObjectMapper objectMapper = new ObjectMapper();
    
    @Mapping(source = "id", target = "contentId")
    @Mapping(target = "signature", expression = "java(mapSignature(entity))")
    @Mapping(target = "error", expression = "java(mapError(entity))")
    @Mapping(source = "inputs", target = "inputs", qualifiedByName = "jsonToInputList")
    @Mapping(source = "warnings", target = "warnings", qualifiedByName = "jsonToWarningList")
    @Mapping(source = "options", target = "options", qualifiedByName = "jsonToOptions")
    public abstract ContentResponse toResponse(Content entity);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    @Mapping(target = "queuedAt", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "endedAt", ignore = true)
    @Mapping(target = "signatureSendDocument", ignore = true)
    @Mapping(target = "signatureFetchResult", ignore = true)
    @Mapping(target = "documentUrl", ignore = true)
    @Mapping(target = "resultUrl", ignore = true)
    @Mapping(target = "errorMessage", ignore = true)
    @Mapping(target = "warnings", ignore = true)
    @Mapping(source = "inputs", target = "inputs", qualifiedByName = "inputListToJson")
    @Mapping(source = "options", target = "options", qualifiedByName = "optionsToJson")
    public abstract Content toEntity(CreateContentRequest request);
    
    protected ContentResponse.Signature mapSignature(Content entity) {
        return ContentResponse.Signature.builder()
            .sendDocument(entity.getSignatureSendDocument())
            .fetchResult(entity.getSignatureFetchResult())
            .build();
    }
    
    protected ContentResponse.ErrorDetail mapError(Content entity) {
        if (entity.getErrorMessage() == null || entity.getErrorMessage().isEmpty()) {
            return null;
        }
        return ContentResponse.ErrorDetail.builder()
            .code("PIPELINE_ERROR")
            .message(entity.getErrorMessage())
            .build();
    }
    
    @Named("jsonToInputList")
    protected List<InputContent> jsonToInputList(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<InputContent>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }
    
    @Named("inputListToJson")
    protected String inputListToJson(List<InputContent> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(inputs);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
    
    @Named("jsonToWarningList")
    protected List<Warning> jsonToWarningList(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Warning>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }
    
    @Named("jsonToOptions")
    protected ContentOptions jsonToOptions(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ContentOptions.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
    
    @Named("optionsToJson")
    protected String optionsToJson(ContentOptions options) {
        if (options == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
