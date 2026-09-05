package com.sanedge.gateway.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.sanedge.gateway.dto.CategoryDto;
import com.sanedge.gateway.domain.requests.FindAllCategoriesRequest;
import com.sanedge.gateway.domain.requests.GetCategoryMonthlyTotalRequest;
import com.sanedge.gateway.domain.requests.GetCategoryMonthlyTotalByMerchantRequest;
import com.sanedge.gateway.service.CategoryService;

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

@Path("/api/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Categories", description = "Category management endpoints")
public class CategoryResource {

        @Inject
        CategoryService categoryService;

        @GET
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
        @Operation(summary = "List all categories")
        public Uni<Response> listCategories(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllCategoriesRequest request = new FindAllCategoriesRequest(search, page, size);
                return categoryService.listCategories(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
        @Operation(summary = "Get category by ID")
        public Uni<Response> getCategory(@PathParam("id") int id) {
                return categoryService.getCategory(id)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/active")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
        @Operation(summary = "Get active categories")
        public Uni<Response> getActiveCategories(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllCategoriesRequest request = new FindAllCategoriesRequest(search, page, size);
                return categoryService.getActiveCategories(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/trashed")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get trashed categories")
        public Uni<Response> getTrashedCategories(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllCategoriesRequest request = new FindAllCategoriesRequest(search, page, size);
                return categoryService.getTrashedCategories(request)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Create a new category")
        public Uni<Response> createCategory(CategoryDto.CreateRequest body) {
                return categoryService.createCategory(body)
                                .map(res -> Response.status(Response.Status.CREATED)
                                                 .entity(res)
                                                 .build());
        }

        @PUT
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Update category")
        public Uni<Response> updateCategory(@PathParam("id") int id, CategoryDto.UpdateRequest body) {
                return categoryService.updateCategory(id, body)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Soft-delete a category")
        public Uni<Response> deleteCategory(@PathParam("id") int id) {
                return categoryService.deleteCategory(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/{id}/restore")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore a soft-deleted category")
        public Uni<Response> restoreCategory(@PathParam("id") int id) {
                return categoryService.restoreCategory(id)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}/permanent")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete a category")
        public Uni<Response> deleteCategoryPermanent(@PathParam("id") int id) {
                return categoryService.deleteCategoryPermanent(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/restore-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore all soft-deleted categories")
        public Uni<Response> restoreAllCategory() {
                return categoryService.restoreAllCategory()
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/permanent-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete all categories")
        public Uni<Response> deleteAllCategoryPermanent() {
                return categoryService.deleteAllCategoryPermanent()
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-total-prices")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly total prices stats")
        public Uni<Response> getMonthlyTotalPrices(
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                return categoryService.getMonthlyTotalPrices(year, month)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-total-prices")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly total prices stats")
        public Uni<Response> getYearlyTotalPrices(@QueryParam("year") int year) {
                return categoryService.getYearlyTotalPrices(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-total-prices/{categoryId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly total prices stats by category ID")
        public Uni<Response> getMonthlyTotalPricesById(
                        @PathParam("categoryId") int categoryId,
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                GetCategoryMonthlyTotalRequest request = new GetCategoryMonthlyTotalRequest(categoryId, year, month);
                return categoryService.getMonthlyTotalPricesById(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-total-prices/{categoryId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly total prices stats by category ID")
        public Uni<Response> getYearlyTotalPricesById(
                        @PathParam("categoryId") int categoryId,
                        @QueryParam("year") int year) {
                return categoryService.getYearlyTotalPricesById(categoryId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-total-prices/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly total prices stats by merchant ID")
        public Uni<Response> getMonthlyTotalPricesByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                GetCategoryMonthlyTotalByMerchantRequest request = new GetCategoryMonthlyTotalByMerchantRequest(merchantId, year, month);
                return categoryService.getMonthlyTotalPricesByMerchant(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-total-prices/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly total prices stats by merchant ID")
        public Uni<Response> getYearlyTotalPricesByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("year") int year) {
                return categoryService.getYearlyTotalPricesByMerchant(merchantId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-prices")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly prices stats")
        public Uni<Response> getMonthlyPrices(@QueryParam("year") int year) {
                return categoryService.getMonthlyPrices(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-prices")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly prices stats")
        public Uni<Response> getYearlyPrices(@QueryParam("year") int year) {
                return categoryService.getYearlyPrices(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-prices/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly prices stats by merchant ID")
        public Uni<Response> getMonthlyPricesByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("year") int year) {
                return categoryService.getMonthlyPricesByMerchant(merchantId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-prices/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly prices stats by merchant ID")
        public Uni<Response> getYearlyPricesByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("year") int year) {
                return categoryService.getYearlyPricesByMerchant(merchantId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-prices/{categoryId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly prices stats by category ID")
        public Uni<Response> getMonthlyPricesById(
                        @PathParam("categoryId") int categoryId,
                        @QueryParam("year") int year) {
                return categoryService.getMonthlyPricesById(categoryId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-prices/{categoryId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly prices stats by category ID")
        public Uni<Response> getYearlyPricesById(
                        @PathParam("categoryId") int categoryId,
                        @QueryParam("year") int year) {
                return categoryService.getYearlyPricesById(categoryId, year)
                                .map(res -> Response.ok(res).build());
        }
}
