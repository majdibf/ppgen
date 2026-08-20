package com.pptxgenerator.generator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pptxgenerator.assigner.model.ClassifiedLayout;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedContent {

    @JsonProperty("generated_content")
    private GeneratedContentData generatedContent;

    private List<ContentGenerationWarning> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeneratedContentData {
        private String title;

        @JsonProperty("total_slides")
        private Integer totalSlides;

        private List<SlideWithContent> slides;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlideWithContent {
        @JsonProperty("slide_number")
        private Integer slideNumber;

        private String slideType;
        private String purpose;

        @JsonProperty("content_brief")
        private String contentBrief;

        @JsonProperty("detailed_context")
        private String detailedContext;

        private ClassifiedLayout layout;
        private SlideContent content;
    }
}
