package com.pptxgenerator.common;

/**
 * Shared text-truncation helper: "…" style ellipsis on a hard character budget.
 * Replaces the copy-pasted "length() > N ? substring(0, N - 3) + "..." : text"
 * idiom spread across the pipeline.
 */
public final class TextTruncator {

    private static final String ELLIPSIS = "...";
    private static final int ELLIPSIS_LENGTH = ELLIPSIS.length();

    private TextTruncator() {
    }

    /**
     * Truncates the text to {@code maxLength} characters INCLUDING the trailing
     * ellipsis when over the limit. Preserves the exact legacy behaviour of the
     * 100-char hooks: {@code substring(0, 97) + "..."} for maxLength 100.
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - ELLIPSIS_LENGTH)).trim() + ELLIPSIS;
    }
}