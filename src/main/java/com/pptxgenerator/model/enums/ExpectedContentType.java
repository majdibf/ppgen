// ExpectedContentType.java
package com.pptxgenerator.model.enums;

public enum ExpectedContentType {
    NUMBER,         // Un numéro (ex: "02")
    SHORT_TEXT,     // Texte court (1-3 mots)
    TITLE_TEXT,     // Titre (3-8 mots)
    LONG_TEXT,      // Texte long (paragraphe)
    BULLET_LIST,    // Liste à puces
    DATE,           // Date
    IMAGE_URL,      // URL d'image
    IMAGE_DESCRIPTION // Description d'image
}