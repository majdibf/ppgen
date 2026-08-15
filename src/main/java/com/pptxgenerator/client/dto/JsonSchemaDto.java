package com.pptxgenerator.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonSchemaDto {

    public enum TypeEnum {
        OBJECT("object"),
        ARRAY("array"),
        STRING("string"),
        NUMBER("number"),
        BOOLEAN("boolean"),
        INTEGER("integer");

        private final String value;

        TypeEnum(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    private TypeEnum type;

    @JsonProperty("enum")
    private List<String> enum_;

    private Map<String, JsonSchemaDto> properties;
    private List<String> required;
    private JsonSchemaDto items;
    private Integer minItems;
    private Integer maxItems;
    private Integer maxLength;

    public JsonSchemaDto() {}

    private JsonSchemaDto(Builder builder) {
        this.type = builder.type;
        this.enum_ = builder.enum_;
        this.properties = builder.properties;
        this.required = builder.required;
        this.items = builder.items;
        this.minItems = builder.minItems;
        this.maxItems = builder.maxItems;
        this.maxLength = builder.maxLength;
    }

    public static Builder builder() {
        return new Builder();
    }

    public TypeEnum getType() { return type; }
    public void setType(TypeEnum type) { this.type = type; }

    public List<String> getEnum_() { return enum_; }
    public void setEnum_(List<String> enum_) { this.enum_ = enum_; }

    public Map<String, JsonSchemaDto> getProperties() { return properties; }
    public void setProperties(Map<String, JsonSchemaDto> properties) { this.properties = properties; }

    public List<String> getRequired() { return required; }
    public void setRequired(List<String> required) { this.required = required; }

    public JsonSchemaDto getItems() { return items; }
    public void setItems(JsonSchemaDto items) { this.items = items; }

    public Integer getMinItems() { return minItems; }
    public void setMinItems(Integer minItems) { this.minItems = minItems; }

    public Integer getMaxItems() { return maxItems; }
    public void setMaxItems(Integer maxItems) { this.maxItems = maxItems; }

    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

    public static class Builder {
        private TypeEnum type;
        private List<String> enum_;
        private Map<String, JsonSchemaDto> properties;
        private List<String> required;
        private JsonSchemaDto items;
        private Integer minItems;
        private Integer maxItems;
        private Integer maxLength;

        public Builder type(TypeEnum type) { this.type = type; return this; }
        public Builder enum_(List<String> enum_) { this.enum_ = enum_; return this; }
        public Builder properties(Map<String, JsonSchemaDto> properties) { this.properties = properties; return this; }
        public Builder required(List<String> required) { this.required = required; return this; }
        public Builder items(JsonSchemaDto items) { this.items = items; return this; }
        public Builder minItems(Integer minItems) { this.minItems = minItems; return this; }
        public Builder maxItems(Integer maxItems) { this.maxItems = maxItems; return this; }
        public Builder maxLength(Integer maxLength) { this.maxLength = maxLength; return this; }

        public JsonSchemaDto build() {
            return new JsonSchemaDto(this);
        }
    }
}
