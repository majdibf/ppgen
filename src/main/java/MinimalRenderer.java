import io.quarkus.runtime.annotations.QuarkusMain;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.dml.*;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.PresentationML.MainPresentationPart;
import org.docx4j.openpackaging.parts.PresentationML.SlideLayoutPart;
import org.docx4j.openpackaging.parts.PresentationML.SlideMasterPart;
import org.docx4j.openpackaging.parts.PresentationML.SlidePart;
import org.pptx4j.pml.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@QuarkusMain
public class MinimalRenderer {

    public static void main(String[] args) throws Exception {
        // 1. Charger le template
        File templateFile = new File("template_1.pptx");
        PresentationMLPackage pptx = PresentationMLPackage.load(templateFile);
        MainPresentationPart mainPart = pptx.getMainPresentationPart();

        // 2. 🔑 VRAIE API : récupérer les SlideLayoutPart via le package
        List<SlideLayoutPart> layouts = findLayoutParts(pptx);
        log.info("=== Layouts trouvés : {} ===", layouts.size());
        for (int i = 0; i < layouts.size(); i++) {
            SlideLayoutPart lp = layouts.get(i);
            log.info("  [{}] {} → {}", i, lp.getPartName(), getLayoutName(lp));
            printLayoutPlaceholders(lp);
        }

        // 3. Purger les slides existantes
        purgeExistingSlides(pptx, mainPart);

        // 4. Choisir un layout (le premier pour tester)
        if (layouts.isEmpty()) {
            log.error("Aucun layout trouvé dans le template !");
            return;
        }
        SlideLayoutPart layoutPart = layouts.get(0);

        // 5. Créer une slide depuis le layout
        PartName slideName = new PartName("/ppt/slides/slide1.xml");
        SlidePart slidePart = PresentationMLPackage.createSlidePart(
                mainPart, layoutPart, slideName
        );
        log.info("Slide créée : {}", slidePart.getPartName());

        // 6. 🔑 CRÉER UNE SHAPE OVERRIDE pour le titre
        createTitleOverride(slidePart, layoutPart, "Mon titre de test");

        // 7. Sauvegarder
        File outputFile = new File("output_test.pptx");
        pptx.save(outputFile);
        log.info("✅ Fichier sauvegardé : {}", outputFile.getAbsolutePath());
    }

    /**
     * 🔑 VRAIE API : Trouver tous les SlideLayoutPart dans le package
     */
    private static List<SlideLayoutPart> findLayoutParts(PresentationMLPackage pptx) {
        List<SlideLayoutPart> layouts = new ArrayList<>();

        for (Part part : pptx.getParts().getParts().values()) {
            if (part instanceof SlideLayoutPart) {
                layouts.add((SlideLayoutPart) part);
            }
        }

        return layouts;
    }

    /**
     * 🔑 VRAIE API : Trouver tous les SlideMasterPart
     */
    private static List<SlideMasterPart> findMasterParts(PresentationMLPackage pptx) {
        List<SlideMasterPart> masters = new ArrayList<>();

        for (Part part : pptx.getParts().getParts().values()) {
            if (part instanceof SlideMasterPart) {
                masters.add((SlideMasterPart) part);
            }
        }

        return masters;
    }

    /**
     * Récupère le nom d'un layout
     */
    private static String getLayoutName(SlideLayoutPart layoutPart) throws Docx4JException {
        SldLayout layout = layoutPart.getContents();
        if (layout.getCSld() != null && layout.getCSld().getName() != null) {
            return layout.getCSld().getName();
        }
        return "(sans nom)";
    }

    /**
     * Affiche les placeholders d'un layout
     */
    private static void printLayoutPlaceholders(SlideLayoutPart layoutPart) throws Docx4JException {
        SldLayout layout = layoutPart.getContents();
        if (layout.getCSld() == null || layout.getCSld().getSpTree() == null) {
            log.info("    (aucun spTree)");
            return;
        }

        int count = 0;
        for (Object obj : layout.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
            if (!(obj instanceof Shape shape)) continue;
            if (shape.getNvSpPr() == null || shape.getNvSpPr().getNvPr() == null) continue;

            CTPlaceholder ph = shape.getNvSpPr().getNvPr().getPh();
            if (ph == null) continue;

            String type = ph.getType() != null ? ph.getType().value() : "body";
            log.info("    [{}] type={}, idx={}", count++, type, ph.getIdx());
        }

        if (count == 0) {
            log.info("    (aucun placeholder)");
        }
    }

    /**
     * 🔑 CRÉER UNE SHAPE OVERRIDE pour le titre
     */
    private static void createTitleOverride(SlidePart slidePart, SlideLayoutPart layoutPart, String titleText) throws Docx4JException {
        // 1. Trouver le placeholder TITLE du layout
        CTPlaceholder layoutTitlePh = findPlaceholderByType(layoutPart, "title");
        if (layoutTitlePh == null) {
            log.error("❌ Aucun placeholder TITLE trouvé dans le layout !");
            return;
        }
        log.info("✅ Placeholder TITLE trouvé : type={}, idx={}",
                layoutTitlePh.getType(), layoutTitlePh.getIdx());

        // 2. Créer la shape override
        Shape overrideShape = new Shape();

        // 2a. Non-visual properties avec le MÊME placeholder (type + idx)
        Shape.NvSpPr nvSpPr = new Shape.NvSpPr();

        CTNonVisualDrawingProps cNvPr = new CTNonVisualDrawingProps();
        cNvPr.setId(100L);
        cNvPr.setName("Title Override 1");
        nvSpPr.setCNvPr(cNvPr);

        nvSpPr.setCNvSpPr(new CTNonVisualDrawingShapeProps());

        NvPr nvPr = new NvPr();
        CTPlaceholder ph = new CTPlaceholder();
        ph.setType(layoutTitlePh.getType());   // ← MÊME TYPE
        if (layoutTitlePh.getIdx() != 0L) {
            ph.setIdx(layoutTitlePh.getIdx());  // ← MÊME IDX
        }
        nvPr.setPh(ph);
        nvSpPr.setNvPr(nvPr);

        overrideShape.setNvSpPr(nvSpPr);

        // 2b. Text body avec le contenu
        CTTextBody txBody = new CTTextBody();
        txBody.setBodyPr(new CTTextBodyProperties());
        txBody.setLstStyle(new CTTextListStyle());

        CTTextParagraph paragraph = new CTTextParagraph();
        CTRegularTextRun run = new CTRegularTextRun();
        run.setT(titleText);
        paragraph.getEGTextRun().add(run);
        txBody.getP().add(paragraph);

        overrideShape.setTxBody(txBody);

        // 3. Ajouter au spTree de la slide
        Sld slide = slidePart.getContents();
        if (slide.getCSld() == null) {
            slide.setCSld(new CommonSlideData());
        }
        if (slide.getCSld().getSpTree() == null) {
            slide.getCSld().setSpTree(new GroupShape());
        }

        slide.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame().add(overrideShape);
        log.info("✅ Shape override ajoutée à la slide");
    }

    /**
     * Trouve un placeholder par type dans un layout
     */
    private static CTPlaceholder findPlaceholderByType(SlideLayoutPart layoutPart, String targetType) throws Docx4JException {
        SldLayout layout = layoutPart.getContents();
        if (layout.getCSld() == null || layout.getCSld().getSpTree() == null) {
            return null;
        }

        for (Object obj : layout.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
            if (!(obj instanceof Shape shape)) continue;
            if (shape.getNvSpPr() == null || shape.getNvSpPr().getNvPr() == null) continue;

            CTPlaceholder ph = shape.getNvSpPr().getNvPr().getPh();
            if (ph == null) continue;

            String type = ph.getType() != null ? ph.getType().value() : "body";
            log.debug("  Placeholder : type={}, idx={}", type, ph.getIdx());

            if (targetType.equals(type)) {
                return ph;
            }
        }
        return null;
    }

    /**
     * 🔑 VRAIE API : Purger les slides existantes
     */
    private static void purgeExistingSlides(PresentationMLPackage pptx, MainPresentationPart mainPart) throws Exception {
        Presentation presentation = mainPart.getContents();
        if (presentation.getSldIdLst() == null || presentation.getSldIdLst().getSldId() == null) {
            return;
        }

        List<Presentation.SldIdLst.SldId> slidesToRemove = new ArrayList<>(presentation.getSldIdLst().getSldId());
        var relationships = mainPart.getRelationshipsPart();

        for (Presentation.SldIdLst.SldId slideId : slidesToRemove) {
            if (slideId.getRid() == null) continue;
            var rel = relationships.getRelationshipByID(slideId.getRid());
            if (rel == null) continue;
            var part = relationships.getPart(rel);
            if (part instanceof SlidePart) {
                pptx.getParts().remove(part.getPartName());
                relationships.removeRelationship(rel);
            }
        }
        presentation.getSldIdLst().getSldId().clear();
    }
}