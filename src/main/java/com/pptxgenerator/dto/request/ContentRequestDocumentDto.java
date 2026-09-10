package com.pptxgenerator.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Multipart wrapper for the document upload endpoint, aligned with the real
 * project {@code ContentRequestDocumentDto}: the controller binds the whole DTO,
 * then reads {@code file()} to access the uploaded file and its original name.
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentRequestDocumentDto {

    @RestForm("file")
    private FileUpload file;
}
