package com.pptxgenerator.generator.parallel;

import com.pptxgenerator.assigner.model.SlidePlanWithLayout;
import com.pptxgenerator.client.GenerativeAiGateway;
import com.pptxgenerator.client.builder.GenerativeAiRequestBuilder;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;
import com.pptxgenerator.generator.model.BodyContent;
import com.pptxgenerator.generator.model.SlideContent;
import com.pptxgenerator.generator.prompt.ContentPromptBuilder;
import com.pptxgenerator.planner.AiResponseParser;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Génère le contenu des slides en parallèle (section 4.4.5).
 * Batch size recommandé : 4-6 appels simultanés.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ParallelContentGenerator {

    private final GenerativeAiGateway generativeAiGateway;
    private final AiResponseParser aiResponseParser;
    private final ContentPromptBuilder promptBuilder;

    private static final int BATCH_SIZE = 4;

    /**
     * Génère le contenu de toutes les slides en parallèle par batches.
     */
    public List<SlideContent> generateAll(List<SlidePlanWithLayout> slides,
                                           String language,
                                           String tone,
                                           boolean webSearch) {

        log.info("Génération parallèle de {} slides (batch size: {})", slides.size(), BATCH_SIZE);

        ExecutorService executor = Executors.newFixedThreadPool(BATCH_SIZE);
        List<SlideContent> results = new ArrayList<>();

        try {
            // Traiter par batches
            for (int i = 0; i < slides.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, slides.size());
                List<SlidePlanWithLayout> batch = slides.subList(i, end);

                log.debug("Traitement du batch {}-{}", i, end - 1);

                // Lancer les appels parallèles pour ce batch
                List<CompletableFuture<SlideContent>> futures = new ArrayList<>();
                for (int j = 0; j < batch.size(); j++) {
                    int slideIndex = i + j;
                    SlidePlanWithLayout slide = batch.get(j);

                    // Contexte de présentation
                    String previousTitle = slideIndex > 0 ?
                        slides.get(slideIndex - 1).getPurpose() : null;
                    String nextPurpose = slideIndex < slides.size() - 1 ?
                        slides.get(slideIndex + 1).getPurpose() : null;

                    CompletableFuture<SlideContent> future = CompletableFuture.supplyAsync(() ->
                        generateSingleSlide(slide, previousTitle, nextPurpose, language, tone, webSearch),
                        executor
                    );
                    futures.add(future);
                }

                // Attendre la fin du batch
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // Collecter les résultats
                for (CompletableFuture<SlideContent> future : futures) {
                    try {
                        results.add(future.get());
                    } catch (Exception e) {
                        log.error("Erreur génération slide", e);
                        results.add(createFallbackContent());
                    }
                }
            }
        } finally {
            executor.shutdown();
        }

        log.info("Génération parallèle terminée : {} slides", results.size());
        return results;
    }

    /**
     * Génère le contenu d'une seule slide avec retry.
     */
    private SlideContent generateSingleSlide(SlidePlanWithLayout slide,
                                              String previousTitle,
                                              String nextPurpose,
                                              String language,
                                              String tone,
                                              boolean webSearch) {
        int maxRetries = 3;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String systemPrompt = promptBuilder.buildSystemPrompt();
                String userPrompt = promptBuilder.buildUserPrompt(
                    slide, previousTitle, nextPurpose, language, tone, webSearch
                );

                TextRequestDto request = GenerativeAiRequestBuilder.builder()
                    .systemPrompt(systemPrompt)
                    .userPrompt(userPrompt)
                    .build()
                    .toRequest();
                TextResponseDto textResponse = generativeAiGateway.processRequest(request);
                String rawText = textResponse.getCandidates().get(0).getText();
                return aiResponseParser.parseAs(rawText, SlideContent.class);

            } catch (Exception e) {
                lastException = e;
                log.warn("Tentative {}/{} échouée pour slide {}: {}",
                    attempt, maxRetries, slide.getSlideNumber(), e.getMessage());

                if (attempt < maxRetries) {
                    sleep((long) Math.pow(2, attempt) * 1000);
                }
            }
        }

        log.error("Échec génération slide {} après {} tentatives", slide.getSlideNumber(), maxRetries);
        return createFallbackContent(slide);
    }

    private SlideContent createFallbackContent() {
        return SlideContent.builder()
            .title("Slide sans titre")
            .body(BodyContent.builder()
                .bullets(List.of("Contenu à générer"))
                .build())
            .build();
    }

    private SlideContent createFallbackContent(SlidePlanWithLayout slide) {
        return SlideContent.builder()
            .title(slide.getContentBrief() != null ? slide.getContentBrief() : "Slide " + slide.getSlideNumber())
            .body(BodyContent.builder()
                .bullets(List.of("Contenu à générer"))
                .build())
            .build();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
