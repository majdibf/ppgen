package com.pptxgenerator.renderer.building;

import com.pptxgenerator.model.LayoutAnalysis;
import com.pptxgenerator.model.TemplateAnalysis;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;

import java.util.Optional;

/**
 * Résout un layout_id en SlideLayoutPart du template.
 */
@Slf4j
@ApplicationScoped
public class LayoutResolver {

    /**
     * Trouve le SlideLayoutPart correspondant au layout_id.
     */
    public Optional<SlideLayoutPart> resolve(PresentationMLPackage pptx, String layoutId, TemplateAnalysis analysis) throws Docx4JException {
        // Trouver le layout dans l'analyse
        Optional<LayoutAnalysis> layoutAnalysis = analysis.getLayouts().stream()
            .filter(l -> l.getLayoutId().equals(layoutId))
            .findFirst();

        if (layoutAnalysis.isEmpty()) {
            log.warn("Layout {} non trouvé dans l'analyse", layoutId);
            return Optional.empty();
        }

        String originalName = layoutAnalysis.get().getOriginalName();

        // Trouver le SlideLayoutPart dans le package
        for (Part part : pptx.getParts().getParts().values()) {
            if (part instanceof SlideLayoutPart layoutPart) {
                String layoutName = layoutPart.getContents().getCSld().getName();
                if (layoutName.equals(originalName)) {
                    return Optional.of(layoutPart);
                }
            }
        }

        return Optional.empty();
    }
}
