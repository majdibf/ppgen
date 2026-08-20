package com.pptxgenerator.generator.validation;

import com.pptxgenerator.generator.model.ContentGenerationWarning;
import com.pptxgenerator.generator.model.GeneratedContent;
import com.pptxgenerator.generator.model.SlideContent;
import com.pptxgenerator.model.enums.ContentCapacity;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Valide le contenu généré selon les règles R1-R8.
 */
@Slf4j
@ApplicationScoped
public class ContentValidator {

    /**
     * Valide et corrige le contenu généré.
     * @return Liste de warnings générés
     */
    public List<ContentGenerationWarning> validateAndFix(List<GeneratedContent.SlideWithContent> slides) {
        List<ContentGenerationWarning> warnings = new ArrayList<>();

        // R1 : Titres courts (max 8 mots)
        validateTitleLength(slides, warnings);

        // R2 : Bullets concis (max 12 mots)
        validateBulletLength(slides, warnings);

        // R3 : Style télégraphique (pas de point final)
        fixTelegraphicStyle(slides);

        // R4 : Densité contrôlée
        validateBulletDensity(slides, warnings);

        // R6 : Titres uniques
        fixDuplicateTitles(slides, warnings);

        // R8 : Pas de redondance
        detectRedundancy(slides, warnings);

        return warnings;
    }

    /**
     * R1 : Vérifie que les titres font max 8 mots
     */
    private void validateTitleLength(List<GeneratedContent.SlideWithContent> slides,
                                      List<ContentGenerationWarning> warnings) {
        List<Integer> violatingSlides = new ArrayList<>();

        for (var slide : slides) {
            if (slide.getContent() != null && slide.getContent().getTitle() != null) {
                int wordCount = slide.getContent().getTitle().trim().split("\\s+").length;
                if (wordCount > 8) {
                    violatingSlides.add(slide.getSlideNumber());
                    log.warn("R1 violée : Slide {} a un titre de {} mots (max 8)",
                        slide.getSlideNumber(), wordCount);
                }
            }
        }

        if (!violatingSlides.isEmpty()) {
            warnings.add(ContentGenerationWarning.builder()
                .code("TITLE_TOO_LONG")
                .message(String.format("Titres trop longs (>8 mots) détectés pour les slides %s", violatingSlides))
                .affectedSlides(violatingSlides)
                .build());
        }
    }

    /**
     * R2 : Vérifie que les bullets font max 12 mots
     */
    private void validateBulletLength(List<GeneratedContent.SlideWithContent> slides,
                                       List<ContentGenerationWarning> warnings) {
        List<Integer> violatingSlides = new ArrayList<>();

        for (var slide : slides) {
            if (slide.getContent() == null) continue;

            List<String> allBullets = extractAllBullets(slide.getContent());
            boolean hasViolation = allBullets.stream()
                .anyMatch(bullet -> bullet.trim().split("\\s+").length > 12);

            if (hasViolation) {
                violatingSlides.add(slide.getSlideNumber());
            }
        }

        if (!violatingSlides.isEmpty()) {
            warnings.add(ContentGenerationWarning.builder()
                .code("BULLET_TOO_LONG")
                .message(String.format("Bullets trop longs (>12 mots) détectés pour les slides %s", violatingSlides))
                .affectedSlides(violatingSlides)
                .build());
        }
    }

    /**
     * R3 : Supprime les points finaux des bullets
     */
    private void fixTelegraphicStyle(List<GeneratedContent.SlideWithContent> slides) {
        for (var slide : slides) {
            if (slide.getContent() == null) continue;

            List<String> allBullets = extractAllBullets(slide.getContent());
            allBullets.replaceAll(bullet -> bullet.replaceAll("\\.$", ""));
        }
    }

    /**
     * R4 : Vérifie la densité (nombre de bullets par zone)
     */
    private void validateBulletDensity(List<GeneratedContent.SlideWithContent> slides,
                                        List<ContentGenerationWarning> warnings) {
        List<Integer> violatingSlides = new ArrayList<>();

        for (var slide : slides) {
            if (slide.getContent() == null || slide.getLayout() == null) continue;

            ContentCapacity capacity = extractCapacity(slide);
            int maxBullets = capacity.getMaxBullets();

            // Vérifier body
            if (slide.getContent().getBody() != null) {
                int bulletCount = slide.getContent().getBody().getBullets().size();
                if (bulletCount > maxBullets) {
                    violatingSlides.add(slide.getSlideNumber());
                    // Tronquer
                    slide.getContent().getBody().setBullets(
                        slide.getContent().getBody().getBullets().subList(0, maxBullets)
                    );
                }
            }

            // Vérifier colonnes
            if (slide.getContent().getLeftColumn() != null) {
                int bulletCount = slide.getContent().getLeftColumn().getBullets().size();
                if (bulletCount > maxBullets) {
                    violatingSlides.add(slide.getSlideNumber());
                    slide.getContent().getLeftColumn().setBullets(
                        slide.getContent().getLeftColumn().getBullets().subList(0, maxBullets)
                    );
                }
            }

            if (slide.getContent().getRightColumn() != null) {
                int bulletCount = slide.getContent().getRightColumn().getBullets().size();
                if (bulletCount > maxBullets) {
                    violatingSlides.add(slide.getSlideNumber());
                    slide.getContent().getRightColumn().setBullets(
                        slide.getContent().getRightColumn().getBullets().subList(0, maxBullets)
                    );
                }
            }
        }

        if (!violatingSlides.isEmpty()) {
            warnings.add(ContentGenerationWarning.builder()
                .code("CONTENT_TRUNCATED")
                .message(String.format("Contenu tronqué pour respecter la capacité des slides %s", violatingSlides))
                .affectedSlides(violatingSlides)
                .build());
        }
    }

    /**
     * R6 : Vérifie l'unicité des titres
     */
    private void fixDuplicateTitles(List<GeneratedContent.SlideWithContent> slides,
                                     List<ContentGenerationWarning> warnings) {
        Map<String, List<Integer>> titleOccurrences = new HashMap<>();

        for (var slide : slides) {
            if (slide.getContent() != null && slide.getContent().getTitle() != null) {
                String title = slide.getContent().getTitle().trim().toLowerCase();
                titleOccurrences.computeIfAbsent(title, k -> new ArrayList<>())
                    .add(slide.getSlideNumber());
            }
        }

        List<Integer> duplicateSlides = new ArrayList<>();
        for (var entry : titleOccurrences.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicateSlides.addAll(entry.getValue());
                // Reformuler le titre dupliqué (ajouter un suffixe)
                for (int i = 1; i < entry.getValue().size(); i++) {
                    int slideNumber = entry.getValue().get(i);
                    var slide = slides.stream()
                        .filter(s -> s.getSlideNumber().equals(slideNumber))
                        .findFirst();
                    slide.ifPresent(s -> {
                        String originalTitle = s.getContent().getTitle();
                        s.getContent().setTitle(originalTitle + " (suite)");
                    });
                }
            }
        }

        if (!duplicateSlides.isEmpty()) {
            warnings.add(ContentGenerationWarning.builder()
                .code("DUPLICATE_TITLE_FIXED")
                .message(String.format("Titres dupliqués détectés et reformulés pour les slides %s", duplicateSlides))
                .affectedSlides(duplicateSlides)
                .build());
        }
    }

    /**
     * R8 : Détecte les redondances entre slides
     */
    private void detectRedundancy(List<GeneratedContent.SlideWithContent> slides,
                                   List<ContentGenerationWarning> warnings) {
        // Simplifié : comparer les bullets entre slides
        Set<String> allBullets = new HashSet<>();
        List<Integer> redundantSlides = new ArrayList<>();

        for (var slide : slides) {
            if (slide.getContent() == null) continue;

            List<String> slideBullets = extractAllBullets(slide.getContent());
            for (String bullet : slideBullets) {
                String normalized = bullet.trim().toLowerCase();
                if (allBullets.contains(normalized)) {
                    redundantSlides.add(slide.getSlideNumber());
                    break;
                }
                allBullets.add(normalized);
            }
        }

        if (!redundantSlides.isEmpty()) {
            warnings.add(ContentGenerationWarning.builder()
                .code("REDUNDANCY_DETECTED")
                .message(String.format("Redondances détectées entre les slides %s", redundantSlides))
                .affectedSlides(redundantSlides)
                .build());
        }
    }

    private List<String> extractAllBullets(SlideContent content) {
        List<String> bullets = new ArrayList<>();
        if (content.getBody() != null && content.getBody().getBullets() != null) {
            bullets.addAll(content.getBody().getBullets());
        }
        if (content.getLeftColumn() != null && content.getLeftColumn().getBullets() != null) {
            bullets.addAll(content.getLeftColumn().getBullets());
        }
        if (content.getRightColumn() != null && content.getRightColumn().getBullets() != null) {
            bullets.addAll(content.getRightColumn().getBullets());
        }
        return bullets;
    }

    private ContentCapacity extractCapacity(GeneratedContent.SlideWithContent slide) {
        if (slide.getLayout() != null) {
            return slide.getLayout().getContentCapacity();
        }
        return ContentCapacity.MEDIUM;
    }
}
