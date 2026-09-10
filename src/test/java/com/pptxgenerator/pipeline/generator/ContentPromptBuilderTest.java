package com.pptxgenerator.pipeline.generator;

import com.pptxgenerator.model.Zone;
import com.pptxgenerator.model.enums.ZoneType;
import com.pptxgenerator.pipeline.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.pipeline.planner.model.SlideType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ContentPromptBuilder}: the per-zone block must expose the
 * physical capacity ("Max characters") so the AI can respect the real text budget.
 */
class ContentPromptBuilderTest {

    private final ContentPromptBuilder builder = new ContentPromptBuilder();

    @Test
    void buildUserPrompt_includesMaxCharacters_whenPresent() {
        SlidePlanWithLayout slide = SlidePlanWithLayout.builder()
            .slideNumber(15)
            .slideType(SlideType.CONTENT)
            .purpose("Établir les faits")
            .contentBrief("Le modèle de formation")
            .detailedContext("60% des médailles d'or")
            .build();
        Zone body = Zone.builder().zoneId(1).zoneType(ZoneType.BODY).maxCharacters(242).build();
        Zone picture = Zone.builder().zoneId(2).zoneType(ZoneType.PICTURE).build();

        String prompt = builder.buildUserPrompt(slide, "Titre précédent", "Suivant", "fr",
            "PROFESSIONAL", false, List.of(body, picture));

        assertThat(prompt).contains("- Zone_key: body_1");
        assertThat(prompt).contains("Max characters: 242");
        assertThat(prompt).doesNotContain("Max characters: null");
    }

    @Test
    void buildSystemPrompt_carriesMaxCharactersRule() {
        assertThat(builder.buildSystemPrompt()).contains("Max characters");
    }
}
