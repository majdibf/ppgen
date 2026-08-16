package com.pptxgenerator.api;

import com.pptxgenerator.dto.response.TemplateResponse;
import com.pptxgenerator.service.TemplateService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.List;

@Path("/contentCreation/v1/templates")
@Produces(MediaType.APPLICATION_JSON)
public class TemplateController {
    
    private static final Logger LOG = Logger.getLogger(TemplateController.class);
    
    private final TemplateService templateService;
    
    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }
    
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response createTemplate(
            @FormParam("name") String name,
            @FormParam("description") String description,
            @FormParam("file") InputStream fileStream,
            @FormParam("fileName") String fileName,
            @FormParam("fileSize") Long fileSize) {
        
        LOG.infof("Creating template: %s", name);
        
        try {
            TemplateResponse response = templateService.createTemplate(name, description, fileStream, fileName, fileSize);
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (Exception e) {
            LOG.errorf("Failed to create template: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @GET
    public Response listTemplates(
            @QueryParam("search") String search,
            @QueryParam("file_type") String fileType) {
        
        LOG.infof("Listing templates, search: %s, fileType: %s", search, fileType);
        
        try {
            List<TemplateResponse> templates = templateService.listTemplates(search, fileType);
            return Response.ok(templates).build();
        } catch (Exception e) {
            LOG.errorf("Failed to list templates: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @GET
    @Path("/{templateId}")
    public Response getTemplate(@PathParam("templateId") String templateId) {
        LOG.infof("Getting template: %s", templateId);
        
        try {
            TemplateResponse response = templateService.getTemplate(templateId);
            if (response == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Template not found\"}")
                    .build();
            }
            return Response.ok(response).build();
        } catch (Exception e) {
            LOG.errorf("Failed to get template: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @DELETE
    @Path("/{templateId}")
    public Response deleteTemplate(@PathParam("templateId") String templateId) {
        LOG.infof("Deleting template: %s", templateId);
        
        try {
            boolean deleted = templateService.deleteTemplate(templateId);
            if (!deleted) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Template not found\"}")
                    .build();
            }
            return Response.noContent().build();
        } catch (Exception e) {
            LOG.errorf("Failed to delete template: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @GET
    @Path("/{templateId}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadTemplate(@PathParam("templateId") String templateId) {
        LOG.infof("Downloading template: %s", templateId);
        
        try {
            InputStream fileStream = templateService.downloadTemplate(templateId);
            if (fileStream == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Template file not found\"}")
                    .build();
            }
            return Response.ok(fileStream)
                .header("Content-Disposition", "attachment; filename=\"template.pptx\"")
                .build();
        } catch (Exception e) {
            LOG.errorf("Failed to download template: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
}
