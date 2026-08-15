package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;

public class EnrichedPlan {
    private String title;
    @JsonProperty("slide_count")
    private int slideCount;
    private List<EnrichedSlide> slides;
    
    @JsonProperty("template_used")
    private String templateUsed;
    
    @JsonProperty("dynamic_slide_count")
    private int dynamicSlideCount;

    public EnrichedPlan() {}

    public EnrichedPlan(PresentationPlan plan) {
        this.title = plan.getTitle();
        this.slideCount = plan.getSlideCount();
        this.slides = plan.getSlides().stream()
                .map(EnrichedSlide::new)
                .collect(Collectors.toList());
        this.dynamicSlideCount = 0;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getSlideCount() { return slideCount; }
    public void setSlideCount(int slideCount) { this.slideCount = slideCount; }

    public List<EnrichedSlide> getSlides() { return slides; }
    public void setSlides(List<EnrichedSlide> slides) { this.slides = slides; }

    public String getTemplateUsed() { return templateUsed; }
    public void setTemplateUsed(String templateUsed) { this.templateUsed = templateUsed; }

    public int getDynamicSlideCount() { return dynamicSlideCount; }
    public void setDynamicSlideCount(int dynamicSlideCount) { this.dynamicSlideCount = dynamicSlideCount; }
    
    public void updateDynamicCount() {
        this.dynamicSlideCount = (int) slides.stream()
                .filter(EnrichedSlide::isDynamic)
                .count();
    }
}
