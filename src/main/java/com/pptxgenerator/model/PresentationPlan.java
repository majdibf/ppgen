package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PresentationPlan {
    @JsonAlias({"titre", "nom"})
    private String title;
    @JsonProperty("slide_count")
    @JsonAlias({"nombre_slides", "nombreSlides"})
    private int slideCount;
    private List<PlanSlide> slides;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getSlideCount() { return slideCount; }
    public void setSlideCount(int slideCount) { this.slideCount = slideCount; }

    public List<PlanSlide> getSlides() { return slides; }
    public void setSlides(List<PlanSlide> slides) { this.slides = slides; }
}
