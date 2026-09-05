package com.sanedge.gateway.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.sanedge.gateway.domain.requests.FindAllRolesRequest;
import com.sanedge.gateway.dto.RoleDto;
import com.sanedge.gateway.service.RoleService;

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

@Path("/api/roles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Roles", description = "Role management endpoints")
public class RoleResource {

        @Inject
        RoleService roleService;

        @GET
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "List all roles")
        public Uni<Response> listRoles(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllRolesRequest request = new FindAllRolesRequest(search, page, size);
                return roleService.listRoles(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get role by ID")
        public Uni<Response> getRole(@PathParam("id") int id) {
                return roleService.getRole(id)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/active")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get active roles")
        public Uni<Response> getActiveRoles(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllRolesRequest request = new FindAllRolesRequest(search, page, size);
                return roleService.getActiveRoles(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/trashed")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get trashed roles")
        public Uni<Response> getTrashedRoles(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllRolesRequest request = new FindAllRolesRequest(search, page, size);
                return roleService.getTrashedRoles(request)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Create a new role")
        public Uni<Response> createRole(RoleDto.CreateRequest body) {
                return roleService.createRole(body)
                                .map(res -> Response.status(Response.Status.CREATED)
                                                 .entity(res)
                                                 .build());
        }

        @PUT
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Update role")
        public Uni<Response> updateRole(@PathParam("id") int id, RoleDto.UpdateRequest body) {
                return roleService.updateRole(id, body)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Soft-delete a role")
        public Uni<Response> deleteRole(@PathParam("id") int id) {
                return roleService.deleteRole(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/{id}/restore")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore a soft-deleted role")
        public Uni<Response> restoreRole(@PathParam("id") int id) {
                return roleService.restoreRole(id)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}/permanent")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete a role")
        public Uni<Response> deleteRolePermanent(@PathParam("id") int id) {
                return roleService.deleteRolePermanent(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/restore-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore all soft-deleted roles")
        public Uni<Response> restoreAllRole() {
                return roleService.restoreAllRole()
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/permanent-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete all roles")
        public Uni<Response> deleteAllRolePermanent() {
                return roleService.deleteAllRolePermanent()
                                .map(res -> Response.ok(res).build());
        }
}
