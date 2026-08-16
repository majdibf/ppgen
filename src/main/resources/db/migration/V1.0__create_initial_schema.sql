-- V1.0__create_initial_schema.sql
-- Create initial database schema for Content Creation API

CREATE TABLE template (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    file_type VARCHAR(10) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    template_analysis JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_template_name (name),
    INDEX idx_template_file_type (file_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE content (
    id VARCHAR(36) PRIMARY KEY,
    operation VARCHAR(50) NOT NULL,
    model_id VARCHAR(100) NOT NULL,
    output_format VARCHAR(10) NOT NULL,
    template_id VARCHAR(36),
    instructions TEXT,
    inputs JSON,
    web_search BOOLEAN DEFAULT FALSE,
    options JSON,
    status VARCHAR(50) NOT NULL,
    signature_send_document VARCHAR(100),
    signature_fetch_result VARCHAR(100),
    document_url VARCHAR(500),
    result_url VARCHAR(500),
    warnings JSON,
    error_message TEXT,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    queued_at TIMESTAMP NULL,
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    FOREIGN KEY (template_id) REFERENCES template(id) ON DELETE SET NULL,
    INDEX idx_content_status (status),
    INDEX idx_content_template_id (template_id),
    INDEX idx_content_submitted_at (submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
