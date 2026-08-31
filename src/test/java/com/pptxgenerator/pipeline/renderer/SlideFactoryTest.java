package com.pptxgenerator.pipeline.renderer;

import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.junit.jupiter.api.Test;
import org.pptx4j.model.SlideSizesWellKnown;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SlideFactory}.
 *
 * <p>Slides are created and inspected in memory; no {@code save()} is performed so the known
 * namespacePrefixMapper JAXB issue is never triggered.
 */
class SlideFactoryTest {

    private final SlideFactory factory = new SlideFactory();

    @Test
    void createSlide_attachesNewSlideToMainPart() throws Exception {
        // Given
        PresentationMLPackage pptx = PresentationMLPackage.createPackage(SlideSizesWellKnown.SCREEN16x9, true);
        SlideLayoutPart layout = defaultLayout(pptx);
        int initial = pptx.getMainPresentationPart().getContents().getSldIdLst().getSldId().size();

        // When
        SlidePart slide = factory.createSlide(pptx, layout, 0);

        // Then
        assertThat(slide).isNotNull();
        assertThat(slide.getPartName().getName()).isEqualTo("/ppt/slides/slide1.xml");
        assertThat(pptx.getMainPresentationPart().getContents().getSldIdLst().getSldId())
                .hasSize(initial + 1);
    }

    @Test
    void purgeExistingSlides_removesCreatedSlides() throws Exception {
        // Given
        PresentationMLPackage pptx = PresentationMLPackage.createPackage(SlideSizesWellKnown.SCREEN16x9, true);
        SlideLayoutPart layout = defaultLayout(pptx);
        factory.createSlide(pptx, layout, 0);
        factory.createSlide(pptx, layout, 1);

        // When
        factory.purgeExistingSlides(pptx);

        // Then
        assertThat(pptx.getMainPresentationPart().getContents().getSldIdLst().getSldId())
                .isEmpty();
    }

    @Test
    void purgeExistingSlides_noSlides_succeeds() throws Exception {
        // Given
        PresentationMLPackage pptx = PresentationMLPackage.createPackage(SlideSizesWellKnown.SCREEN16x9, true);

        // When
        factory.purgeExistingSlides(pptx);

        // Then
        assertThat(pptx.getMainPresentationPart().getContents().getSldIdLst().getSldId())
                .isEmpty();
    }

    private static SlideLayoutPart defaultLayout(PresentationMLPackage pptx) {
        return pptx.getParts().getParts().values().stream()
                .filter(SlideLayoutPart.class::isInstance)
                .map(SlideLayoutPart.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
