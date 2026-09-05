package com.sanedge.gateway.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.sanedge.gateway.dto.MerchantDto;
import com.sanedge.gateway.domain.requests.FindAllMerchantsRequest;
import com.sanedge.gateway.service.MerchantService;

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

@Path("/api/merchants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Merchants", description = "Merchant management endpoints")
public class MerchantResource {

        @Inject
        MerchantService merchantService;

        @GET
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "List all merchants")
        public Uni<Response> listMerchants(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllMerchantsRequest request = new FindAllMerchantsRequest(search, page, size);
                return merchantService.listMerchants(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get merchant by ID")
        public Uni<Response> getMerchant(@PathParam("id") int id) {
                return merchantService.getMerchant(id)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/active")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get active merchants")
        public Uni<Response> getActiveMerchants(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllMerchantsRequest request = new FindAllMerchantsRequest(search, page, size);
                return merchantService.getActiveMerchants(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/trashed")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get trashed merchants")
        public Uni<Response> getTrashedMerchants(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllMerchantsRequest request = new FindAllMerchantsRequest(search, page, size);
                return merchantService.getTrashedMerchants(request)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Create a new merchant")
        public Uni<Response> createMerchant(MerchantDto.CreateRequest body) {
                return merchantService.createMerchant(body)
                                .map(res -> Response.status(Response.Status.CREATED)
                                                 .entity(res)
                                                 .build());
        }

        @PUT
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Update merchant")
        public Uni<Response> updateMerchant(@PathParam("id") int id,
                        MerchantDto.UpdateRequest body) {
                return merchantService.updateMerchant(id, body)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Soft-delete a merchant")
        public Uni<Response> deleteMerchant(@PathParam("id") int id) {
                return merchantService.deleteMerchant(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/{id}/restore")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore a soft-deleted merchant")
        public Uni<Response> restoreMerchant(@PathParam("id") int id) {
                return merchantService.restoreMerchant(id)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}/permanent")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete a merchant")
        public Uni<Response> deleteMerchantPermanent(@PathParam("id") int id) {
                return merchantService.deleteMerchantPermanent(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/restore-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore all soft-deleted merchants")
        public Uni<Response> restoreAllMerchant() {
                return merchantService.restoreAllMerchant()
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/permanent-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete all merchants")
        public Uni<Response> deleteAllMerchantPermanent() {
                return merchantService.deleteAllMerchantPermanent()
                                .map(res -> Response.ok(res).build());
        }
}
