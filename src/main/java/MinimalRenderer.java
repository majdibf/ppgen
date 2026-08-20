import org.docx4j.XmlUtils;
import org.docx4j.dml.*;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.PresentationML.*;
import org.pptx4j.pml.*;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class MinimalRenderer {

    // Compteur global pour garantir des ID uniques dans TOUT le fichier PPTX
    private static int globalShapeIdCounter = 1000;

    public static void main(String[] args) {
        try {
            log("=== DÉMARRAGE DU TEST RÉALISTE (TOUS LES LAYOUTS) ===");

            // 1. Charger le template
            File templateFile = new File("template_1.pptx"); // Adaptez le nom si nécessaire
            if (!templateFile.exists()) {
                log("❌ Template non trouvé : {}", templateFile.getAbsolutePath());
                return;
            }
            log("✅ Template chargé : {}", templateFile.getName());

            PresentationMLPackage pptx = PresentationMLPackage.load(templateFile);
            MainPresentationPart mainPart = pptx.getMainPresentationPart();

            // 2. Trouver tous les SlideLayoutPart
            List<SlideLayoutPart> layouts = findLayoutParts(pptx);
            log("=== Layouts trouvés : {} ===", layouts.size());

            if (layouts.isEmpty()) {
                log("❌ Aucun layout trouvé dans le template !");
                return;
            }

            // 3. Purger les slides existantes du template
            purgeExistingSlides(pptx, mainPart);
            log("✅ Slides existantes purgées");

            // 4. Boucler sur CHAQUE layout pour créer une slide de test
            for (int i = 0; i < layouts.size(); i++) {
                SlideLayoutPart layoutPart = layouts.get(i);
                String layoutName = getLayoutName(layoutPart);

                log("--------------------------------------------------");
                log("Traitement du layout [{}] : {}", i, layoutName);
                printLayoutPlaceholders(layoutPart);

                // Créer une slide basée sur ce layout
                PartName slideName = new PartName("/ppt/slides/slide" + (i + 1) + ".xml");
                SlidePart slidePart = PresentationMLPackage.createSlidePart(mainPart, layoutPart, slideName);

                // 🔑 CLÉ : Remplir TOUS les placeholders textuels de cette slide
                populateAllTextPlaceholders(slidePart, layoutPart, layoutName);

                log("  ✅ Slide {} créée et remplie avec succès", i + 1);
            }

            // 5. Sauvegarder le résultat
            File outputFile = new File("output_all_layouts_test.pptx");
            pptx.save(outputFile);

            log("==================================================");
            log("✅ Fichier sauvegardé : {}", outputFile.getAbsolutePath());
            log("👉 Ouvre le fichier dans PowerPoint.");
            log("👉 Tu devrais voir 1 slide par layout, avec le texte correctement placé et stylé.");

        } catch (Exception e) {
            log("❌ Erreur fatale: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 🔑 MÉTHODE CLÉ : Clone et remplit TOUS les placeholders textuels d'un layout
     */
    private static void populateAllTextPlaceholders(SlidePart slidePart, SlideLayoutPart layoutPart, String layoutName) throws Exception {
        SldLayout layout = layoutPart.getContents();
        if (layout.getCSld() == null || layout.getCSld().getSpTree() == null) return;

        Sld slide = slidePart.getContents();
        if (slide.getCSld() == null) slide.setCSld(new CommonSlideData());
        if (slide.getCSld().getSpTree() == null) slide.getCSld().setSpTree(new GroupShape());

        GroupShape slideSpTree = slide.getCSld().getSpTree();

        // Parcourir toutes les shapes du layout
        for (Object obj : layout.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
            if (!(obj instanceof Shape layoutShape)) continue;
            if (layoutShape.getNvSpPr() == null || layoutShape.getNvSpPr().getNvPr() == null) continue;

            CTPlaceholder ph = layoutShape.getNvSpPr().getNvPr().getPh();
            if (ph == null || ph.getType() == null) continue;

            String type = ph.getType().value();

            // On ne traite que les placeholders capables de contenir du texte
            if (type.equals("title") || type.equals("ctrTitle") || type.equals("subTitle") || type.equals("body") || type.equals("obj")) {

                // 1. Deep Copy de la shape du layout
                Shape clonedShape = (Shape) XmlUtils.deepCopy(layoutShape, org.pptx4j.jaxb.Context.jcPML);

                // 2. Assigner un ID unique global (évite les conflits dans le PPTX final)
                clonedShape.getNvSpPr().getCNvPr().setId(globalShapeIdCounter);
                clonedShape.getNvSpPr().getCNvPr().setName("Clone_" + type + "_" + globalShapeIdCounter);
                globalShapeIdCounter++;

                // 3. Injecter le texte de test selon le type de placeholder
                if (clonedShape.getTxBody() != null) {
                    clonedShape.getTxBody().getP().clear(); // Vider le texte "Cliquez pour..."

                    if (type.equals("title") || type.equals("ctrTitle")) {
                        addParagraph(clonedShape, "Titre : " + layoutName, false, 0);
                    } else if (type.equals("subTitle")) {
                        addParagraph(clonedShape, "Sous-titre ou date de la présentation", false, 0);
                    } else if (type.equals("body") || type.equals("obj")) {
                        addParagraph(clonedShape, "Premier point clé du contenu", true, 0);
                        addParagraph(clonedShape, "Deuxième élément important avec des détails", true, 0);
                        addParagraph(clonedShape, "Troisième donnée factuelle ou chiffre", true, 0);
                    }
                }

                // 4. Ajouter la shape clonée à la slide
                slideSpTree.getSpOrGrpSpOrGraphicFrame().add(clonedShape);
            }
        }
    }

    /**
     * Ajoute un paragraphe (avec ou sans puce) à une shape
     */
    private static void addParagraph(Shape shape, String text, boolean isBullet, int level) {
        CTTextParagraph p = new CTTextParagraph();

        if (isBullet) {
            CTTextParagraphProperties pPr = new CTTextParagraphProperties();
            pPr.setLvl(level); // Niveau 0 = puce standard
            p.setPPr(pPr);
        }

        CTRegularTextRun run = new CTRegularTextRun();
        run.setT(text);
        p.getEGTextRun().add(run);

        shape.getTxBody().getP().add(p);
    }

    // ============================================================
    // MÉTHODES UTILITAIRES (inchangées, elles fonctionnent bien)
    // ============================================================

    private static List<SlideLayoutPart> findLayoutParts(PresentationMLPackage pptx) {
        List<SlideLayoutPart> layouts = new ArrayList<>();
        for (Part part : pptx.getParts().getParts().values()) {
            if (part instanceof SlideLayoutPart) {
                layouts.add((SlideLayoutPart) part);
            }
        }
        return layouts;
    }

    private static String getLayoutName(SlideLayoutPart layoutPart) throws Docx4JException {
        SldLayout layout = layoutPart.getContents();
        if (layout.getCSld() != null && layout.getCSld().getName() != null) {
            return layout.getCSld().getName();
        }
        return "(sans nom)";
    }

    private static void printLayoutPlaceholders(SlideLayoutPart layoutPart) throws Docx4JException {
        SldLayout layout = layoutPart.getContents();
        if (layout.getCSld() == null || layout.getCSld().getSpTree() == null) {
            log("    (aucun spTree)");
            return;
        }

        int count = 0;
        for (Object obj : layout.getCSld().getSpTree().getSpOrGrpSpOrGraphicFrame()) {
            if (!(obj instanceof Shape shape)) continue;
            if (shape.getNvSpPr() == null || shape.getNvSpPr().getNvPr() == null) continue;

            CTPlaceholder ph = shape.getNvSpPr().getNvPr().getPh();
            if (ph == null) continue;

            String type = ph.getType() != null ? ph.getType().value() : "body";
            log("    [{}] type={}, idx={}", count++, type, ph.getIdx());
        }

        if (count == 0) {
            log("    (aucun placeholder)");
        }
    }

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

    private static void log(String format, Object... args) {
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int i = 0;
        while (i < format.length()) {
            if (i + 1 < format.length() && format.charAt(i) == '{' && format.charAt(i + 1) == '}' && argIndex < args.length) {
                sb.append(args[argIndex++]);
                i += 2;
            } else {
                sb.append(format.charAt(i));
                i++;
            }
        }
        System.out.println(sb.toString());
    }
}