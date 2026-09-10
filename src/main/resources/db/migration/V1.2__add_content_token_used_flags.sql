-- Single-use token flags (aligned with real project: ContentRequestDocumentDto flow)
ALTER TABLE content ADD COLUMN document_token_used BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE content ADD COLUMN result_token_used BOOLEAN NOT NULL DEFAULT FALSE;