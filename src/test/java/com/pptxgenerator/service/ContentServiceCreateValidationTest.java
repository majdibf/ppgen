package com.pptxgenerator.service;

import com.pptxgenerator.dto.request.CreateContentRequest;
import com.pptxgenerator.mapper.ContentMapper;
import com.pptxgenerator.model.enums.Operation;
import com.pptxgenerator.model.enums.OutputFormat;
import com.pptxgenerator.pipeline.ContentCreationPipeline;
import com.pptxgenerator.repository.ContentRepository;
import com.pptxgenerator.repository.TemplateRepository;
import com.pptxgenerator.service.s3.S3ContentOutputStorage;
import com.pptxgenerator.service.s3.S3ContentTemplateStorage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * TemplateId is reserved by the spec for the template library (CAS 2), which is
 * out of scope: createContent must reject it instead of leaving a QUEUED content
 * with no pipeline trigger.
 */
class ContentServiceCreateValidationTest {

    @Test
    void templateId_rejectedUntilLibraryExists() throws Exception {
        ContentRepository repository = mock(ContentRepository.class);
        ContentService service = new ContentService(repository,
            mock(TemplateRepository.class),
            mock(ContentMapper.class),
            mock(S3ContentTemplateStorage.class),
            mock(S3ContentOutputStorage.class),
            mock(ContentCreationPipeline.class));

        CreateContentRequest request = new CreateContentRequest();
        request.setOperation(Operation.CREATION);
        request.setOutputFormat(OutputFormat.PPTX);
        request.setTemplateId("tpl_a1b2c3d4");

        assertThatThrownBy(() -> service.createContent(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("templateId");

        Mockito.verifyNoInteractions(repository);
    }
}