package com.pptxgenerator.client.builder;

import com.pptxgenerator.client.dto.TextRequestDto;

public class GenerativeAiRequestBuilder {

    private final String modelId;
    private final String systemPrompt;
    private final String userPrompt;
    private final Object outputSchema;
    private final Double temperature;
    private final Integer maxTokens;

    private GenerativeAiRequestBuilder(Builder builder) {
        this.modelId = builder.modelId;
        this.systemPrompt = builder.systemPrompt;
        this.userPrompt = builder.userPrompt;
        this.outputSchema = builder.outputSchema;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public TextRequestDto toRequest() {
        return TextRequestDto.builder()
                .modelId(modelId)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .outputSchema(outputSchema)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    public static class Builder {

        private String modelId;
        private String systemPrompt;
        private String userPrompt;
        private Object outputSchema;
        private Double temperature;
        private Integer maxTokens;

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        public Builder outputSchema(Object outputSchema) {
            this.outputSchema = outputSchema;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public GenerativeAiRequestBuilder build() {
            return new GenerativeAiRequestBuilder(this);
        }
    }
}
