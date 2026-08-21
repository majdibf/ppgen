import org.apache.poi.sl.usermodel.Placeholder;  // ← IMPORT IMPORTANT
import org.apache.poi.xslf.usermodel.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;

public class PoiRealisticRenderer {

    public static void main(String[] args) {
        try {
            System.out.println("=== TEST AVEC APACHE POI (100% Open Source) ===");

            File templateFile = new File("template_1.pptx");
            if (!templateFile.exists()) {
                System.out.println("❌ Template non trouvé : " + templateFile.getAbsolutePath());
                return;
            }

            // 1. Charger la présentation
            XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(templateFile));
            System.out.println("✅ Template chargé. Layouts disponibles : "
                    + ppt.getSlideMasters().get(0).getSlideLayouts().length);

            // 2. Purger les slides existantes du template
            while (ppt.getSlides().size() > 0) {
                ppt.removeSlide(0);
            }
            System.out.println("✅ Slides existantes purgées.");

            XSLFSlideMaster master = ppt.getSlideMasters().get(0);

            // 3. Boucler sur CHAQUE layout
            for (XSLFSlideLayout layout : master.getSlideLayouts()) {
                String layoutName = layout.getName();

                // 🔧 CORRECTION 1 : Filtre simplifié (juste par nom, pas de LayoutType)
                if (layoutName == null || layoutName.trim().isEmpty()) {
                    continue;
                }

                System.out.println("--------------------------------------------------");
                System.out.println("Traitement du layout : " + layoutName);

                // Créer une slide basée sur ce layout
                // POI copie AUTOMATIQUEMENT les placeholders du layout
                XSLFSlide slide = ppt.createSlide(layout);
                System.out.println("  ✅ Slide créée à partir du layout.");

                // 4. Parcourir les formes de la slide
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        Placeholder ph = textShape.getPlaceholder();
                        if (ph == null) continue;

                        String phType = ph.name();
                        System.out.println("    → Trouvé placeholder : " + phType);

                        // Vider le texte par défaut ("Cliquez pour ajouter...")
                        textShape.clearText();

                        // Titre
                        if (ph == Placeholder.TITLE || ph == Placeholder.CENTERED_TITLE) {
                            addParagraph(textShape, "Titre : " + layoutName, false);
                        }
                        // Sous-titre
                        else if (ph == Placeholder.SUBTITLE) {
                            addParagraph(textShape, "Sous-titre ou date de la présentation", false);
                        }
                        // Body / Contenu
                        else if (ph == Placeholder.BODY || ph == Placeholder.VERTICAL_OBJECT) {
                            addParagraph(textShape, "Premier point clé du contenu", true);
                            addParagraph(textShape, "Deuxième élément important avec des détails", true);
                            addParagraph(textShape, "Troisième donnée factuelle ou chiffre", true);
                        }
                        // 🔑 MÉDIAS : On ne touche PAS au texte, l'icône native reste
                        else if (ph == Placeholder.PICTURE || ph == Placeholder.CHART || ph == Placeholder.TABLE) {
                            System.out.println("    → Zone média " + phType + " préservée (icône native)");
                            // textShape.clearText() a déjà vidé le placeholder, c'est tout
                        }
                    }
                }
                System.out.println("  ✅ Slide remplie avec succès");
            }

            // 6. Sauvegarder le résultat
            File outputFile = new File("output_poi_test.pptx");
            ppt.write(new FileOutputStream(outputFile));
            ppt.close();

            System.out.println("==================================================");
            System.out.println("✅ Fichier sauvegardé : " + outputFile.getAbsolutePath());
            System.out.println("👉 Ouvre le fichier dans PowerPoint.");

        } catch (Exception e) {
            System.out.println("❌ Erreur fatale : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ajoute un paragraphe (avec ou sans puce)
     */
    private static void addParagraph(XSLFTextShape shape, String text, boolean isBullet) {
        XSLFTextParagraph para = shape.addNewTextParagraph();

        if (isBullet) {
            para.setIndentLevel(1);
        }

        XSLFTextRun run = para.addNewTextRun();
        run.setText(text);
    }
}