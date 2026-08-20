package com.pptxgenerator.model.enums;

public enum ContentCapacity {
    HIGH,
    MEDIUM,
    LOW;

    public int getMaxBullets() {
        return switch (this) {
            case HIGH -> 5;
            case MEDIUM -> 4;
            case LOW -> 2;
        };
    }
}
