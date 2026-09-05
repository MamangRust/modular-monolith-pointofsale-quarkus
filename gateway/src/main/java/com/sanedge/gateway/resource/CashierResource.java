package com.sanedge.gateway.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.sanedge.gateway.dto.CashierDto;
import com.sanedge.gateway.domain.requests.FindAllCashiersRequest;
import com.sanedge.gateway.domain.requests.FindCashiersByMerchantRequest;
import com.sanedge.gateway.domain.requests.GetCashierMonthlyTotalSalesRequest;
import com.sanedge.gateway.domain.requests.GetCashierMonthlyTotalSalesByMerchantRequest;
import com.sanedge.gateway.service.CashierService;

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

@Path("/api/cashiers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Cashiers", description = "Cashier management endpoints")
public class CashierResource {

        @Inject
        CashierService cashierService;

        @GET
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "List all cashiers")
        public Uni<Response> listCashiers(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllCashiersRequest request = new FindAllCashiersRequest(search, page, size);
                return cashierService.listCashiers(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get cashier by ID")
        public Uni<Response> getCashier(@PathParam("id") int id) {
                return cashierService.getCashier(id)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/active")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get active cashiers")
        public Uni<Response> getActiveCashiers(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllCashiersRequest request = new FindAllCashiersRequest(search, page, size);
                return cashierService.getActiveCashiers(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/trashed")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get trashed cashiers")
        public Uni<Response> getTrashedCashiers(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllCashiersRequest request = new FindAllCashiersRequest(search, page, size);
                return cashierService.getTrashedCashiers(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get cashiers by merchant ID")
        public Uni<Response> getCashiersByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindCashiersByMerchantRequest request = new FindCashiersByMerchantRequest(merchantId, page, size, search);
                return cashierService.getCashiersByMerchant(request)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Create a new cashier")
        public Uni<Response> createCashier(CashierDto.CreateRequest body) {
                return cashierService.createCashier(body)
                                .map(res -> Response.status(Response.Status.CREATED)
                                                 .entity(res)
                                                 .build());
        }

        @PUT
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Update cashier")
        public Uni<Response> updateCashier(@PathParam("id") int id, CashierDto.UpdateRequest body) {
                return cashierService.updateCashier(id, body)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Soft-delete a cashier")
        public Uni<Response> deleteCashier(@PathParam("id") int id) {
                return cashierService.deleteCashier(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/{id}/restore")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore a soft-deleted cashier")
        public Uni<Response> restoreCashier(@PathParam("id") int id) {
                return cashierService.restoreCashier(id)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}/permanent")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete a cashier")
        public Uni<Response> deleteCashierPermanent(@PathParam("id") int id) {
                return cashierService.deleteCashierPermanent(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/restore-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore all soft-deleted cashiers")
        public Uni<Response> restoreAllCashier() {
                return cashierService.restoreAllCashier()
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/permanent-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete all cashiers")
        public Uni<Response> deleteAllCashierPermanent() {
                return cashierService.deleteAllCashierPermanent()
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-total-sales")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly total sales stats")
        public Uni<Response> getMonthlyTotalSales(
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                return cashierService.getMonthlyTotalSales(year, month)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-total-sales")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly total sales stats")
        public Uni<Response> getYearlyTotalSales(@QueryParam("year") int year) {
                return cashierService.getYearlyTotalSales(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-total-sales/{cashierId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly total sales stats by cashier ID")
        public Uni<Response> getMonthlyTotalSalesById(
                        @PathParam("cashierId") int cashierId,
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                GetCashierMonthlyTotalSalesRequest request = new GetCashierMonthlyTotalSalesRequest(cashierId, year, month);
                return cashierService.getMonthlyTotalSalesById(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-total-sales/{cashierId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly total sales stats by cashier ID")
        public Uni<Response> getYearlyTotalSalesById(
                        @PathParam("cashierId") int cashierId,
                        @QueryParam("year") int year) {
                return cashierService.getYearlyTotalSalesById(cashierId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-total-sales/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly total sales stats by merchant ID")
        public Uni<Response> getMonthlyTotalSalesByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                GetCashierMonthlyTotalSalesByMerchantRequest request = new GetCashierMonthlyTotalSalesByMerchantRequest(merchantId, year, month);
                return cashierService.getMonthlyTotalSalesByMerchant(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-total-sales/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly total sales stats by merchant ID")
        public Uni<Response> getYearlyTotalSalesByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("year") int year) {
                return cashierService.getYearlyTotalSalesByMerchant(merchantId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-sales")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly sales stats")
        public Uni<Response> getMonthlySales(@QueryParam("year") int year) {
                return cashierService.getMonthlySales(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-sales")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly sales stats")
        public Uni<Response> getYearlySales(@QueryParam("year") int year) {
                return cashierService.getYearlySales(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-sales/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly sales stats by merchant ID")
        public Uni<Response> getMonthlySalesByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("year") int year) {
                return cashierService.getMonthlySalesByMerchant(merchantId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-sales/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly sales stats by merchant ID")
        public Uni<Response> getYearlySalesByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("year") int year) {
                return cashierService.getYearlySalesByMerchant(merchantId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-sales/{cashierId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly sales stats by cashier ID")
        public Uni<Response> getMonthlySalesById(
                        @PathParam("cashierId") int cashierId,
                        @QueryParam("year") int year) {
                return cashierService.getMonthlySalesById(cashierId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-sales/{cashierId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly sales stats by cashier ID")
        public Uni<Response> getYearlySalesById(
                        @PathParam("cashierId") int cashierId,
                        @QueryParam("year") int year) {
                return cashierService.getYearlySalesById(cashierId, year)
                                .map(res -> Response.ok(res).build());
        }
}
