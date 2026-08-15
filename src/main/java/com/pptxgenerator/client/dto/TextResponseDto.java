package com.pptxgenerator.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TextResponseDto {

    private List<TextCandidate> candidates;

    public TextResponseDto() {}

    public TextResponseDto(List<TextCandidate> candidates) {
        this.candidates = candidates;
    }

    public List<TextCandidate> getCandidates() { return candidates; }
    public void setCandidates(List<TextCandidate> candidates) { this.candidates = candidates; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextCandidate {
        private String text;

        public TextCandidate() {}

        public TextCandidate(String text) {
            this.text = text;
        }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}
