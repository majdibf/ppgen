package com.pptxgenerator.api;

import com.pptxgenerator.common.exception.NotFoundException;
import com.pptxgenerator.dto.request.CreateContentRequest;
import com.pptxgenerator.dto.response.ContentResponse;
import com.pptxgenerator.service.ContentService;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.InputStream;

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
    public Response uploadDocument(
            @PathParam("contentId") String contentId,
            @QueryParam("signature") String signature,
            @RestForm("file") FileUpload file) throws Exception {

        log.info("Uploading document for content: {}", contentId);
        ContentResponse response = contentService.uploadDocument(contentId, signature, file);
        return Response.ok(response).build();
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
            @PathParam("contentId") String contentId,
            @QueryParam("signature") String signature) throws Exception {

        log.info("Getting result for content: {}", contentId);
        InputStream resultStream = contentService.getResult(contentId, signature);
        if (resultStream == null) {
            throw new NotFoundException("Result not found");
        }
        return Response.ok(resultStream)
                .header("Content-Disposition", "attachment; filename=\"" + contentId + ".pptx\"")
                .build();
    }
}
