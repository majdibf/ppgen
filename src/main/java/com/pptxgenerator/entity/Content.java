package com.pptxgenerator.entity;

import com.pptxgenerator.model.enums.ContentStatus;
import com.pptxgenerator.model.enums.Operation;
import com.pptxgenerator.model.enums.OutputFormat;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "content")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Content extends PanacheEntityBase {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Operation operation;
    
    @Column(name = "model_id", nullable = false)
    private String modelId;
    
    @Column(name = "output_format", nullable = false)
    @Enumerated(EnumType.STRING)
    private OutputFormat outputFormat;
    
    @Column(name = "template_id")
    private String templateId;
    
    @Column(columnDefinition = "TEXT")
    private String instructions;
    
    @Column(columnDefinition = "JSON")
    private String inputs;
    
    @Column(name = "web_search")
    @Builder.Default
    private Boolean webSearch = false;
    
    @Column(columnDefinition = "JSON")
    private String options;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ContentStatus status;
    
    @Column(name = "signature_send_document")
    private String signatureSendDocument;
    
    @Column(name = "signature_fetch_result")
    private String signatureFetchResult;
    
    @Column(name = "document_url", length = 500)
    private String documentUrl;
    
    @Column(name = "result_url", length = 500)
    private String resultUrl;
    
    @Column(columnDefinition = "JSON")
    private String warnings;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "submitted_at")
    private Instant submittedAt;
    
    @Column(name = "queued_at")
    private Instant queuedAt;
    
    @Column(name = "started_at")
    private Instant startedAt;
    
    @Column(name = "ended_at")
    private Instant endedAt;
    
    @PrePersist
    void prePersist() {
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
    }
}
