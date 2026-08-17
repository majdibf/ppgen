package com.pptxgenerator.renderer;

import com.pptxgenerator.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class PresentationRendererTest {

    private PresentationRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new PresentationRenderer();
    }

    @Test
    void testRendererCanBeInstantiated() {
        assertNotNull(renderer);
    }

    @Test
    void testRenderWithNullParametersThrowsException() {
        assertThrows(Exception.class, () -> {
            renderer.render(null, null, null, null, "output.pptx");
        });
    }

    @Test
    void testRenderWithEmptyContentMap() throws Exception {
        TemplateStructure template = createMockTemplate();
        EnrichedPlan plan = createMockEnrichedPlan();
        ContentMap contentMap = new ContentMap("Test");
        
        // Note: This test verifies the method signature and basic flow
        // The renderer handles empty content gracefully
        assertDoesNotThrow(() -> {
            renderer.render("template_1.pptx", template, plan, contentMap, "target/test-output.pptx");
        });
    }

    @Test
    void testRenderInheritsRealLayoutShapes() throws Exception {
        com.pptxgenerator.analyzer.TemplateAnalyzer analyzer = new com.pptxgenerator.analyzer.TemplateAnalyzer();
        TemplateStructure template = analyzer.analyze("template_1.pptx");
        SlideLayout layout = template.getLayouts().stream()
            .filter(candidate -> candidate.getZones() != null && !candidate.getZones().isEmpty())
            .findFirst().orElseThrow();

        EnrichedSlide slide = new EnrichedSlide();
        slide.setTitle("Inherited layout test");
        slide.setAssignedLayout(layout);
        EnrichedPlan plan = new EnrichedPlan();
        plan.setTitle("Renderer test");
        plan.setSlides(List.of(slide));

        SlideContent slideContent = new SlideContent();
        slideContent.setSlideId("slide_0");
        List<ZoneContent> contents = new ArrayList<>();
        ZoneContent title = new ZoneContent();
        title.setZoneId(layout.getZones().get(0).getZoneId());
        title.setZoneType(layout.getZones().get(0).getZoneType());
        title.setContent("Inherited layout test");
        contents.add(title);
        slideContent.setZoneContents(contents);
        ContentMap contentMap = new ContentMap("Renderer test");
        contentMap.addSlideContent("slide_0", slideContent);

        File output = renderer.render("template_1.pptx", template, plan, contentMap,
            "target/inherited-layout-test.pptx");
        assertTrue(output.exists());
        assertTrue(output.length() > 0);
    }

    private TemplateStructure createMockTemplate() {
        TemplateStructure template = new TemplateStructure();
        
        Metadata metadata = new Metadata();
        metadata.setTemplateOriginalName("template_1.pptx");
        metadata.setSlideCount(10);
        metadata.setLayoutCount(4);
        template.setMetadata(metadata);
        
        List<SlideLayout> layouts = new ArrayList<>();
        
        SlideLayout titleLayout = new SlideLayout();
        titleLayout.setLayoutId("layout_title");
        titleLayout.setSemanticType("TITLE_SLIDE");
        titleLayout.setOriginalName("/ppt/slideLayouts/slideLayout1.xml");
        layouts.add(titleLayout);
        
        SlideLayout contentLayout = new SlideLayout();
        contentLayout.setLayoutId("layout_content");
        contentLayout.setSemanticType("CONTENT");
        contentLayout.setOriginalName("/ppt/slideLayouts/slideLayout2.xml");
        layouts.add(contentLayout);
        
        template.setLayouts(layouts);
        
        return template;
    }

    private EnrichedPlan createMockEnrichedPlan() {
        EnrichedPlan plan = new EnrichedPlan();
        plan.setTitle("Test Presentation");
        
        List<EnrichedSlide> slides = new ArrayList<>();
        
        EnrichedSlide slide1 = new EnrichedSlide();
        slide1.setTitle("Introduction");
        slide1.setType("cover");
        SlideLayout titleLayout = new SlideLayout();
        titleLayout.setLayoutId("layout_title");
        titleLayout.setSemanticType("TITLE_SLIDE");
        slide1.setAssignedLayout(titleLayout);
        slides.add(slide1);
        
        EnrichedSlide slide2 = new EnrichedSlide();
        slide2.setTitle("Content");
        slide2.setType("content");
        SlideLayout contentLayout = new SlideLayout();
        contentLayout.setLayoutId("layout_content");
        contentLayout.setSemanticType("CONTENT");
        slide2.setAssignedLayout(contentLayout);
        slides.add(slide2);
        
        plan.setSlides(slides);
        plan.setSlideCount(slides.size());
        
        return plan;
    }
}
