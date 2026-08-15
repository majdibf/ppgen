package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanSlide {
    private String type;
    @JsonAlias({"titre", "nom"})
    private String title;
    @JsonAlias({"sous_titre", "sousTitre"})
    private String subtitle;
    @JsonProperty("bullet_points")
    @JsonAlias({"points", "liste_points"})
    private List<String> bulletPoints;
    private String notes;
    @JsonProperty("layout_hint")
    private String layoutHint;

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
}
