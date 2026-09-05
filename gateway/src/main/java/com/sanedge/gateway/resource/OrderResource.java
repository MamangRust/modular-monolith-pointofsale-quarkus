package com.sanedge.gateway.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.sanedge.gateway.domain.requests.FindAllOrdersRequest;
import com.sanedge.gateway.domain.requests.FindOrdersByMerchantRequest;
import com.sanedge.gateway.domain.requests.GetOrderMonthlyTotalRevenueRequest;
import com.sanedge.gateway.domain.requests.GetOrderMonthlyTotalRevenueByMerchantRequest;
import com.sanedge.gateway.dto.OrderDto;
import com.sanedge.gateway.service.OrderService;

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

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Orders", description = "Order management endpoints")
public class OrderResource {

        @Inject
        OrderService orderService;

        @GET
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
        @Operation(summary = "List all orders")
        public Uni<Response> listOrders(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllOrdersRequest request = new FindAllOrdersRequest(search, page, size);
                return orderService.listOrders(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
        @Operation(summary = "Get order by ID")
        public Uni<Response> getOrder(@PathParam("id") int id) {
                return orderService.getOrder(id)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
        @Operation(summary = "List orders by merchant ID")
        public Uni<Response> listOrdersByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindOrdersByMerchantRequest request = new FindOrdersByMerchantRequest(merchantId, page, size, search);
                return orderService.listOrdersByMerchant(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/active")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
        @Operation(summary = "Get active orders")
        public Uni<Response> getActiveOrders(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllOrdersRequest request = new FindAllOrdersRequest(search, page, size);
                return orderService.getActiveOrders(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/trashed")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get trashed orders")
        public Uni<Response> getTrashedOrders(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllOrdersRequest request = new FindAllOrdersRequest(search, page, size);
                return orderService.getTrashedOrders(request)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Create a new order")
        public Uni<Response> createOrder(OrderDto.CreateRequest body) {
                return orderService.createOrder(body)
                                .map(res -> Response.status(Response.Status.CREATED)
                                                 .entity(res)
                                                 .build());
        }

        @PUT
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Update order")
        public Uni<Response> updateOrder(@PathParam("id") int id, OrderDto.UpdateRequest body) {
                return orderService.updateOrder(id, body)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Soft-delete an order")
        public Uni<Response> deleteOrder(@PathParam("id") int id) {
                return orderService.deleteOrder(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/{id}/restore")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore a soft-deleted order")
        public Uni<Response> restoreOrder(@PathParam("id") int id) {
                return orderService.restoreOrder(id)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}/permanent")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete an order")
        public Uni<Response> deleteOrderPermanent(@PathParam("id") int id) {
                return orderService.deleteOrderPermanent(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/restore-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore all soft-deleted orders")
        public Uni<Response> restoreAllOrder() {
                return orderService.restoreAllOrder()
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/permanent-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete all orders")
        public Uni<Response> deleteAllOrderPermanent() {
                return orderService.deleteAllOrderPermanent()
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-total-revenue")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly total revenue stats")
        public Uni<Response> getMonthlyTotalRevenue(
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                return orderService.getMonthlyTotalRevenue(year, month)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-total-revenue")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly total revenue stats")
        public Uni<Response> getYearlyTotalRevenue(@QueryParam("year") int year) {
                return orderService.getYearlyTotalRevenue(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-total-revenue/{orderId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly total revenue stats by order ID")
        public Uni<Response> getMonthlyTotalRevenueById(
                        @PathParam("orderId") int orderId,
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                GetOrderMonthlyTotalRevenueRequest request = new GetOrderMonthlyTotalRevenueRequest(orderId, year, month);
                return orderService.getMonthlyTotalRevenueById(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-total-revenue/{orderId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly total revenue stats by order ID")
        public Uni<Response> getYearlyTotalRevenueById(
                        @PathParam("orderId") int orderId,
                        @QueryParam("year") int year) {
                return orderService.getYearlyTotalRevenueById(orderId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-total-revenue/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly total revenue stats by merchant ID")
        public Uni<Response> getMonthlyTotalRevenueByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                GetOrderMonthlyTotalRevenueByMerchantRequest request = new GetOrderMonthlyTotalRevenueByMerchantRequest(merchantId, year, month);
                return orderService.getMonthlyTotalRevenueByMerchant(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-total-revenue/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly total revenue stats by merchant ID")
        public Uni<Response> getYearlyTotalRevenueByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("year") int year) {
                return orderService.getYearlyTotalRevenueByMerchant(merchantId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-revenue")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly revenue stats")
        public Uni<Response> getMonthlyRevenue(@QueryParam("year") int year) {
                return orderService.getMonthlyRevenue(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-revenue")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly revenue stats")
        public Uni<Response> getYearlyRevenue(@QueryParam("year") int year) {
                return orderService.getYearlyRevenue(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/monthly-revenue/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly revenue stats by merchant ID")
        public Uni<Response> getMonthlyRevenueByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("year") int year) {
                return orderService.getMonthlyRevenueByMerchant(merchantId, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/yearly-revenue/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly revenue stats by merchant ID")
        public Uni<Response> getYearlyRevenueByMerchant(
                        @PathParam("merchantId") int merchantId,
                        @QueryParam("year") int year) {
                return orderService.getYearlyRevenueByMerchant(merchantId, year)
                                .map(res -> Response.ok(res).build());
        }
}
