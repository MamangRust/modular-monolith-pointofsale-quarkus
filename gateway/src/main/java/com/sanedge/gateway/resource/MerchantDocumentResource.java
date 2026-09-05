package com.sanedge.gateway.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.sanedge.gateway.domain.requests.FindAllMerchantDocumentsRequest;
import com.sanedge.gateway.dto.MerchantDocumentDto;
import com.sanedge.gateway.service.MerchantDocumentService;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import jakarta.annotation.security.RolesAllowed;

@Path("/api/merchant-documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Merchant Documents", description = "Merchant document management endpoints")
public class MerchantDocumentResource {

        @Inject
        MerchantDocumentService merchantDocumentService;

        @GET
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "List all merchant documents")
        public Uni<Response> listMerchantDocuments(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllMerchantDocumentsRequest request = new FindAllMerchantDocumentsRequest(search, page, size);
                return merchantDocumentService.listMerchantDocuments(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/active")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "List all active merchant documents")
        public Uni<Response> listActiveMerchantDocuments(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllMerchantDocumentsRequest request = new FindAllMerchantDocumentsRequest(search, page, size);
                return merchantDocumentService.listActiveMerchantDocuments(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/trashed")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "List all trashed merchant documents")
        public Uni<Response> listTrashedMerchantDocuments(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllMerchantDocumentsRequest request = new FindAllMerchantDocumentsRequest(search, page, size);
                return merchantDocumentService.listTrashedMerchantDocuments(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get merchant document by ID")
        public Uni<Response> getMerchantDocument(@PathParam("id") int id) {
                return merchantDocumentService.getMerchantDocument(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Create a new merchant document")
        public Uni<Response> createMerchantDocument(MerchantDocumentDto.CreateRequest body) {
                return merchantDocumentService.createMerchantDocument(body)
                                .map(res -> Response.status(Response.Status.CREATED)
                                                 .entity(res)
                                                 .build());
        }

        @PUT
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Update merchant document")
        public Uni<Response> updateMerchantDocument(@PathParam("id") int id, MerchantDocumentDto.UpdateRequest body) {
                return merchantDocumentService.updateMerchantDocument(id, body)
                                .map(res -> Response.ok(res).build());
        }

        @PUT
        @Path("/{id}/status")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Update merchant document status")
        public Uni<Response> updateMerchantDocumentStatus(@PathParam("id") int id,
                        MerchantDocumentDto.UpdateStatusRequest body) {
                return merchantDocumentService.updateMerchantDocumentStatus(id, body)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Soft-delete a merchant document")
        public Uni<Response> deleteMerchantDocument(@PathParam("id") int id) {
                return merchantDocumentService.deleteMerchantDocument(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/trash/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Soft-delete merchant document by ID")
        public Uni<Response> trashMerchantDocument(@PathParam("id") int id) {
                return merchantDocumentService.deleteMerchantDocument(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/restore/{id}")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore merchant document by ID")
        public Uni<Response> restoreMerchantDocument(@PathParam("id") int id) {
                return merchantDocumentService.restoreMerchantDocument(id)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}/permanent")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete a merchant document")
        public Uni<Response> deleteMerchantDocumentPermanent(@PathParam("id") int id) {
                return merchantDocumentService.deleteMerchantDocumentPermanent(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/restore-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore all merchant documents")
        public Uni<Response> restoreAllMerchantDocuments() {
                return merchantDocumentService.restoreAllMerchantDocuments()
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/delete-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Delete all merchant documents permanently")
        public Uni<Response> deleteAllMerchantDocuments() {
                return merchantDocumentService.deleteAllMerchantDocuments()
                                .map(res -> Response.ok(res).build());
        }
}
