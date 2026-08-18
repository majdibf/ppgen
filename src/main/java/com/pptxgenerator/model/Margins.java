// Margins.java
package com.pptxgenerator.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Margins {
    private double left;    // en EMU
    private double right;   // en EMU
    private double top;     // en EMU
    private double bottom;  // en EMU

    public Margins(double left, double right, double top, double bottom) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }

    public double getLeftInches() { return left / 914400.0; }
    public void setLeftInches(double ignored) { }
    public double getRightInches() { return right / 914400.0; }
    public void setRightInches(double ignored) { }
    public double getTopInches() { return top / 914400.0; }
    public void setTopInches(double ignored) { }
    public double getBottomInches() { return bottom / 914400.0; }
    public void setBottomInches(double ignored) { }
}
