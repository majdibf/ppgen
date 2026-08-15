package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChartInfo {
    @JsonProperty("chart_type")
    private String chartType;
    @JsonProperty("has_legend")
    private boolean hasLegend;
    @JsonProperty("has_title")
    private boolean hasTitle;
    @JsonProperty("series_count")
    private int seriesCount;
    @JsonProperty("category_count")
    private int categoryCount;

    public String getChartType() { return chartType; }
    public void setChartType(String chartType) { this.chartType = chartType; }

    public boolean isHasLegend() { return hasLegend; }
    public void setHasLegend(boolean hasLegend) { this.hasLegend = hasLegend; }

    public boolean isHasTitle() { return hasTitle; }
    public void setHasTitle(boolean hasTitle) { this.hasTitle = hasTitle; }

    public int getSeriesCount() { return seriesCount; }
    public void setSeriesCount(int seriesCount) { this.seriesCount = seriesCount; }

    public int getCategoryCount() { return categoryCount; }
    public void setCategoryCount(int categoryCount) { this.categoryCount = categoryCount; }
}
