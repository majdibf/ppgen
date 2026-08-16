package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class EnrichedSlide {
    @JsonProperty("slide_number")
    private Integer slideNumber;
    @JsonProperty("slide_type")
    private String slideType;
    private String purpose;
    @JsonProperty("content_brief")
    private String contentBrief;
    @JsonProperty("detailed_context")
    private String detailedContext;
    @JsonProperty("section_number")
    private Integer sectionNumber;
    private String type;
    private String title;
    private String subtitle;
    @JsonProperty("bullet_points")
    private List<String> bulletPoints;
    private String notes;
    @JsonProperty("layout_hint")
    private String layoutHint;
    
    @JsonProperty("assigned_layout")
    private SlideLayout assignedLayout;
    
    @JsonProperty("is_dynamic")
    private boolean isDynamic;
    
    @JsonProperty("layout_match_score")
    private double layoutMatchScore;
    @JsonProperty("assignment_method")
    private String assignmentMethod;
    private String rationale;

    public EnrichedSlide() {}

    public EnrichedSlide(PlanSlide planSlide) {
        this.slideNumber = planSlide.getSlideNumber();
        this.slideType = planSlide.getSlideType();
        this.purpose = planSlide.getPurpose();
        this.contentBrief = planSlide.getContentBrief();
        this.detailedContext = planSlide.getDetailedContext();
        this.sectionNumber = planSlide.getSectionNumber();
        this.bulletPoints = planSlide.getBulletPoints();
        this.type = planSlide.getSlideType();
        this.title = planSlide.getContentBrief();
        this.subtitle = planSlide.getPurpose();
        this.isDynamic = false;
    }

    public Integer getSlideNumber() { return slideNumber; }
    public void setSlideNumber(Integer value) { this.slideNumber = value; }
    public String getSlideType() { return slideType != null ? slideType : type; }
    public void setSlideType(String value) { this.slideType = value; this.type = value; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String value) { this.purpose = value; }
    public String getContentBrief() { return contentBrief; }
    public void setContentBrief(String value) { this.contentBrief = value; }
    public String getDetailedContext() { return detailedContext; }
    public void setDetailedContext(String value) { this.detailedContext = value; }
    public Integer getSectionNumber() { return sectionNumber; }
    public void setSectionNumber(Integer value) { this.sectionNumber = value; }
    public String getAssignmentMethod() { return assignmentMethod; }
    public void setAssignmentMethod(String value) { this.assignmentMethod = value; }
    public String getRationale() { return rationale; }
    public void setRationale(String value) { this.rationale = value; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public List<String> getBulletPoints() { return bulletPoints; }
    public void setBulletPoints(List<String> bulletPoints) { this.bulletPoints = bulletPoints; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getLayoutHint() { return layoutHint; }
    public void setLayoutHint(String layoutHint) { this.layoutHint = layoutHint; }

    public SlideLayout getAssignedLayout() { return assignedLayout; }
    public void setAssignedLayout(SlideLayout assignedLayout) { this.assignedLayout = assignedLayout; }

    public boolean isDynamic() { return isDynamic; }
    public void setDynamic(boolean dynamic) { isDynamic = dynamic; }

    public double getLayoutMatchScore() { return layoutMatchScore; }
    public void setLayoutMatchScore(double layoutMatchScore) { this.layoutMatchScore = layoutMatchScore; }
}
