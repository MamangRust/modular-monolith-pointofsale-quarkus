package com.sanedge.gateway.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.sanedge.gateway.domain.requests.FindAllTransactionsRequest;
import com.sanedge.gateway.domain.requests.FindTransactionsByCardNumberRequest;
import com.sanedge.gateway.domain.requests.GetTransactionStatusFailedByCardNumberRequest;
import com.sanedge.gateway.domain.requests.GetTransactionStatusSuccessByCardNumberRequest;
import com.sanedge.gateway.dto.TransactionDto;
import com.sanedge.gateway.service.TransactionService;

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

@Path("/api/transactions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Transactions", description = "Transaction management and statistics endpoints")
public class TransactionResource {

        @Inject
        TransactionService transactionService;

        @GET
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
        @Operation(summary = "List all transactions")
        public Uni<Response> listTransactions(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllTransactionsRequest request = new FindAllTransactionsRequest(search, page, size);
                return transactionService.listTransactions(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/card-number/{cardNumber}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
        @Operation(summary = "List transactions by card number")
        public Uni<Response> listTransactionsByCardNumber(
                        @PathParam("cardNumber") String cardNumber,
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindTransactionsByCardNumberRequest request = new FindTransactionsByCardNumberRequest(cardNumber, page, size, search);
                return transactionService.listTransactionsByCardNumber(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
        @Operation(summary = "Get transaction by ID")
        public Uni<Response> getTransaction(@PathParam("id") int id) {
                return transactionService.getTransaction(id)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/merchant/{merchantId}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
        @Operation(summary = "Get transactions by merchant ID")
        public Uni<Response> getTransactionsByMerchant(@PathParam("merchantId") int merchantId) {
                return transactionService.getTransactionsByMerchant(merchantId)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/active")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
        @Operation(summary = "Get active transactions")
        public Uni<Response> getActiveTransactions(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllTransactionsRequest request = new FindAllTransactionsRequest(search, page, size);
                return transactionService.getActiveTransactions(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/trashed")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get trashed transactions")
        public Uni<Response> getTrashedTransactions(
                        @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("20") int size,
                        @QueryParam("search") String search) {
                FindAllTransactionsRequest request = new FindAllTransactionsRequest(search, page, size);
                return transactionService.getTrashedTransactions(request)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Create a new transaction")
        public Uni<Response> createTransaction(TransactionDto.CreateRequest body) {
                return transactionService.createTransaction(body)
                                .map(res -> Response.status(Response.Status.CREATED)
                                                 .entity(res)
                                                 .build());
        }

        @PUT
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Update transaction")
        public Uni<Response> updateTransaction(@PathParam("id") int id, TransactionDto.UpdateRequest body) {
                return transactionService.updateTransaction(id, body)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Soft-delete a transaction")
        public Uni<Response> deleteTransaction(@PathParam("id") int id) {
                return transactionService.deleteTransaction(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/{id}/restore")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore a soft-deleted transaction")
        public Uni<Response> restoreTransaction(@PathParam("id") int id) {
                return transactionService.restoreTransaction(id)
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/{id}/permanent")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete a transaction")
        public Uni<Response> deleteTransactionPermanent(@PathParam("id") int id) {
                return transactionService.deleteTransactionPermanent(id)
                                .map(res -> Response.ok(res).build());
        }

        @POST
        @Path("/restore-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Restore all soft-deleted transactions")
        public Uni<Response> restoreAllTransaction() {
                return transactionService.restoreAllTransaction()
                                .map(res -> Response.ok(res).build());
        }

        @DELETE
        @Path("/permanent-all")
        @RolesAllowed({ "ROLE_ADMIN" })
        @Operation(summary = "Permanently delete all soft-deleted transactions")
        public Uni<Response> deleteAllTransactionPermanent() {
                return transactionService.deleteAllTransactionPermanent()
                                .map(res -> Response.ok(res).build());
        }

        // --- Statistics - Amount ---

        @GET
        @Path("/stats/amount/monthly")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly transaction amount statistics")
        public Uni<Response> getMonthlyAmounts(@QueryParam("year") int year) {
                return transactionService.getMonthlyAmounts(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/amount/yearly")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly transaction amount statistics")
        public Uni<Response> getYearlyAmounts(@QueryParam("year") int year) {
                return transactionService.getYearlyAmounts(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/amount/monthly/card-number/{cardNumber}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly transaction amount statistics by card number")
        public Uni<Response> getMonthlyAmountsByCardNumber(
                        @PathParam("cardNumber") String cardNumber,
                        @QueryParam("year") int year) {
                return transactionService.getMonthlyAmountsByCardNumber(cardNumber, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/amount/yearly/card-number/{cardNumber}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly transaction amount statistics by card number")
        public Uni<Response> getYearlyAmountsByCardNumber(
                        @PathParam("cardNumber") String cardNumber,
                        @QueryParam("year") int year) {
                return transactionService.getYearlyAmountsByCardNumber(cardNumber, year)
                                .map(res -> Response.ok(res).build());
        }

        // --- Statistics - Method ---

        @GET
        @Path("/stats/method/monthly")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly payment method statistics")
        public Uni<Response> getMonthlyPaymentMethods(@QueryParam("year") int year) {
                return transactionService.getMonthlyPaymentMethods(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/method/yearly")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly payment method statistics")
        public Uni<Response> getYearlyPaymentMethods(@QueryParam("year") int year) {
                return transactionService.getYearlyPaymentMethods(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/method/monthly/card-number/{cardNumber}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly payment method statistics by card number")
        public Uni<Response> getMonthlyPaymentMethodsByCardNumber(
                        @PathParam("cardNumber") String cardNumber,
                        @QueryParam("year") int year) {
                return transactionService.getMonthlyPaymentMethodsByCardNumber(cardNumber, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/method/yearly/card-number/{cardNumber}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly payment method statistics by card number")
        public Uni<Response> getYearlyPaymentMethodsByCardNumber(
                        @PathParam("cardNumber") String cardNumber,
                        @QueryParam("year") int year) {
                return transactionService.getYearlyPaymentMethodsByCardNumber(cardNumber, year)
                                .map(res -> Response.ok(res).build());
        }

        // --- Statistics - Status ---

        @GET
        @Path("/stats/status/monthly/success")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly successful transaction status statistics")
        public Uni<Response> getMonthlyTransactionStatusSuccess(
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                return transactionService.getMonthlyTransactionStatusSuccess(year, month)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/status/yearly/success")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly successful transaction status statistics")
        public Uni<Response> getYearlyTransactionStatusSuccess(@QueryParam("year") int year) {
                return transactionService.getYearlyTransactionStatusSuccess(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/status/monthly/failed")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly failed transaction status statistics")
        public Uni<Response> getMonthlyTransactionStatusFailed(
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                return transactionService.getMonthlyTransactionStatusFailed(year, month)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/status/yearly/failed")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly failed transaction status statistics")
        public Uni<Response> getYearlyTransactionStatusFailed(@QueryParam("year") int year) {
                return transactionService.getYearlyTransactionStatusFailed(year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/status/monthly/success/card-number/{cardNumber}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly successful transaction status statistics by card number")
        public Uni<Response> getMonthlyTransactionStatusSuccessByCardNumber(
                        @PathParam("cardNumber") String cardNumber,
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                GetTransactionStatusSuccessByCardNumberRequest request = new GetTransactionStatusSuccessByCardNumberRequest(cardNumber, year, month);
                return transactionService.getMonthlyTransactionStatusSuccessByCardNumber(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/status/yearly/success/card-number/{cardNumber}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly successful transaction status statistics by card number")
        public Uni<Response> getYearlyTransactionStatusSuccessByCardNumber(
                        @PathParam("cardNumber") String cardNumber,
                        @QueryParam("year") int year) {
                return transactionService.getYearlyTransactionStatusSuccessByCardNumber(cardNumber, year)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/status/monthly/failed/card-number/{cardNumber}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get monthly failed transaction status statistics by card number")
        public Uni<Response> getMonthlyTransactionStatusFailedByCardNumber(
                        @PathParam("cardNumber") String cardNumber,
                        @QueryParam("year") int year,
                        @QueryParam("month") int month) {
                GetTransactionStatusFailedByCardNumberRequest request = new GetTransactionStatusFailedByCardNumberRequest(cardNumber, year, month);
                return transactionService.getMonthlyTransactionStatusFailedByCardNumber(request)
                                .map(res -> Response.ok(res).build());
        }

        @GET
        @Path("/stats/status/yearly/failed/card-number/{cardNumber}")
        @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
        @Operation(summary = "Get yearly failed transaction status statistics by card number")
        public Uni<Response> getYearlyTransactionStatusFailedByCardNumber(
                        @PathParam("cardNumber") String cardNumber,
                        @QueryParam("year") int year) {
                return transactionService.getYearlyTransactionStatusFailedByCardNumber(cardNumber, year)
                                .map(res -> Response.ok(res).build());
        }
}
