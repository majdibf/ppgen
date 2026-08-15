package com.pptxgenerator.planner;

import com.pptxgenerator.model.PresentationPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiResponseParserTest {

    private AiResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiResponseParser();
    }

    @Test
    void testExtractJsonFromPlainJson() {
        String raw = "{\"title\": \"Test\", \"slides\": []}";
        String result = parser.extractJson(raw);
        assertEquals("{\"title\": \"Test\", \"slides\": []}", result);
    }

    @Test
    void testExtractJsonFromMarkdownFence() {
        String raw = """
                ```json
                {"title": "Test", "slides": []}
                ```
                """;
        String result = parser.extractJson(raw);
        assertEquals("{\"title\": \"Test\", \"slides\": []}", result);
    }

    @Test
    void testExtractJsonFromTextWithJson() {
        String raw = """
                Voici le plan demandé:
                {"title": "Test", "slides": []}
                N'hésitez pas à me demander des modifications.
                """;
        String result = parser.extractJson(raw);
        assertEquals("{\"title\": \"Test\", \"slides\": []}", result);
    }

    @Test
    void testExtractJsonFromMarkdownFenceWithText() {
        String raw = """
                Voici le plan:
                ```json
                {"title": "Test", "slides": []}
                ```
                J'espère que cela vous convient.
                """;
        String result = parser.extractJson(raw);
        assertEquals("{\"title\": \"Test\", \"slides\": []}", result);
    }

    @Test
    void testParseAsPresentationPlan() {
        String raw = """
                {
                  "title": "Ma Présentation",
                  "slide_count": 3,
                  "slides": [
                    {"type": "cover", "title": "Introduction"},
                    {"type": "content", "title": "Point 1", "bullet_points": ["A", "B"]},
                    {"type": "conclusion", "title": "Fin"}
                  ]
                }
                """;
        PresentationPlan plan = parser.parseAs(raw, PresentationPlan.class);
        assertNotNull(plan);
        assertEquals("Ma Présentation", plan.getTitle());
        assertEquals(3, plan.getSlideCount());
        assertEquals(3, plan.getSlides().size());
        assertEquals("cover", plan.getSlides().get(0).getType());
        assertEquals("Introduction", plan.getSlides().get(0).getTitle());
    }

    @Test
    void testParseAsWithMarkdownFence() {
        String raw = """
                ```json
                {
                  "title": "Test",
                  "slides": [{"type": "cover", "title": "Couverture"}]
                }
                ```
                """;
        PresentationPlan plan = parser.parseAs(raw, PresentationPlan.class);
        assertNotNull(plan);
        assertEquals("Test", plan.getTitle());
    }

    @Test
    void testExtractJsonWithNestedBraces() {
        String raw = """
                {
                  "title": "Test",
                  "slides": [
                    {"type": "content", "title": "Slide 1", "bullet_points": ["A", "B"]}
                  ]
                }
                """;
        String result = parser.extractJson(raw);
        assertTrue(result.startsWith("{"));
        assertTrue(result.endsWith("}"));
    }

    @Test
    void testExtractJsonThrowsOnEmpty() {
        assertThrows(IllegalArgumentException.class, () -> parser.extractJson(""));
        assertThrows(IllegalArgumentException.class, () -> parser.extractJson(null));
    }
}
