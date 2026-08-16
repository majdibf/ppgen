package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanSlide {
    @JsonProperty("slide_number")
    private Integer slideNumber;
    @JsonProperty("slide_type")
    @JsonAlias({"type"})
    private String slideType;
    private String purpose;
    @JsonProperty("content_brief")
    @JsonAlias({"title"})
    private String contentBrief;
    @JsonProperty("detailed_context")
    private String detailedContext;
    @JsonProperty("section_number")
    private Integer sectionNumber;
    @JsonProperty("bullet_points")
    @JsonAlias({"points", "liste_points"})
    private List<String> bulletPoints;

    // Legacy accessors kept for renderer/tests while the pipeline migrates.
    public String getType() { return slideType; }
    public void setType(String type) { this.slideType = type; }
    public String getTitle() { return contentBrief; }
    public void setTitle(String title) { this.contentBrief = title; }
    public String getSubtitle() { return purpose; }
    public void setSubtitle(String subtitle) { this.purpose = subtitle; }

    public Integer getSlideNumber() { return slideNumber; }
    public void setSlideNumber(Integer slideNumber) { this.slideNumber = slideNumber; }
    public String getSlideType() { return slideType; }
    public void setSlideType(String slideType) { this.slideType = slideType; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getContentBrief() { return contentBrief; }
    public void setContentBrief(String contentBrief) { this.contentBrief = contentBrief; }
    public String getDetailedContext() { return detailedContext; }
    public void setDetailedContext(String detailedContext) { this.detailedContext = detailedContext; }
    public Integer getSectionNumber() { return sectionNumber; }
    public void setSectionNumber(Integer sectionNumber) { this.sectionNumber = sectionNumber; }
    public List<String> getBulletPoints() { return bulletPoints; }
    public void setBulletPoints(List<String> bulletPoints) { this.bulletPoints = bulletPoints; }
}
