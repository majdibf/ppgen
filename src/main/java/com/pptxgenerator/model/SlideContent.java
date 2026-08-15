package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SlideContent {
    @JsonProperty("slide_id")
    private String slideId;
    
    @JsonProperty("slide_title")
    private String slideTitle;
    
    @JsonProperty("zone_contents")
    private List<ZoneContent> zoneContents;

    public SlideContent() {}

    public String getSlideId() { return slideId; }
    public void setSlideId(String slideId) { this.slideId = slideId; }

    public String getSlideTitle() { return slideTitle; }
    public void setSlideTitle(String slideTitle) { this.slideTitle = slideTitle; }

    public List<ZoneContent> getZoneContents() { return zoneContents; }
    public void setZoneContents(List<ZoneContent> zoneContents) { this.zoneContents = zoneContents; }
}
