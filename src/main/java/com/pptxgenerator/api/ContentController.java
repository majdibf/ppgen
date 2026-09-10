package com.pptxgenerator.api;

import com.pptxgenerator.common.exception.ContentNotFoundException;
import com.pptxgenerator.common.exception.ContentResultNotAvailableException;
import com.pptxgenerator.common.exception.ContentTokenUsedException;
import com.pptxgenerator.common.exception.DocumentUploadException;
import com.pptxgenerator.common.exception.InvalidTokenException;
import com.pptxgenerator.common.exception.NotFoundException;
import com.pptxgenerator.dto.request.CreateContentRequest;
import com.pptxgenerator.dto.request.ContentRequestDocumentDto;
import com.pptxgenerator.dto.response.ContentResponse;
import com.pptxgenerator.dto.response.ContentResultDto;
import com.pptxgenerator.service.ContentService;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.ByteArrayInputStream;

@Slf4j
@Path("/contentCreation/v1/contents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @POST
    public Response createContent(@Valid CreateContentRequest request) throws Exception {
        log.info("Creating content with operation: {}", request.getOperation());
        ContentResponse response = contentService.createContent(request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @POST
    @Path("/{contentId}/document")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendDocumentForContent(
            @PathParam("contentId") String contentExternalId,
            @QueryParam("signature") String signature,
            ContentRequestDocumentDto input) throws Exception {

        try {
            final FileUpload fileUpload = input.getFile();
            final java.nio.file.Path uploadedFile = fileUpload != null ? fileUpload.filePath() : null;
            if (uploadedFile == null) {
                throw new IllegalArgumentException("Uploaded file is null.");
            }
            ContentResponse response = contentService.sendDocumentForContent(
                    contentExternalId, uploadedFile.toFile(), fileUpload.fileName(), signature);
            return Response.status(Response.Status.OK)
                    .entity(response)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (InvalidTokenException | ContentTokenUsedException e) {
            throw new NotAuthorizedException(e.getMessage());
        } catch (ContentNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        } catch (DocumentUploadException e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @GET
    @Path("/{contentId}")
    public Response getContent(@PathParam("contentId") String contentId) throws Exception {
        log.info("Getting content: {}", contentId);
        ContentResponse response = contentService.getContent(contentId);
        if (response == null) {
            throw new NotFoundException("Content not found");
        }
        return Response.ok(response).build();
    }

    @GET
    @Path("/{contentId}/result")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getResult(
            @PathParam("contentId") String contentExternalId,
            @QueryParam("signature") String signature) throws Exception {

        log.info("Getting result for content: {}", contentExternalId);
        try {
            ContentResultDto result = contentService.getContentResult(contentExternalId, signature);
            if (result.getContent() == null) {
                throw new NotFoundException("Result not found");
            }
            return Response.ok(new ByteArrayInputStream(result.getContent()))
                    .header("Content-Disposition",
                            "attachment; filename=\"" + result.getFileName() + "\"")
                    .build();
        } catch (InvalidTokenException | ContentTokenUsedException e) {
            throw new NotAuthorizedException(e.getMessage());
        } catch (ContentNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        } catch (ContentResultNotAvailableException e) {
            throw new NotFoundException(e.getMessage());
        }
    }
}
