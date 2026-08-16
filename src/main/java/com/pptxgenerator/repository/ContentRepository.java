package com.pptxgenerator.repository;

import com.pptxgenerator.entity.Content;
import com.pptxgenerator.model.enums.ContentStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class ContentRepository implements PanacheRepositoryBase<Content, String> {
    
    public Content findByContentId(String contentId) {
        return findById(contentId);
    }
    
    public List<Content> findByStatus(ContentStatus status) {
        return list("status", status);
    }
    
    public List<Content> findByTemplateId(String templateId) {
        return list("templateId", templateId);
    }
    
    public Content save(Content content) {
        persist(content);
        return content;
    }
    
    public Content update(Content content) {
        getEntityManager().merge(content);
        return content;
    }
    
    public boolean deleteByContentId(String contentId) {
        return deleteById(contentId);
    }
}
