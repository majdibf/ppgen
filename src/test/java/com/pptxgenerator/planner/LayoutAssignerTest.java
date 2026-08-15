package com.pptxgenerator.planner;

import com.pptxgenerator.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LayoutAssignerTest {

    private LayoutAssigner layoutAssigner;

    @BeforeEach
    void setUp() {
        layoutAssigner = new LayoutAssigner();
    }

    @Test
    void testAssignLayoutsBasic() {
        TemplateStructure template = createMockTemplate();
        PresentationPlan plan = createMockPlan();

        EnrichedPlan enrichedPlan = layoutAssigner.assignLayouts(template, plan);

        assertNotNull(enrichedPlan);
        assertEquals(plan.getTitle(), enrichedPlan.getTitle());
        assertEquals(plan.getSlides().size(), enrichedPlan.getSlides().size());
        assertNotNull(enrichedPlan.getTemplateUsed());
    }

    @Test
    void testAssignLayoutsCoversTitleSlide() {
        TemplateStructure template = createMockTemplate();
        PresentationPlan plan = createMockPlan();

        EnrichedPlan enrichedPlan = layoutAssigner.assignLayouts(template, plan);

        EnrichedSlide coverSlide = enrichedPlan.getSlides().get(0);
        assertEquals("cover", coverSlide.getType());
        assertNotNull(coverSlide.getAssignedLayout());
        assertEquals("TITLE_SLIDE", coverSlide.getAssignedLayout().getSemanticType());
        assertFalse(coverSlide.isDynamic());
    }

    @Test
    void testAssignLayoutsContentSlides() {
        TemplateStructure template = createMockTemplate();
        PresentationPlan plan = createMockPlan();

        EnrichedPlan enrichedPlan = layoutAssigner.assignLayouts(template, plan);

        EnrichedSlide contentSlide = enrichedPlan.getSlides().get(1);
        assertEquals("content", contentSlide.getType());
        assertNotNull(contentSlide.getAssignedLayout());
        assertEquals("CONTENT", contentSlide.getAssignedLayout().getSemanticType());
        assertFalse(contentSlide.isDynamic());
    }

    @Test
    void testAssignLayoutsNoLayoutsAvailable() {
        TemplateStructure template = new TemplateStructure();
        template.setLayouts(new ArrayList<>());
        template.setMetadata(createMockMetadata());
        
        PresentationPlan plan = createMockPlan();

        EnrichedPlan enrichedPlan = layoutAssigner.assignLayouts(template, plan);

        assertNotNull(enrichedPlan);
        assertEquals(plan.getSlides().size(), enrichedPlan.getDynamicSlideCount());
        
        for (EnrichedSlide slide : enrichedPlan.getSlides()) {
            assertTrue(slide.isDynamic());
            assertNull(slide.getAssignedLayout());
        }
    }

    @Test
    void testAssignLayoutsMatchScore() {
        TemplateStructure template = createMockTemplate();
        PresentationPlan plan = createMockPlan();

        EnrichedPlan enrichedPlan = layoutAssigner.assignLayouts(template, plan);

        for (EnrichedSlide slide : enrichedPlan.getSlides()) {
            if (!slide.isDynamic()) {
                assertTrue(slide.getLayoutMatchScore() > 0);
            }
        }
    }

    @Test
    void testAssignLayoutsDynamicCount() {
        TemplateStructure template = createMockTemplate();
        PresentationPlan plan = createMockPlan();

        EnrichedPlan enrichedPlan = layoutAssigner.assignLayouts(template, plan);

        enrichedPlan.updateDynamicCount();
        assertEquals(0, enrichedPlan.getDynamicSlideCount());
    }

    private TemplateStructure createMockTemplate() {
        TemplateStructure template = new TemplateStructure();
        
        List<SlideLayout> layouts = new ArrayList<>();
        
        SlideLayout titleLayout = new SlideLayout();
        titleLayout.setLayoutId("layout_title");
        titleLayout.setSemanticType("TITLE_SLIDE");
        titleLayout.setZones(createTitleZones());
        layouts.add(titleLayout);
        
        SlideLayout contentLayout1 = new SlideLayout();
        contentLayout1.setLayoutId("layout_content_1");
        contentLayout1.setSemanticType("CONTENT");
        contentLayout1.setZones(createContentZones());
        layouts.add(contentLayout1);
        
        SlideLayout contentLayout2 = new SlideLayout();
        contentLayout2.setLayoutId("layout_content_2");
        contentLayout2.setSemanticType("CONTENT");
        contentLayout2.setZones(createContentZones());
        layouts.add(contentLayout2);
        
        SlideLayout sectionLayout = new SlideLayout();
        sectionLayout.setLayoutId("layout_section");
        sectionLayout.setSemanticType("SECTION_HEADER");
        sectionLayout.setZones(createTitleZones());
        layouts.add(sectionLayout);
        
        template.setLayouts(layouts);
        template.setMetadata(createMockMetadata());
        
        return template;
    }

    private List<Zone> createTitleZones() {
        List<Zone> zones = new ArrayList<>();
        
        Zone titleZone = new Zone();
        titleZone.setZoneType("title");
        titleZone.setImportance("HIGH");
        zones.add(titleZone);
        
        Zone subtitleZone = new Zone();
        subtitleZone.setZoneType("body");
        subtitleZone.setImportance("MEDIUM");
        zones.add(subtitleZone);
        
        return zones;
    }

    private List<Zone> createContentZones() {
        List<Zone> zones = new ArrayList<>();
        
        Zone titleZone = new Zone();
        titleZone.setZoneType("title");
        titleZone.setImportance("HIGH");
        zones.add(titleZone);
        
        Zone bodyZone = new Zone();
        bodyZone.setZoneType("body");
        bodyZone.setImportance("MEDIUM");
        zones.add(bodyZone);
        
        return zones;
    }

    private PresentationPlan createMockPlan() {
        PresentationPlan plan = new PresentationPlan();
        plan.setTitle("Test Presentation");
        plan.setSlideCount(4);
        
        List<PlanSlide> slides = new ArrayList<>();
        
        PlanSlide coverSlide = new PlanSlide();
        coverSlide.setType("cover");
        coverSlide.setTitle("Introduction");
        coverSlide.setSubtitle("Welcome");
        slides.add(coverSlide);
        
        PlanSlide contentSlide1 = new PlanSlide();
        contentSlide1.setType("content");
        contentSlide1.setTitle("Point 1");
        contentSlide1.setBulletPoints(List.of("A", "B", "C"));
        slides.add(contentSlide1);
        
        PlanSlide contentSlide2 = new PlanSlide();
        contentSlide2.setType("content");
        contentSlide2.setTitle("Point 2");
        contentSlide2.setBulletPoints(List.of("X", "Y"));
        slides.add(contentSlide2);
        
        PlanSlide conclusionSlide = new PlanSlide();
        conclusionSlide.setType("conclusion");
        conclusionSlide.setTitle("Conclusion");
        conclusionSlide.setBulletPoints(List.of("Summary"));
        slides.add(conclusionSlide);
        
        plan.setSlides(slides);
        
        return plan;
    }

    private Metadata createMockMetadata() {
        Metadata metadata = new Metadata();
        metadata.setTemplateOriginalName("template_1.pptx");
        metadata.setSlideCount(10);
        metadata.setLayoutCount(4);
        return metadata;
    }
}
