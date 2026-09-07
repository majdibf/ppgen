package com.pptxgenerator.pipeline.renderer;

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

    private final PlaceholderMapper mapper = new PlaceholderMapper(new OoxmlHelper());
    private final OoxmlHelper ooxml = new OoxmlHelper();

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
    void mapPlaceholders_matchesByIdxAndType() throws Exception {
        // Given: 1 zone, 1 shape placeholder, même idx
        Shape body = placeholder(1, STPlaceholderType.BODY, 5);
        SlidePart slide = slideWith(body);
        Zone zone = Zone.builder().zoneId(0).zoneType(ZoneType.BODY).idx(5L).build();

        // When
        Map<String, Shape> mapping = mapper.mapPlaceholders(slide, List.of(zone));

        // Then: la zone est mappée sur le placeholder de même idx et de type compatible
        assertThat(mapping).containsEntry("body_0", body);
    }

    @Test
    void mapPlaceholders_matchesEachZoneToItsOwnPlaceholder() throws Exception {
        // Given: 3 zones, 3 placeholders (l'ordre des listes est volontairement inversé)
        Shape title = placeholder(1, STPlaceholderType.TITLE, 0);
        Shape body = placeholder(2, STPlaceholderType.BODY, 5);
        Shape note = placeholder(3, STPlaceholderType.BODY, 6);
        SlidePart slide = slideWith(title, body, note);

        Zone titleZone = Zone.builder().zoneId(0).zoneType(ZoneType.TITLE).idx(0L).build();
        Zone bodyZone = Zone.builder().zoneId(1).zoneType(ZoneType.BODY).idx(5L).build();
        Zone noteZone = Zone.builder().zoneId(2).zoneType(ZoneType.LINE).idx(6L).build();

        // When
        Map<String, Shape> mapping = mapper.mapPlaceholders(slide, List.of(noteZone, titleZone, bodyZone));

        // Then: jointure par idx/type, chaque placeholder est utilisé une seule fois
        assertThat(mapping).containsEntry("title_0", title);
        assertThat(mapping).containsEntry("body_1", body);
        assertThat(mapping).containsEntry("line_2", note);
    }

    @Test
    void mapPlaceholders_typeMismatch_neverCrossMaps() throws Exception {
        // Given: 1 zone BODY idx=5 mais seul un placeholder TITLE est disponible
        Shape title = placeholder(1, STPlaceholderType.TITLE, 5);
        SlidePart slide = slideWith(title);
        Zone bodyZone = Zone.builder().zoneId(0).zoneType(ZoneType.BODY).idx(5L).build();

        // When
        Map<String, Shape> mapping = mapper.mapPlaceholders(slide, List.of(bodyZone));

        // Then: aucun cross-mapping silencieux, même à idx identique
        assertThat(mapping).isEmpty();
    }

    @Test
    void mapPlaceholders_extraPlaceholder_neverUsedByMismatchedZone() throws Exception {
        // Given: 1 zone BODY idx=5, 2 placeholders dont un title — cas où les filtres
        // M1/M5 divergent : le mapping ne doit pas se décaler
        Shape title = placeholder(1, STPlaceholderType.TITLE, 0);
        Shape body = placeholder(2, STPlaceholderType.BODY, 5);
        SlidePart slide = slideWith(title, body);
        Zone bodyZone = Zone.builder().zoneId(0).zoneType(ZoneType.BODY).idx(5L).build();

        // When
        Map<String, Shape> mapping = mapper.mapPlaceholders(slide, List.of(bodyZone));

        // Then: la zone est mappée sur le body (identité), pas sur le 1er placeholder
        assertThat(mapping).containsEntry("body_0", body);
        assertThat(mapping).hasSize(1);
    }

    @Test
    void mapPlaceholders_moreZonesThanShapes_unmatchedZonesWarning() throws Exception {
        // Given: 3 zones, 1 shape
        Shape body = placeholder(1, STPlaceholderType.BODY, 5);
        SlidePart slide = slideWith(body);
        Zone z0 = Zone.builder().zoneId(0).zoneType(ZoneType.TITLE).idx(0L).build();
        Zone z1 = Zone.builder().zoneId(1).zoneType(ZoneType.BODY).idx(5L).build();
        Zone z2 = Zone.builder().zoneId(2).zoneType(ZoneType.LINE).idx(6L).build();

        // When
        Map<String, Shape> mapping = mapper.mapPlaceholders(slide, List.of(z0, z1, z2));

        // Then: seule la zone d'identité compatible est mappée ; title_0 et line_2
        // restent non mappées (warning loggué) — elles n'écrasent jamais le body.
        assertThat(mapping).hasSize(1);
        assertThat(mapping).containsEntry("body_1", body);
    }

    @Test
    void extractPlaceholders_keepsOnlyShapesWithPlaceholder() throws Exception {
        // Given
        Shape withPh = placeholder(1, STPlaceholderType.TITLE, 0);
        Shape withoutPh = plainShape(2, "not-a-placeholder");
        SlidePart slide = slideWith(withPh, withoutPh);

        // When
        List<Shape> placeholders = ooxml.extractPlaceholders(slide.getContents().getCSld().getSpTree());

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
