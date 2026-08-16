package com.pptxgenerator.dto.response;

import com.pptxgenerator.dto.request.ContentOptions;
import com.pptxgenerator.dto.request.InputContent;
import com.pptxgenerator.model.enums.ContentStatus;
import com.pptxgenerator.model.enums.Operation;
import com.pptxgenerator.model.enums.OutputFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentResponse {
    
    private String contentId;
    private Operation operation;
    private String modelId;
    private OutputFormat outputFormat;
    private String templateId;
    private String instructions;
    private List<InputContent> inputs;
    private Boolean webSearch;
    private ContentOptions options;
    private ContentStatus status;
    private Signature signature;
    private Instant submittedAt;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant endedAt;
    private List<Warning> warnings;
    private ErrorDetail error;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Signature {
        private String sendDocument;
        private String fetchResult;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorDetail {
        private String code;
        private String message;
    }
}
