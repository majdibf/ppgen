package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Metadata {
    @JsonProperty("analysis_version")
    private String analysisVersion;
    @JsonProperty("template_original_name")
    private String templateOriginalName;
    @JsonProperty("slide_count")
    private int slideCount;
    @JsonProperty("layout_count")
    private int layoutCount;
    @JsonProperty("analysis_date")
    private String analysisDate;

    public String getAnalysisVersion() { return analysisVersion; }
    public void setAnalysisVersion(String analysisVersion) { this.analysisVersion = analysisVersion; }

    public String getTemplateOriginalName() { return templateOriginalName; }
    public void setTemplateOriginalName(String templateOriginalName) { this.templateOriginalName = templateOriginalName; }

    public int getSlideCount() { return slideCount; }
    public void setSlideCount(int slideCount) { this.slideCount = slideCount; }

    public int getLayoutCount() { return layoutCount; }
    public void setLayoutCount(int layoutCount) { this.layoutCount = layoutCount; }

    public String getAnalysisDate() { return analysisDate; }
    public void setAnalysisDate(String analysisDate) { this.analysisDate = analysisDate; }
}
