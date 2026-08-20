package com.pptxgenerator.analyzer;

import com.pptxgenerator.model.Point;
import com.pptxgenerator.model.SlideDimensions;
import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.pptx4j.pml.CTPlaceholder;
import org.pptx4j.pml.STPlaceholderType;
import org.pptx4j.pml.Shape;
import org.pptx4j.pml.SldLayout;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@ApplicationScoped
public class ZoneIdentifier {

    /**
     * Identifie toutes les zones d'un layout
     */
    public List<Zone> identify(SlideLayoutPart layoutPart, SlideDimensions dimensions) throws Docx4JException {
        List<Zone> zones = new ArrayList<>();
        int zoneId = 0;

        SldLayout layout = layoutPart.getContents();
        if (layout.getCSld() == null || layout.getCSld().getSpTree() == null) {
            return zones;
        }

        for (Object shapeObj : layout.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
            if (!(shapeObj instanceof Shape shape)) continue;

            if (shape.getNvSpPr() == null || shape.getNvSpPr().getNvPr() == null) continue;
            CTPlaceholder placeholder = shape.getNvSpPr().getNvPr().getPh();
            if (placeholder == null) continue;

            if (!hasExplicitGeometry(shape)) {
                // Placeholder sans xfrm explicite (position héritée du master, ex: sldNum/ftr/dt)
                log.debug("Placeholder de type {} sans géométrie explicite, zone ignorée",
                    placeholder.getType() != null ? placeholder.getType().value() : "inconnu");
                continue;
            }

            ZoneType zoneType = classifyZone(placeholder, shape, dimensions);
            String position = calculatePosition(shape, dimensions);
            double surfacePercentage = calculateSurfacePercentage(shape, dimensions);

            // Construire le polygone (rectangle)
            long x = shape.getSpPr().getXfrm().getOff().getX();
            long y = shape.getSpPr().getXfrm().getOff().getY();
            long width = shape.getSpPr().getXfrm().getExt().getCx();
            long height = shape.getSpPr().getXfrm().getExt().getCy();

            List<Point> polygon = List.of(
                new Point(x, y),
                new Point(x + width, y),
                new Point(x + width, y + height),
                new Point(x, y + height)
            );

            Zone zone = Zone.builder()
                .zoneId(zoneId++)
                .zoneType(zoneType)
                .width(width)
                .height(height)
                .polygon(polygon)
                .surfacePercentage(Math.round(surfacePercentage * 10.0) / 10.0)
                .zIndex(zoneId - 1)
                .position(position)
                .build();

            zones.add(zone);
        }

        return zones;
    }

    private ZoneType classifyZone(CTPlaceholder placeholder, Shape shape, SlideDimensions dimensions) {
        STPlaceholderType type = placeholder.getType();

        // Mapping direct des types connus
        if (type != null) {
            String typeValue = type.value();
            return switch (typeValue) {
                case "title" -> ZoneType.TITLE;
                case "ctrTitle" -> ZoneType.CENTER_TITLE;
                case "subTitle" -> ZoneType.SUBTITLE;
                case "body" -> classifyBySize(shape, dimensions);
                case "obj" -> classifyBySize(shape, dimensions);
                case "pic" -> ZoneType.PICTURE;
                case "chart" -> ZoneType.CHART;
                case "tbl" -> ZoneType.TABLE;
                case "hdr" -> ZoneType.HEADER;
                case "ftr" -> ZoneType.FOOTER;
                case "sldNum" -> ZoneType.SLIDE_NUMBER;
                case "dt" -> ZoneType.DATE;
                default -> ZoneType.UNKNOWN;
            };
        }

        return ZoneType.BODY; // Défaut
    }

    private ZoneType classifyBySize(Shape shape, SlideDimensions dimensions) {
        long height = shape.getSpPr().getXfrm().getExt().getCy();
        long width = shape.getSpPr().getXfrm().getExt().getCx();

        double estimatedLines = height / 400000.0; // ~400k EMU par ligne
        double surfacePercentage = calculateSurfacePercentage(shape, dimensions);
        double widthPercentage = (width / (double) dimensions.getWidth()) * 100;

        if (estimatedLines > 1.5) {
            return surfacePercentage < 5 ? (widthPercentage >= 15 ? ZoneType.LINE : ZoneType.WORD) : ZoneType.BODY;
        } else {
            return widthPercentage >= 15 ? ZoneType.LINE : ZoneType.WORD;
        }
    }

    private String calculatePosition(Shape shape, SlideDimensions dimensions) {
        long centerX = shape.getSpPr().getXfrm().getOff().getX() + shape.getSpPr().getXfrm().getExt().getCx() / 2;
        long centerY = shape.getSpPr().getXfrm().getOff().getY() + shape.getSpPr().getXfrm().getExt().getCy() / 2;

        String vertical = centerY < dimensions.getHeight() * 0.33 ? "top" :
                         centerY > dimensions.getHeight() * 0.67 ? "bottom" : "middle";
        String horizontal = centerX < dimensions.getWidth() * 0.33 ? "left" :
                           centerX > dimensions.getWidth() * 0.67 ? "right" : "center";

        return vertical + "_" + horizontal;
    }

    private double calculateSurfacePercentage(Shape shape, SlideDimensions dimensions) {
        long zoneSurface = shape.getSpPr().getXfrm().getExt().getCx() * shape.getSpPr().getXfrm().getExt().getCy();
        long totalSurface = dimensions.getWidth() * dimensions.getHeight();
        return (zoneSurface / (double) totalSurface) * 100;
    }

    /**
     * Certains placeholders (souvent sldNum/ftr/dt) n'ont pas de xfrm propre :
     * leur position/taille est héritée du slide master, pas du layout lui-même.
     */
    private boolean hasExplicitGeometry(Shape shape) {
        return shape.getSpPr() != null
            && shape.getSpPr().getXfrm() != null
            && shape.getSpPr().getXfrm().getOff() != null
            && shape.getSpPr().getXfrm().getExt() != null;
    }
}
