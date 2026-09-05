package com.sanedge.gateway.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.sanedge.gateway.dto.UserDto;
import com.sanedge.gateway.domain.requests.FindAllUsersRequest;
import com.sanedge.gateway.service.UserService;

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

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Users", description = "User management endpoints")
public class UserResource {

        @Inject
        UserService userService;

        @GET
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "List all users")
        public Uni<Response> listUsers(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllUsersRequest request = new FindAllUsersRequest(search, page, size);
                return userService.listUsers(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/active")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get active users")
        public Uni<Response> getActiveUsers(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllUsersRequest request = new FindAllUsersRequest(search, page, size);
                return userService.getActiveUsers(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/trashed")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Get trashed users")
        public Uni<Response> getTrashedUsers(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllUsersRequest request = new FindAllUsersRequest(search, page, size);
                return userService.getTrashedUsers(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get user by ID")
        public Uni<Response> getUser(@PathParam("id") int id) {
                return userService.getUser(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Create a new user")
        public Uni<Response> createUser(UserDto.CreateRequest body) {
                return userService.createUser(body)
                                .map(res -> Response.status(Response.Status.CREATED)
                                                 .entity(res)
                                                 .build());
        }

        @PUT
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Update user")
        public Uni<Response> updateUser(@PathParam("id") int id, UserDto.UpdateRequest body) {
                return userService.updateUser(id, body)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Soft-delete a user")
        public Uni<Response> deleteUser(@PathParam("id") int id) {
                return userService.deleteUser(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/{id}/restore")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore a soft-deleted user")
        public Uni<Response> restoreUser(@PathParam("id") int id) {
                return userService.restoreUser(id)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}/permanent")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete a user")
        public Uni<Response> deleteUserPermanent(@PathParam("id") int id) {
                return userService.deleteUserPermanent(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/restore-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore all soft-deleted users")
        public Uni<Response> restoreAllUser() {
                return userService.restoreAllUser()
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/permanent-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete all users")
        public Uni<Response> deleteAllUserPermanent() {
                return userService.deleteAllUserPermanent()
                                .map(res -> Response.ok(res).build());
        }
}