package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ZoneContent {
    @JsonProperty("zone_id")
    private int zoneId;
    
    @JsonProperty("zone_type")
    private String zoneType;

    @JsonProperty("zone_key")
    private String zoneKey;
    
    private String content;
    
    @JsonProperty("image_description")
    private String imageDescription;
    
    @JsonProperty("table_data")
    private List<List<String>> tableData;
    
    @JsonProperty("chart_data")
    private ChartData chartData;

    public ZoneContent() {}

    public int getZoneId() { return zoneId; }
    public void setZoneId(int zoneId) { this.zoneId = zoneId; }

    public String getZoneType() { return zoneType; }
    public void setZoneType(String zoneType) { this.zoneType = zoneType; }

    public String getZoneKey() { return zoneKey; }
    public void setZoneKey(String zoneKey) { this.zoneKey = zoneKey; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getImageDescription() { return imageDescription; }
    public void setImageDescription(String imageDescription) { this.imageDescription = imageDescription; }

    public List<List<String>> getTableData() { return tableData; }
    public void setTableData(List<List<String>> tableData) { this.tableData = tableData; }

    public ChartData getChartData() { return chartData; }
    public void setChartData(ChartData chartData) { this.chartData = chartData; }
}
