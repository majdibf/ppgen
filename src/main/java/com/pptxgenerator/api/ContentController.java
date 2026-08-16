package com.pptxgenerator.api;

import com.pptxgenerator.dto.request.CreateContentRequest;
import com.pptxgenerator.dto.response.ContentResponse;
import com.pptxgenerator.service.ContentService;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.logging.Logger;

import java.io.InputStream;

@Path("/contentCreation/v1/contents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContentController {
    
    private static final Logger LOG = Logger.getLogger(ContentController.class);
    
    private final ContentService contentService;
    
    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }
    
    @POST
    public Response createContent(@Valid CreateContentRequest request) {
        LOG.infof("Creating content with operation: %s", request.getOperation());
        
        try {
            ContentResponse response = contentService.createContent(request);
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (Exception e) {
            LOG.errorf("Failed to create content: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @POST
    @Path("/{contentId}/document")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadDocument(
            @PathParam("contentId") String contentId,
            @QueryParam("signature") String signature,
            @RestForm("file") FileUpload file) {
        
        LOG.infof("Uploading document for content: %s", contentId);
        
        try {
            ContentResponse response = contentService.uploadDocument(contentId, signature, file);
            return Response.ok(response).build();
        } catch (Exception e) {
            LOG.errorf("Failed to upload document: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @GET
    @Path("/{contentId}")
    public Response getContent(@PathParam("contentId") String contentId) {
        LOG.infof("Getting content: %s", contentId);
        
        try {
            ContentResponse response = contentService.getContent(contentId);
            if (response == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Content not found\"}")
                    .build();
            }
            return Response.ok(response).build();
        } catch (Exception e) {
            LOG.errorf("Failed to get content: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @GET
    @Path("/{contentId}/result")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getResult(
            @PathParam("contentId") String contentId,
            @QueryParam("signature") String signature) {
        
        LOG.infof("Getting result for content: %s", contentId);
        
        try {
            InputStream resultStream = contentService.getResult(contentId, signature);
            if (resultStream == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Result not found\"}")
                    .build();
            }
            return Response.ok(resultStream)
                .header("Content-Disposition", "attachment; filename=\"presentation.pptx\"")
                .build();
        } catch (Exception e) {
            LOG.errorf("Failed to get result: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
}
