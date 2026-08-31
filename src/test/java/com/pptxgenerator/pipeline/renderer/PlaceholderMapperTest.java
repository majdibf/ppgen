package com.pptxgenerator.pipeline.renderer;

import com.pptxgenerator.model.Point;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import org.docx4j.dml.CTNonVisualDrawingProps;
import org.docx4j.dml.CTPoint2D;
import org.docx4j.dml.CTPositiveSize2D;
import org.docx4j.dml.CTShapeProperties;
import org.docx4j.dml.CTTransform2D;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.junit.jupiter.api.Test;
import org.pptx4j.pml.CTPlaceholder;
import org.pptx4j.pml.CommonSlideData;
import org.pptx4j.pml.GroupShape;
import org.pptx4j.pml.NvPr;
import org.pptx4j.pml.STPlaceholderType;
import org.pptx4j.pml.Sld;
import org.pptx4j.pml.Shape;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PlaceholderMapper}.
 *
 * <p>docx4j objects are built in memory; no {@code save()} is performed so the known
 * namespacePrefixMapper JAXB issue is never triggered.
 */
class PlaceholderMapperTest {

    private final PlaceholderMapper mapper = new PlaceholderMapper();

    @Test
    void mapPlaceholders_nullZones_returnsEmptyMap() throws Exception {
        // When
        Map<String, Shape> mapping = mapper.mapPlaceholders(slideWith(), null);

        // Then
        assertThat(mapping).isEmpty();
    }

    @Test
    void mapPlaceholders_noShapeTree_returnsEmptyMap() throws Exception {
        // Given
        SlidePart slidePart = new SlidePart();
        Sld sld = new Sld();
        sld.setCSld(new CommonSlideData());
        slidePart.setContents(sld);

        // When
        Map<String, Shape> mapping = mapper.mapPlaceholders(slidePart, List.of(zone(0, ZoneType.TITLE)));

        // Then
        assertThat(mapping).isEmpty();
    }

    @Test
    void mapPlaceholders_matchesByExactIdx() throws Exception {
        // Given
        Shape title = placeholder(1, STPlaceholderType.TITLE, 0);
        Shape body = placeholder(2, STPlaceholderType.BODY, 5);
        SlidePart slide = slideWith(title, body);
        Zone zone = Zone.builder().zoneId(0).zoneType(ZoneType.BODY).idx(5L).build();

        // When
        Map<String, Shape> mapping = mapper.mapPlaceholders(slide, List.of(zone));

        // Then
        assertThat(mapping).containsEntry("body_0", body);
    }

    @Test
    void mapPlaceholders_matchesByType_whenNoIdx() throws Exception {
        // Given
        Shape title = placeholder(1, STPlaceholderType.TITLE, 0);
        Shape body = placeholder(2, STPlaceholderType.BODY, 5);
        SlidePart slide = slideWith(title, body);
        Zone zone = Zone.builder().zoneId(0).zoneType(ZoneType.TITLE).build();

        // When
        Map<String, Shape> mapping = mapper.mapPlaceholders(slide, List.of(zone));

        // Then
        assertThat(mapping).containsEntry("title_0", title);
    }

    @Test
    void mapPlaceholders_noCompatibleType_omitsZone() throws Exception {
        // Given
        Shape title = placeholder(1, STPlaceholderType.TITLE, 0);
        SlidePart slide = slideWith(title);
        Zone zone = Zone.builder().zoneId(0).zoneType(ZoneType.BODY).build();

        // When
        Map<String, Shape> mapping = mapper.mapPlaceholders(slide, List.of(zone));

        // Then
        assertThat(mapping).doesNotContainKey("body_0");
    }

    @Test
    void mapPlaceholders_multipleSameType_picksClosestByPosition() throws Exception {
        // Given
        Shape far = placeholderAt(1, STPlaceholderType.BODY, 0, 900_000, 900_000, 100, 100);
        Shape near = placeholderAt(2, STPlaceholderType.BODY, 1, 100, 200, 300, 400);
        SlidePart slide = slideWith(far, near);

        Zone zone = Zone.builder()
            .zoneId(0)
            .zoneType(ZoneType.BODY)
            .polygon(List.of(new Point(100L, 200L), new Point(400L, 600L)))
            .build();

        // When
        Map<String, Shape> mapping = mapper.mapPlaceholders(slide, List.of(zone));

        // Then
        assertThat(mapping).containsEntry("body_0", near);
    }

    @Test
    void extractPlaceholders_keepsOnlyShapesWithPlaceholder() throws Exception {
        // Given
        Shape withPh = placeholder(1, STPlaceholderType.TITLE, 0);
        Shape withoutPh = plainShape(2, "not-a-placeholder");
        SlidePart slide = slideWith(withPh, withoutPh);

        // When
        List<Shape> placeholders = PlaceholderMapper.extractPlaceholders(slide.getContents().getCSld().getSpTree());

        // Then
        assertThat(placeholders).containsExactly(withPh);
    }

    private static SlidePart slideWith(Shape... shapes) {
        try {
            SlidePart slidePart = new SlidePart();
            Sld sld = new Sld();
            CommonSlideData csld = new CommonSlideData();
            GroupShape spTree = new GroupShape();
            spTree.getSpOrGrpSpOrGraphicFrame().addAll(List.of(shapes));
            csld.setSpTree(spTree);
            sld.setCSld(csld);
            slidePart.setContents(sld);
            return slidePart;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Shape placeholder(long id, STPlaceholderType type, long idx) {
        Shape shape = plainShape(id, "ph" + id);
        NvPr nvPr = shape.getNvSpPr().getNvPr();
        CTPlaceholder ph = new CTPlaceholder();
        ph.setType(type);
        ph.setIdx(idx);
        nvPr.setPh(ph);
        return shape;
    }

    private static Shape placeholderAt(long id, STPlaceholderType type, long idx,
                                       long x, long y, long cx, long cy) {
        Shape shape = placeholder(id, type, idx);
        CTShapeProperties spPr = new CTShapeProperties();
        CTTransform2D xfrm = new CTTransform2D();
        CTPoint2D off = new CTPoint2D();
        off.setX(x);
        off.setY(y);
        xfrm.setOff(off);
        CTPositiveSize2D ext = new CTPositiveSize2D();
        ext.setCx(cx);
        ext.setCy(cy);
        xfrm.setExt(ext);
        spPr.setXfrm(xfrm);
        shape.setSpPr(spPr);
        return shape;
    }

    private static Shape plainShape(long id, String name) {
        Shape shape = new Shape();
        Shape.NvSpPr nv = new Shape.NvSpPr();
        CTNonVisualDrawingProps cNvPr = new CTNonVisualDrawingProps();
        cNvPr.setId(id);
        cNvPr.setName(name);
        nv.setCNvPr(cNvPr);
        nv.setNvPr(new NvPr());
        shape.setNvSpPr(nv);
        return shape;
    }

    private static Zone zone(int id, ZoneType type) {
        return Zone.builder().zoneId(id).zoneType(type).build();
    }
}
