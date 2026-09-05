package com.sanedge.gateway.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.gateway.dto.TransactionDto;
import com.sanedge.gateway.service.TransactionService;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class TransactionResourceTest {
    @Mock
    TransactionService transactionService;
    private TransactionResource transactionResource;

    @BeforeEach
    void setUp() throws Exception {
        transactionResource = new TransactionResource();
        Field f = TransactionResource.class.getDeclaredField("transactionService");
        f.setAccessible(true);
        f.set(transactionResource, transactionService);
    }

    @Test
    void listTransactions_Success() {
        TransactionDto.ApiResponsePaginationTransaction dto = new TransactionDto.ApiResponsePaginationTransaction(
                "success", "ok", List.of(), null);
        lenient().when(transactionService.listTransactions(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = transactionResource.listTransactions(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getTransaction_Success() {
        TransactionDto.ApiResponseTransaction dto = new TransactionDto.ApiResponseTransaction("success", "ok", null);
        lenient().when(transactionService.getTransaction(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = transactionResource.getTransaction(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getActiveTransactions_Success() {
        TransactionDto.ApiResponsePaginationTransactionDeleteAt dto = new TransactionDto.ApiResponsePaginationTransactionDeleteAt(
                "success", "ok", List.of(), null);
        lenient().when(transactionService.getActiveTransactions(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = transactionResource.getActiveTransactions(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getTrashedTransactions_Success() {
        TransactionDto.ApiResponsePaginationTransactionDeleteAt dto = new TransactionDto.ApiResponsePaginationTransactionDeleteAt(
                "success", "ok", List.of(), null);
        lenient().when(transactionService.getTrashedTransactions(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = transactionResource.getTrashedTransactions(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void createTransaction_Success_Returns201() {
        TransactionDto.ApiResponseTransaction dto = new TransactionDto.ApiResponseTransaction("success", "created", null);
        lenient().when(transactionService.createTransaction(any())).thenReturn(Uni.createFrom().item(dto));
        TransactionDto.CreateRequest req = new TransactionDto.CreateRequest(null, "1234", 100, "cash", 1, null, null);
        Response r = transactionResource.createTransaction(req).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(201);
    }

    @Test
    void deleteTransaction_Success() {
        TransactionDto.ApiResponseTransactionDeleteAt dto = new TransactionDto.ApiResponseTransactionDeleteAt("success", "trashed", null);
        lenient().when(transactionService.deleteTransaction(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = transactionResource.deleteTransaction(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void restoreTransaction_Success() {
        TransactionDto.ApiResponseTransactionDeleteAt dto = new TransactionDto.ApiResponseTransactionDeleteAt("success", "restored", null);
        lenient().when(transactionService.restoreTransaction(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = transactionResource.restoreTransaction(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }
}
