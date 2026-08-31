package com.pptxgenerator.pipeline.renderer;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.pipeline.renderer.model.RenderWarning;
import org.docx4j.dml.CTNonVisualDrawingProps;
import org.docx4j.dml.CTTextBody;
import org.docx4j.dml.CTTextParagraph;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.junit.jupiter.api.Test;
import org.pptx4j.pml.CTPlaceholder;
import org.pptx4j.pml.CommonSlideData;
import org.pptx4j.pml.GroupShape;
import org.pptx4j.pml.NvPr;
import org.pptx4j.pml.STPlaceholderType;
import org.pptx4j.pml.Sld;
import org.pptx4j.pml.SldLayout;
import org.pptx4j.pml.Shape;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PlaceholderInjector}.
 *
 * <p>docx4j objects are built in memory; no {@code save()} is performed so the known
 * namespacePrefixMapper JAXB issue is never triggered.
 */
class PlaceholderInjectorTest {

    private final PlaceholderInjector injector = new PlaceholderInjector(new PlaceholderMapper());

    @Test
    void inject_emptyLayoutZones_returnsNoWarnings() throws Exception {
        // Given
        SlidePart slide = emptySlide();
        SlideLayoutPart layout = layoutWith();
        Map<String, String> zoneText = Map.of("body_0", "Hello");

        // When
        List<RenderWarning> warnings = injector.inject(slide, zoneText, layout, List.of());

        // Then
        assertThat(warnings).isEmpty();
    }

    @Test
    void inject_clonesPlaceholdersAndInjectsText() throws Exception {
        // Given
        SlidePart slide = emptySlide();
        SlideLayoutPart layout = layoutWith(placeholder(1, STPlaceholderType.BODY, 5));
        Zone zone = Zone.builder().zoneId(0).zoneType(ZoneType.BODY).idx(5L).build();
        Map<String, String> zoneText = Map.of("body_0", "Hello world");

        // When
        List<RenderWarning> warnings = injector.inject(slide, zoneText, layout, List.of(zone));

        // Then
        assertThat(warnings).isEmpty();
        assertThat(slide.getContents().getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()).hasSize(1);
        Shape injected = (Shape) slide.getContents().getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame().get(0);
        List<CTTextParagraph> paragraphs = injected.getTxBody().getP();
        assertThat(paragraphs).hasSize(1);
        assertThat(text(paragraphs.get(0))).isEqualTo("Hello world");
    }

    @Test
    void inject_zoneWithoutMatchingShape_leavesNoText() throws Exception {
        // Given
        SlidePart slide = emptySlide();
        SlideLayoutPart layout = layoutWith(placeholder(1, STPlaceholderType.TITLE, 0));
        Zone zone = Zone.builder().zoneId(0).zoneType(ZoneType.BODY).idx(5L).build();
        Map<String, String> zoneText = Map.of("body_0", "Hello");

        // When
        List<RenderWarning> warnings = injector.inject(slide, zoneText, layout, List.of(zone));

        // Then
        assertThat(warnings).isEmpty();
        Shape cloned = (Shape) slide.getContents().getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame().get(0);
        assertThat(cloned.getTxBody().getP()).isEmpty();
    }

    @Test
    void inject_bodyTextWithBullets_createsBulletParagraphs() throws Exception {
        // Given
        SlidePart slide = emptySlide();
        SlideLayoutPart layout = layoutWith(placeholder(1, STPlaceholderType.BODY, 5));
        Zone zone = Zone.builder().zoneId(0).zoneType(ZoneType.BODY).idx(5L).build();
        Map<String, String> zoneText = Map.of("body_0", "- First\n- Second");

        // When
        List<RenderWarning> warnings = injector.inject(slide, zoneText, layout, List.of(zone));

        // Then
        assertThat(warnings).isEmpty();
        Shape injected = (Shape) slide.getContents().getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame().get(0);
        List<CTTextParagraph> paragraphs = injected.getTxBody().getP();
        assertThat(paragraphs).hasSize(2);
        assertThat(text(paragraphs.get(0))).isEqualTo("First");
        assertThat(paragraphs.get(0).getPPr().getLvl()).isEqualTo(0);
    }

    private static String text(CTTextParagraph p) {
        return ((org.docx4j.dml.CTRegularTextRun) p.getEGTextRun().get(0)).getT();
    }

    private static SlidePart emptySlide() throws Exception {
        SlidePart slidePart = new SlidePart();
        Sld sld = new Sld();
        sld.setCSld(new CommonSlideData());
        slidePart.setContents(sld);
        return slidePart;
    }

    private static SlideLayoutPart layoutWith(Shape... shapes) throws Exception {
        SlideLayoutPart layoutPart = new SlideLayoutPart();
        SldLayout layout = new SldLayout();
        CommonSlideData csld = new CommonSlideData();
        GroupShape spTree = new GroupShape();
        for (Shape s : shapes) {
            spTree.getSpOrGrpSpOrGraphicFrame().add(s);
        }
        csld.setSpTree(spTree);
        layout.setCSld(csld);
        layoutPart.setContents(layout);
        return layoutPart;
    }

    private static Shape placeholder(long id, STPlaceholderType type, long idx) {
        Shape shape = new Shape();
        Shape.NvSpPr nv = new Shape.NvSpPr();
        CTNonVisualDrawingProps cNvPr = new CTNonVisualDrawingProps();
        cNvPr.setId(id);
        cNvPr.setName("ph" + id);
        nv.setCNvPr(cNvPr);
        NvPr nvPr = new NvPr();
        CTPlaceholder ph = new CTPlaceholder();
        ph.setType(type);
        ph.setIdx(idx);
        nvPr.setPh(ph);
        nv.setNvPr(nvPr);
        shape.setNvSpPr(nv);
        shape.setTxBody(new CTTextBody());
        return shape;
    }
}
