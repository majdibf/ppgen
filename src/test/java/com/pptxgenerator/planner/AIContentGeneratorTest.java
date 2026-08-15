package com.pptxgenerator.planner;

import com.pptxgenerator.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AIContentGeneratorTest {

    private AIContentGenerator contentGenerator;

    @BeforeEach
    void setUp() {
        contentGenerator = new AIContentGenerator();
        contentGenerator.aiGateway = new MockGenerativeAiGateway();
        contentGenerator.responseParser = new AiResponseParser();
    }

    @Test
    void testGenerateContentBasic() {
        EnrichedPlan enrichedPlan = createMockEnrichedPlan();

        ContentMap contentMap = contentGenerator.generateContent(enrichedPlan, "Test Topic");

        assertNotNull(contentMap);
        assertEquals(enrichedPlan.getTitle(), contentMap.getPresentationTitle());
        assertEquals(enrichedPlan.getSlides().size(), contentMap.getTotalSlides());
    }

    @Test
    void testGenerateContentForSlideWithTitle() {
        EnrichedPlan enrichedPlan = createMockEnrichedPlan();

        ContentMap contentMap = contentGenerator.generateContent(enrichedPlan, "Test Topic");

        SlideContent slideContent = contentMap.getSlideContent("slide_0");
        assertNotNull(slideContent);
        assertEquals("slide_0", slideContent.getSlideId());
        assertEquals("Introduction", slideContent.getSlideTitle());
        assertNotNull(slideContent.getZoneContents());
        assertFalse(slideContent.getZoneContents().isEmpty());
    }

    @Test
    void testGenerateContentForSlideWithBulletPoints() {
        EnrichedPlan enrichedPlan = createMockEnrichedPlan();

        ContentMap contentMap = contentGenerator.generateContent(enrichedPlan, "Test Topic");

        SlideContent slideContent = contentMap.getSlideContent("slide_1");
        assertNotNull(slideContent);
        assertNotNull(slideContent.getZoneContents());
        
        ZoneContent bodyZone = slideContent.getZoneContents().stream()
            .filter(z -> "body".equals(z.getZoneType()))
            .findFirst()
            .orElse(null);
        
        assertNotNull(bodyZone);
        assertNotNull(bodyZone.getContent());
        assertTrue(bodyZone.getContent().contains("Point A"));
    }

    @Test
    void testGenerateContentForSlideWithoutLayout() {
        EnrichedPlan enrichedPlan = createMockEnrichedPlanWithoutLayout();

        ContentMap contentMap = contentGenerator.generateContent(enrichedPlan, "Test Topic");

        SlideContent slideContent = contentMap.getSlideContent("slide_0");
        assertNotNull(slideContent);
        assertNotNull(slideContent.getZoneContents());
        assertFalse(slideContent.getZoneContents().isEmpty());
    }

    @Test
    void testGenerateContentMapHasAllSlides() {
        EnrichedPlan enrichedPlan = createMockEnrichedPlan();

        ContentMap contentMap = contentGenerator.generateContent(enrichedPlan, "Test Topic");

        for (int i = 0; i < enrichedPlan.getSlides().size(); i++) {
            String slideId = "slide_" + i;
            assertTrue(contentMap.hasSlideContent(slideId));
            assertNotNull(contentMap.getSlideContent(slideId));
        }
    }

    @Test
    void testGenerateContentForPictureZone() {
        EnrichedPlan enrichedPlan = createMockEnrichedPlanWithPicture();

        ContentMap contentMap = contentGenerator.generateContent(enrichedPlan, "Test Topic");

        SlideContent slideContent = contentMap.getSlideContent("slide_0");
        assertNotNull(slideContent);
        
        ZoneContent pictureZone = slideContent.getZoneContents().stream()
            .filter(z -> "picture".equals(z.getZoneType()))
            .findFirst()
            .orElse(null);
        
        assertNotNull(pictureZone);
        assertNotNull(pictureZone.getImageDescription());
    }

    private EnrichedPlan createMockEnrichedPlan() {
        EnrichedPlan plan = new EnrichedPlan();
        plan.setTitle("Test Presentation");
        
        List<EnrichedSlide> slides = new ArrayList<>();
        
        EnrichedSlide slide1 = new EnrichedSlide();
        slide1.setTitle("Introduction");
        slide1.setType("cover");
        slide1.setAssignedLayout(createMockLayout("layout_1", "TITLE_SLIDE"));
        slides.add(slide1);
        
        EnrichedSlide slide2 = new EnrichedSlide();
        slide2.setTitle("Point 1");
        slide2.setType("content");
        slide2.setBulletPoints(List.of("Point A", "Point B", "Point C"));
        slide2.setAssignedLayout(createMockLayout("layout_2", "CONTENT"));
        slides.add(slide2);
        
        EnrichedSlide slide3 = new EnrichedSlide();
        slide3.setTitle("Conclusion");
        slide3.setType("conclusion");
        slide3.setAssignedLayout(createMockLayout("layout_3", "CONTENT"));
        slides.add(slide3);
        
        plan.setSlides(slides);
        plan.setSlideCount(slides.size());
        
        return plan;
    }

    private EnrichedPlan createMockEnrichedPlanWithoutLayout() {
        EnrichedPlan plan = new EnrichedPlan();
        plan.setTitle("Test Presentation");
        
        List<EnrichedSlide> slides = new ArrayList<>();
        
        EnrichedSlide slide1 = new EnrichedSlide();
        slide1.setTitle("Introduction");
        slide1.setType("cover");
        slide1.setAssignedLayout(null);
        slides.add(slide1);
        
        plan.setSlides(slides);
        plan.setSlideCount(slides.size());
        
        return plan;
    }

    private EnrichedPlan createMockEnrichedPlanWithPicture() {
        EnrichedPlan plan = new EnrichedPlan();
        plan.setTitle("Test Presentation");
        
        List<EnrichedSlide> slides = new ArrayList<>();
        
        EnrichedSlide slide1 = new EnrichedSlide();
        slide1.setTitle("Image Slide");
        slide1.setType("content");
        slide1.setAssignedLayout(createMockLayoutWithPicture("layout_1", "CONTENT"));
        slides.add(slide1);
        
        plan.setSlides(slides);
        plan.setSlideCount(slides.size());
        
        return plan;
    }

    private SlideLayout createMockLayout(String layoutId, String semanticType) {
        SlideLayout layout = new SlideLayout();
        layout.setLayoutId(layoutId);
        layout.setSemanticType(semanticType);
        
        List<Zone> zones = new ArrayList<>();
        
        Zone titleZone = new Zone();
        titleZone.setZoneId(0);
        titleZone.setZoneType("title");
        titleZone.setImportance("HIGH");
        zones.add(titleZone);
        
        Zone bodyZone = new Zone();
        bodyZone.setZoneId(1);
        bodyZone.setZoneType("body");
        bodyZone.setImportance("MEDIUM");
        zones.add(bodyZone);
        
        layout.setZones(zones);
        
        return layout;
    }

    private SlideLayout createMockLayoutWithPicture(String layoutId, String semanticType) {
        SlideLayout layout = new SlideLayout();
        layout.setLayoutId(layoutId);
        layout.setSemanticType(semanticType);
        
        List<Zone> zones = new ArrayList<>();
        
        Zone titleZone = new Zone();
        titleZone.setZoneId(0);
        titleZone.setZoneType("title");
        titleZone.setImportance("HIGH");
        zones.add(titleZone);
        
        Zone pictureZone = new Zone();
        pictureZone.setZoneId(1);
        pictureZone.setZoneType("picture");
        pictureZone.setImportance("HIGH");
        zones.add(pictureZone);
        
        layout.setZones(zones);
        
        return layout;
    }

    static class MockGenerativeAiGateway extends com.pptxgenerator.client.GenerativeAiGateway {
        @Override
        public com.pptxgenerator.client.dto.TextResponseDto processRequest(
                com.pptxgenerator.client.dto.TextRequestDto request) {
            String mockResponse = "Contenu généré par mock AI";
            return new com.pptxgenerator.client.dto.TextResponseDto(
                List.of(new com.pptxgenerator.client.dto.TextResponseDto.TextCandidate(mockResponse))
            );
        }
    }
}
