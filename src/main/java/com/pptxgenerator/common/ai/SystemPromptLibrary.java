package com.pptxgenerator.common.ai;

/**
 * Single source of truth for the fragments shared by every stage system prompt.
 *
 * <p>Previously each {@code *PromptBuilder} (and {@code TemplateAnalyzer}) re-declared its own
 * "Tu es un expert en ... PowerPoint" preamble and the "respond with JSON only" directive, which
 * drifted out of sync. Centralising them here guarantees every provider receives a consistent
 * instruction set.
 */
public final class SystemPromptLibrary {

    private SystemPromptLibrary() {
    }

    /**
     * Directive appended to every system prompt so providers always return JSON only. It used to be
     * hardcoded inside {@code GroqGenerativeAiApi}; it now lives here and is included by each builder.
     */
    public static final String JSON_ONLY_DIRECTIVE =
            "IMPORTANT: You must respond with valid JSON only. Do not include any other text, markdown formatting, or explanations.";

    /**
     * Standard "expert" preamble shared by all stages (the only part that differs is the specialty).
     */
    public static String expertIntro(String specialty) {
        return "Tu es un expert en " + specialty + ".";
    }

    /**
     * Appends the JSON-only directive to a system prompt (single source of the directive).
     */
    public static String withJsonDirective(String systemPrompt) {
        return systemPrompt + "\n\n" + JSON_ONLY_DIRECTIVE;
    }
}
