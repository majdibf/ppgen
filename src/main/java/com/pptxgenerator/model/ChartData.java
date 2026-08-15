package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ChartData {
    @JsonProperty("chart_type")
    private String chartType;
    
    private String title;
    
    @JsonProperty("categories")
    private List<String> categories;
    
    @JsonProperty("series")
    private List<Series> series;

    public ChartData() {}

    public String getChartType() { return chartType; }
    public void setChartType(String chartType) { this.chartType = chartType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }

    public List<Series> getSeries() { return series; }
    public void setSeries(List<Series> series) { this.series = series; }

    public static class Series {
        private String name;
        private List<Double> values;

        public Series() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<Double> getValues() { return values; }
        public void setValues(List<Double> values) { this.values = values; }
    }
}
