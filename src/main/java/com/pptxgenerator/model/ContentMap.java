package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

public class ContentMap {
    @JsonProperty("presentation_title")
    private String presentationTitle;
    
    @JsonProperty("slide_contents")
    private Map<String, SlideContent> slideContents;
    
    @JsonProperty("total_slides")
    private int totalSlides;

    public ContentMap() {
        this.slideContents = new LinkedHashMap<>();
    }

    public ContentMap(String presentationTitle) {
        this.presentationTitle = presentationTitle;
        this.slideContents = new LinkedHashMap<>();
    }

    public String getPresentationTitle() { return presentationTitle; }
    public void setPresentationTitle(String presentationTitle) { this.presentationTitle = presentationTitle; }

    public Map<String, SlideContent> getSlideContents() { return slideContents; }
    public void setSlideContents(Map<String, SlideContent> slideContents) { this.slideContents = slideContents; }

    public int getTotalSlides() { return totalSlides; }
    public void setTotalSlides(int totalSlides) { this.totalSlides = totalSlides; }

    public void addSlideContent(String slideId, SlideContent content) {
        slideContents.put(slideId, content);
        totalSlides = slideContents.size();
    }

    public SlideContent getSlideContent(String slideId) {
        return slideContents.get(slideId);
    }

    public boolean hasSlideContent(String slideId) {
        return slideContents.containsKey(slideId);
    }
}
