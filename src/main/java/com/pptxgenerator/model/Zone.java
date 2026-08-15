package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Zone {
    private int zoneId;
    private String zoneType;
    private String position;
    private long widthEmu;
    private long heightEmu;
    private double widthInches;
    private double heightInches;
    private long xEmu;
    private long yEmu;
    private double surfacePercentage;
    private ZoneStyle style;
    private PlaceholderInfo placeholder;
    private ImageInfo imageInfo;
    private TableInfo tableInfo;
    private ChartInfo chartInfo;
    private int readingOrder;
    private String importance;

    @JsonProperty("zone_id")
    public int getZoneId() { return zoneId; }
    public void setZoneId(int zoneId) { this.zoneId = zoneId; }

    @JsonProperty("zone_type")
    public String getZoneType() { return zoneType; }
    public void setZoneType(String zoneType) { this.zoneType = zoneType; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    @JsonProperty("width_emu")
    public long getWidthEmu() { return widthEmu; }
    public void setWidthEmu(long widthEmu) { this.widthEmu = widthEmu; }

    @JsonProperty("height_emu")
    public long getHeightEmu() { return heightEmu; }
    public void setHeightEmu(long heightEmu) { this.heightEmu = heightEmu; }

    @JsonProperty("width_inches")
    public double getWidthInches() { return widthInches; }
    public void setWidthInches(double widthInches) { this.widthInches = widthInches; }

    @JsonProperty("height_inches")
    public double getHeightInches() { return heightInches; }
    public void setHeightInches(double heightInches) { this.heightInches = heightInches; }

    @JsonProperty("x_emu")
    public long getXEmu() { return xEmu; }
    public void setXEmu(long xEmu) { this.xEmu = xEmu; }

    @JsonProperty("y_emu")
    public long getYEmu() { return yEmu; }
    public void setYEmu(long yEmu) { this.yEmu = yEmu; }

    @JsonProperty("surface_percentage")
    public double getSurfacePercentage() { return surfacePercentage; }
    public void setSurfacePercentage(double surfacePercentage) { this.surfacePercentage = surfacePercentage; }

    public ZoneStyle getStyle() { return style; }
    public void setStyle(ZoneStyle style) { this.style = style; }

    public PlaceholderInfo getPlaceholder() { return placeholder; }
    public void setPlaceholder(PlaceholderInfo placeholder) { this.placeholder = placeholder; }

    @JsonProperty("image_info")
    public ImageInfo getImageInfo() { return imageInfo; }
    public void setImageInfo(ImageInfo imageInfo) { this.imageInfo = imageInfo; }

    @JsonProperty("table_info")
    public TableInfo getTableInfo() { return tableInfo; }
    public void setTableInfo(TableInfo tableInfo) { this.tableInfo = tableInfo; }

    @JsonProperty("chart_info")
    public ChartInfo getChartInfo() { return chartInfo; }
    public void setChartInfo(ChartInfo chartInfo) { this.chartInfo = chartInfo; }

    @JsonProperty("reading_order")
    public int getReadingOrder() { return readingOrder; }
    public void setReadingOrder(int readingOrder) { this.readingOrder = readingOrder; }

    public String getImportance() { return importance; }
    public void setImportance(String importance) { this.importance = importance; }
}
