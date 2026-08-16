package com.pptxgenerator.service;

import com.pptxgenerator.dto.response.TemplateResponse;
import com.pptxgenerator.entity.Template;
import com.pptxgenerator.mapper.TemplateMapper;
import com.pptxgenerator.model.enums.FileType;
import com.pptxgenerator.repository.TemplateRepository;
import com.pptxgenerator.storage.StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class TemplateService {
    
    private static final Logger LOG = Logger.getLogger(TemplateService.class);
    
    private final TemplateRepository templateRepository;
    private final TemplateMapper templateMapper;
    private final StorageService storageService;
    
    public TemplateService(TemplateRepository templateRepository,
                          TemplateMapper templateMapper,
                          StorageService storageService) {
        this.templateRepository = templateRepository;
        this.templateMapper = templateMapper;
        this.storageService = storageService;
    }
    
    @Transactional
    public TemplateResponse createTemplate(String name, String description, InputStream fileStream, 
                                          String fileName, Long fileSize) throws Exception {
        LOG.infof("Creating template: %s", name);
        
        // Generate template ID
        String templateId = "tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        
        // Upload to storage
        String objectKey = "templates/" + templateId + "/" + fileName;
        storageService.uploadTemplate(objectKey, fileStream, "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        
        // Create template entity
        Template template = Template.builder()
            .id(templateId)
            .name(name)
            .description(description)
            .fileType(FileType.PPTX)
            .fileUrl(storageService.getTemplateUrl(objectKey))
            .fileSizeBytes(fileSize)
            .fileName(fileName)
            .build();
        
        // Save to database
        template = templateRepository.save(template);
        
        LOG.infof("Template created: %s", templateId);
        
        return templateMapper.toResponse(template);
    }
    
    public List<TemplateResponse> listTemplates(String search, String fileType) throws Exception {
        LOG.infof("Listing templates, search: %s, fileType: %s", search, fileType);
        
        List<Template> templates;
        
        if (search != null && !search.isEmpty()) {
            templates = templateRepository.searchByName(search);
        } else if (fileType != null && !fileType.isEmpty()) {
            templates = templateRepository.findByFileType(FileType.valueOf(fileType));
        } else {
            templates = templateRepository.findAllTemplates();
        }
        
        return templates.stream()
            .map(templateMapper::toResponse)
            .collect(Collectors.toList());
    }
    
    public TemplateResponse getTemplate(String templateId) throws Exception {
        Template template = templateRepository.findByTemplateId(templateId);
        if (template == null) {
            return null;
        }
        return templateMapper.toResponse(template);
    }
    
    @Transactional
    public boolean deleteTemplate(String templateId) throws Exception {
        Template template = templateRepository.findByTemplateId(templateId);
        if (template == null) {
            return false;
        }
        
        // Delete from storage
        String objectKey = template.getFileUrl().substring(template.getFileUrl().indexOf("/") + 1);
        storageService.deleteTemplate(objectKey);
        
        // Delete from database
        templateRepository.deleteByTemplateId(templateId);
        
        LOG.infof("Template deleted: %s", templateId);
        
        return true;
    }
    
    public InputStream downloadTemplate(String templateId) throws Exception {
        Template template = templateRepository.findByTemplateId(templateId);
        if (template == null) {
            return null;
        }
        
        // Extract object key from URL
        String objectKey = template.getFileUrl().substring(template.getFileUrl().indexOf("/") + 1);
        return storageService.downloadTemplate(objectKey);
    }
}
