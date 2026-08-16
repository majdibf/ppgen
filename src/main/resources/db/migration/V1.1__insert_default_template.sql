-- V1.1__insert_default_template.sql
-- Insert default template for V1

INSERT INTO template (id, name, description, file_type, file_url, file_size_bytes, file_name, template_analysis)
VALUES (
    'tpl_default_001',
    'Template par défaut',
    'Template de présentation par défaut pour V1',
    'PPTX',
    'templates/default/template_1.pptx',
    2458624,
    'template_1.pptx',
    NULL
);
