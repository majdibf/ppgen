package com.pptxgenerator.repository;

import com.pptxgenerator.entity.Template;
import com.pptxgenerator.model.enums.FileType;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class TemplateRepository implements PanacheRepositoryBase<Template, String> {
    
    public List<Template> findAllTemplates() {
        return listAll();
    }
    
    public Template findByTemplateId(String templateId) {
        return findById(templateId);
    }
    
    public Template findByName(String name) {
        return find("name", name).firstResult();
    }
    
    public List<Template> findByFileType(FileType fileType) {
        return list("fileType", fileType);
    }
    
    public List<Template> searchByName(String search) {
        return find("name LIKE ?1", "%" + search + "%").list();
    }
    
    public Template save(Template template) {
        persist(template);
        return template;
    }
    
    public boolean deleteByTemplateId(String templateId) {
        return deleteById(templateId);
    }
}
