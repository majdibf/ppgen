package com.pptxgenerator.pipeline.generator;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Classifie et trie les zones d'une slide par type.
 * Centralise la logique de filtrage pour éviter la duplication.
 */
public class ZoneClassifier {

    private final List<Zone> titleZones;
    private final List<Zone> centerTitleZones;
    private final List<Zone> subtitleZones;
    private final List<Zone> lineZones;
    private final List<Zone> wordZones;
    private final List<Zone> mediaZones; // PICTURE + BACKGROUND

    public ZoneClassifier(List<Zone> zones) {
        this.titleZones = filterByType(zones, ZoneType.TITLE);
        this.centerTitleZones = filterByType(zones, ZoneType.CENTER_TITLE);
        this.subtitleZones = filterByType(zones, ZoneType.SUBTITLE);
        this.lineZones = filterAndSort(zones, ZoneType.LINE);
        this.wordZones = filterAndSort(zones, ZoneType.WORD);
        this.mediaZones = filterByTypes(zones, ZoneType.PICTURE, ZoneType.BACKGROUND);
    }

    // === Getters ===

    public List<Zone> getTitleZones() { return titleZones; }
    public List<Zone> getCenterTitleZones() { return centerTitleZones; }
    public List<Zone> getSubtitleZones() { return subtitleZones; }
    public List<Zone> getLineZones() { return lineZones; }
    public List<Zone> getWordZones() { return wordZones; }
    public List<Zone> getMediaZones() { return mediaZones; }

    /**
     * Retourne le premier titre (priorité aux TITLE, puis CENTER_TITLE).
     */
    public Optional<Zone> getFirstTitle() {
        if (!titleZones.isEmpty()) {
            return Optional.of(titleZones.get(0));
        }
        if (!centerTitleZones.isEmpty()) {
            return Optional.of(centerTitleZones.get(0));
        }
        return Optional.empty();
    }

    public Optional<Zone> getFirstSubtitle() {
        return subtitleZones.isEmpty() ? Optional.empty() : Optional.of(subtitleZones.get(0));
    }

    public boolean hasSubtitle() {
        return !subtitleZones.isEmpty();
    }

    // === Méthodes privées ===

    private List<Zone> filterByType(List<Zone> zones, ZoneType type) {
        return zones.stream()
                .filter(z -> z.getZoneType() == type)
                .toList();
    }

    private List<Zone> filterByTypes(List<Zone> zones, ZoneType... types) {
        return zones.stream()
                .filter(z -> {
                    for (ZoneType type : types) {
                        if (z.getZoneType() == type) return true;
                    }
                    return false;
                })
                .toList();
    }

    private List<Zone> filterAndSort(List<Zone> zones, ZoneType type) {
        return zones.stream()
                .filter(z -> z.getZoneType() == type)
                .sorted(Comparator.comparingInt(Zone::getZoneId))
                .toList();
    }
}
