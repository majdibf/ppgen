package com.pptxgenerator.client.dto;

import com.pptxgenerator.client.helper.AIRequestType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TextRequestDto {

    private String modelId;
    private String systemPrompt;
    private String userPrompt;
    private Object outputSchema;
    private Double temperature;
    private Integer maxTokens;

    @JsonIgnore
    private AIRequestType requestType;

    public TextRequestDto() {}

    private TextRequestDto(Builder builder) {
        this.modelId = builder.modelId;
        this.systemPrompt = builder.systemPrompt;
        this.userPrompt = builder.userPrompt;
        this.outputSchema = builder.outputSchema;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.requestType = builder.requestType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getUserPrompt() { return userPrompt; }
    public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }

    public Object getOutputSchema() { return outputSchema; }
    public void setOutputSchema(Object outputSchema) { this.outputSchema = outputSchema; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public AIRequestType getRequestType() { return requestType; }
    public void setRequestType(AIRequestType requestType) { this.requestType = requestType; }

    public static class Builder {
        private String modelId;
        private String systemPrompt;
        private String userPrompt;
        private Object outputSchema;
        private Double temperature;
        private Integer maxTokens;
        private AIRequestType requestType;

        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder userPrompt(String userPrompt) { this.userPrompt = userPrompt; return this; }
        public Builder outputSchema(Object outputSchema) { this.outputSchema = outputSchema; return this; }
        public Builder temperature(Double temperature) { this.temperature = temperature; return this; }
        public Builder maxTokens(Integer maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder requestType(AIRequestType requestType) { this.requestType = requestType; return this; }

        public TextRequestDto build() {
            return new TextRequestDto(this);
        }
    }
}
