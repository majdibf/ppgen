package com.pptxgenerator.service;

import com.pptxgenerator.entity.Content;
import com.pptxgenerator.model.enums.ContentStatus;
import com.pptxgenerator.repository.ContentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class ContentStatusService {
    private final ContentRepository repository;

    public ContentStatusService(ContentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Content markRunning(String contentId) {
        Content content = required(contentId);
        content.setStatus(ContentStatus.RUNNING);
        content.setStartedAt(Instant.now());
        return repository.update(content);
    }

    @Transactional
    public Content markSucceeded(String contentId, String resultUrl) {
        Content content = required(contentId);
        content.setResultUrl(resultUrl);
        content.setStatus(ContentStatus.SUCCEEDED);
        content.setEndedAt(Instant.now());
        content.setSignatureFetchResult("sig_fr_" + UUID.randomUUID().toString().replace("-", ""));
        return repository.update(content);
    }

    @Transactional
    public void markFailed(String contentId, String message) {
        Content content = repository.findByContentId(contentId);
        if (content == null) return;
        content.setStatus(ContentStatus.FAILED);
        content.setErrorMessage(message);
        content.setEndedAt(Instant.now());
        repository.update(content);
    }

    private Content required(String contentId) {
        Content content = repository.findByContentId(contentId);
        if (content == null) throw new IllegalArgumentException("Content not found: " + contentId);
        return content;
    }
}
