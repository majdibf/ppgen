package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class EnrichedSlide {
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

    public EnrichedSlide() {}

    public EnrichedSlide(PlanSlide planSlide) {
        this.type = planSlide.getType();
        this.title = planSlide.getTitle();
        this.subtitle = planSlide.getSubtitle();
        this.bulletPoints = planSlide.getBulletPoints();
        this.notes = planSlide.getNotes();
        this.layoutHint = planSlide.getLayoutHint();
        this.isDynamic = false;
    }

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
