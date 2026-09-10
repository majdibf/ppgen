package com.pptxgenerator.pipeline.analyzer;

import com.pptxgenerator.model.TemplateAnalysis;
import com.pptxgenerator.pipeline.analyzer.TemplateAnalyzer;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.PresentationMLPackage;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TemplateAnalysisService {

    private final TemplateAnalyzer templateAnalyzer;
    private final TemplateAnalysisValidator validator;

    /**
     * Full analysis of a PowerPoint template (Java port of step2_layout.py).
     *
     * @param modelId user-requested model id (nullable: falls back to the provider default)
     */
    public TemplateAnalysis analyze(PresentationMLPackage pptx, String modelId) throws Docx4JException {
        TemplateAnalysis analysis = templateAnalyzer.analyze(pptx, modelId);
        validator.validate(analysis);
        return analysis;
    }
}
