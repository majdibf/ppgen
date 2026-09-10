package com.pptxgenerator.pipeline.analyzer;

import com.pptxgenerator.pipeline.common.ooxml.OoxmlShapes;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlideMasterPart;
import org.pptx4j.pml.CTPlaceholder;
import org.pptx4j.pml.Shape;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the geometry of a layout placeholder, following the python-pptx semantics
 * used by the original POC: a placeholder without explicit {@code <a:xfrm>} inherits
 * its position/size from the matching placeholder of the slide master.
 *
 * <p>Matching rules (mirroring python-pptx placeholder inheritance):
 * <ul>
 *   <li>placeholders typed {@code title/ctrTitle/subTitle/dt/ftr/sldNum/hdr} match by
 *       placeholder type (they have no exploitable {@code idx});</li>
 *   <li>body-family placeholders ({@code body/obj}) match preferentially by {@code idx},
 *       falling back to the first matching type.</li>
 * </ul>
 *
 * <p>Keeping this resolution in a single class removes the need for the former
 * "explicit geometry only" filter in {@link TemplateAnalyzer#identifyZones}, so layout
 * placeholders inherited from the master no longer silently disappear from the analysis.
 */
@Slf4j
@ApplicationScoped
public class InheritedGeometryResolver {

    /**
     * Resolved position/size of a shape (in EMU).
     */
    public record Geometry(long x, long y, long width, long height) {
    }

    /**
     * Returns the geometry to use for a layout placeholder: its own explicit geometry
     * when present, otherwise the geometry inherited from the slide master.
     *
     * @return empty when the placeholder has no explicit geometry and no master counterpart
     */
    public Optional<Geometry> resolve(SlideLayoutPart layoutPart, Shape shape) {
        if (OoxmlShapes.hasExplicitGeometry(shape)) {
            return OoxmlShapes.geometryOf(shape).map(g -> new Geometry(g.x(), g.y(), g.width(), g.height()));
        }

        SlideMasterPart masterPart = layoutPart.getSlideMasterPart();
        if (masterPart == null) {
            log.debug("Layout '{}' has no slide master part; cannot inherit geometry.",
                    layoutPart.getPartName());
            return Optional.empty();
        }

        CTPlaceholder placeholder = OoxmlShapes.placeholderOf(shape);
        if (placeholder == null) {
            return Optional.empty();
        }

        return matchMasterGeometry(masterPart, shape, placeholder);
    }

    private Optional<Geometry> matchMasterGeometry(SlideMasterPart masterPart, Shape layoutShape,
                                                   CTPlaceholder layoutPlaceholder) {
        try {
            List<Shape> masterPlaceholders = masterPlaceholders(masterPart);
            String layoutType = OoxmlShapes.typeOf(layoutShape);
            Long layoutIdx = OoxmlShapes.idxOf(layoutShape);

            // Preferred candidates: same placeholder type (with the ctrTitle/title
            // and subtitle/body equivalences PowerPoint uses when inheriting).
            List<Shape> sameFamily = masterPlaceholders.stream()
                    .filter(candidate -> isSameFamily(layoutType, OoxmlShapes.typeOf(candidate)))
                    .toList();

            // Body-family placeholders are disambiguated by idx when available.
            if (layoutIdx != null) {
                Optional<Shape> byIdx = sameFamily.stream()
                        .filter(candidate -> layoutIdx.equals(OoxmlShapes.idxOf(candidate)))
                        .findFirst();
                if (byIdx.isPresent()) {
                    return geometryOf(byIdx.get());
                }
            }

            return sameFamily.stream().findFirst().flatMap(this::geometryOf);
        } catch (Docx4JException e) {
            log.debug("Master geometry lookup failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Placeholder types sharing the same master geometry slot. OOXML layouts rarely
     * contain literal ctrTitle/subTitle placeholders themselves, but when they do,
     * they inherit from the master {@code title}/{@code body} placeholder.
     */
    private boolean isSameFamily(String layoutType, String masterType) {
        if (layoutType == null || masterType == null) {
            return false;
        }
        return switch (layoutType) {
            case "title", "ctrTitle" -> masterType.equals("title") || masterType.equals("ctrTitle");
            case "subTitle", "body", "obj" -> masterType.equals("body") || masterType.equals("obj")
                    || masterType.equals("subTitle");
            case "pic" -> masterType.equals("pic");
            case "chart" -> masterType.equals("chart");
            case "tbl" -> masterType.equals("tbl");
            case "hdr" -> masterType.equals("hdr");
            case "ftr" -> masterType.equals("ftr");
            case "sldNum" -> masterType.equals("sldNum");
            case "dt" -> masterType.equals("dt");
            default -> layoutType.equals(masterType);
        };
    }

    private List<Shape> masterPlaceholders(SlideMasterPart masterPart) throws Docx4JException {
        var master = masterPart.getContents();
        if (master == null || master.getCSld() == null) {
            return List.of();
        }
        return OoxmlShapes.placeholderShapesIn(master.getCSld().getSpTree());
    }

    private Optional<Geometry> geometryOf(Shape shape) {
        return OoxmlShapes.geometryOf(shape).map(g -> new Geometry(g.x(), g.y(), g.width(), g.height()));
    }
}
