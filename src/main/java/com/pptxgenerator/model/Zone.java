package com.pptxgenerator.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class Zone {
    // === IDENTIFICATION ===
    private int zoneId;
    private String zoneType;          // "title", "body", "badge", "picture", "footer", "section_number", "slide_number"
    private String placeholderType;   // Type exact du placeholder: "num", "title", "ctrTitle", "subTitle", "body", "obj", "pic", "ftr", "sldNum", "dt"
    private String placeholderName;   // Nom du placeholder (ex: "Numéro de section", "Titre 1")
    private String semanticRole;      // Rôle sémantique: "section_number", "slide_title", "body_text", "main_title", "subtitle", "badge_icon", "footer_text", "date"

    // === POSITION ET DIMENSIONS ===
    private long xEmu;
    private long yEmu;
    private long widthEmu;
    private long heightEmu;
    private double widthInches;
    private double heightInches;
    private String position;          // "top_center", "bottom_left", "middle_right", etc.
    private double surfacePercentage; // Pourcentage de la surface de la slide

    // === SÉMANTIQUE ===
    private boolean isBadge;          // Petite zone (numéro, icône)
    private boolean isNumber;         // Contient un numéro
    private boolean isTitle;          // Est un titre
    private boolean isBody;           // Est un corps de texte
    private boolean isImage;          // Est une image
    private boolean isFooter;         // Est un footer
    private boolean isDate;           // Est une date
    private List<String> expectedContentTypes; // ["number", "short_text", "long_text", "title_text", "date"]

    // === STYLE ===
    private ZoneStyle style;
    private Margins margins;          // Marges internes (lIns, rIns, tIns, bIns)

    // === RELATIONS ===
    private int readingOrder;         // Ordre de lecture (1, 2, 3...)
    private Integer pairedWithZoneId; // Zone liée (ex: badge + titre)

    // === MÉTADONNÉES ===
    private String importance;        // "HIGH", "MEDIUM", "LOW"
    private int maxChars;             // Estimation du nombre max de caractères
    private String description;       // Description lisible pour l'IA

    // === PLACEHOLDER ORIGINAL ===
    private PlaceholderInfo placeholder;

    // === IMAGE INFO ===
    private ImageInfo imageInfo;
}