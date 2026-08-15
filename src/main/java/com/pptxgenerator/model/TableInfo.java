package com.pptxgenerator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TableInfo {
    private int rows;
    private int columns;
    @JsonProperty("has_header")
    private boolean hasHeader;
    @JsonProperty("header_style")
    private TableHeaderStyle headerStyle;
    @JsonProperty("data_rows")
    private int dataRows;

    public int getRows() { return rows; }
    public void setRows(int rows) { this.rows = rows; }

    public int getColumns() { return columns; }
    public void setColumns(int columns) { this.columns = columns; }

    public boolean isHasHeader() { return hasHeader; }
    public void setHasHeader(boolean hasHeader) { this.hasHeader = hasHeader; }

    public TableHeaderStyle getHeaderStyle() { return headerStyle; }
    public void setHeaderStyle(TableHeaderStyle headerStyle) { this.headerStyle = headerStyle; }

    public int getDataRows() { return dataRows; }
    public void setDataRows(int dataRows) { this.dataRows = dataRows; }
}
