package com.pptxgenerator.planner;

import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.model.PresentationPlan;
import com.pptxgenerator.model.TemplateStructure;
import com.pptxgenerator.model.SlideLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PresentationPlannerTest {

    private PresentationPlanner planner;
    private GenerativeAiGateway mockGateway;
    private AiResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiResponseParser();
        mockGateway = new MockGenerativeAiGateway();
        planner = new PresentationPlanner();
        planner.aiGateway = mockGateway;
        planner.responseParser = parser;
    }

    @Test
    void testGeneratePlan() {
        TemplateStructure template = new TemplateStructure();
        template.setLayouts(List.of(new SlideLayout(), new SlideLayout(), new SlideLayout()));

        PresentationPlan plan = planner.generatePlan("Intelligence Artificielle", template);

        assertNotNull(plan);
        assertEquals("Présentation Mock", plan.getTitle());
        assertEquals(5, plan.getSlideCount());
        assertNotNull(plan.getSlides());
        assertEquals(5, plan.getSlides().size());
        assertEquals("cover", plan.getSlides().get(0).getType());
        assertEquals("conclusion", plan.getSlides().get(4).getType());
    }

    @Test
    void testGeneratePlanWithEmptyTemplate() {
        TemplateStructure template = new TemplateStructure();

        PresentationPlan plan = planner.generatePlan("Sujet Test", template);

        assertNotNull(plan);
        assertNotNull(plan.getSlides());
        assertTrue(plan.getSlides().size() > 0);
    }

    static class MockGenerativeAiGateway extends GenerativeAiGateway {

        @Override
        public TextResponseDto processRequest(com.pptxgenerator.client.dto.TextRequestDto request) {
            String mockJson = """
                    {
                      "title": "Présentation Mock",
                      "slide_count": 5,
                      "slides": [
                        {"type": "cover", "title": "Introduction", "subtitle": "Sous-titre"},
                        {"type": "content", "title": "Point 1", "bullet_points": ["A", "B"]},
                        {"type": "content", "title": "Point 2", "bullet_points": ["C", "D"]},
                        {"type": "content", "title": "Point 3", "bullet_points": ["E", "F"]},
                        {"type": "conclusion", "title": "Conclusion", "bullet_points": ["Résumé"]}
                      ]
                    }
                    """;
            return new TextResponseDto(List.of(new TextResponseDto.TextCandidate(mockJson)));
        }
    }
}
