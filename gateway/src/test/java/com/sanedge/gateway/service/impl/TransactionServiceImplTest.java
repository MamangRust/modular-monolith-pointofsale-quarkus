package com.sanedge.gateway.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.gateway.dto.TransactionDto;
import com.sanedge.gateway.telemetry.TelemetryHelper;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TelemetryHelper telemetryHelper;
    @Mock
    private pb.transaction.MutinyTransactionQueryServiceGrpc.MutinyTransactionQueryServiceStub transactionQueryService;
    @Mock
    private pb.transaction.MutinyTransactionCommandServiceGrpc.MutinyTransactionCommandServiceStub transactionCommandService;
    @Mock
    private pb.transaction.stats.MutinyTransactionStatsAmountServiceGrpc.MutinyTransactionStatsAmountServiceStub transactionStatsAmountService;
    @Mock
    private pb.transaction.stats.MutinyTransactionStatsMethodServiceGrpc.MutinyTransactionStatsMethodServiceStub transactionStatsMethodService;
    @Mock
    private pb.transaction.stats.MutinyTransactionStatsStatusServiceGrpc.MutinyTransactionStatsStatusServiceStub transactionStatsStatusService;

    private TransactionServiceImpl service;

    private void inject(String name, Object value) throws Exception {
        Field f = TransactionServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(telemetryHelper.traceAndMetric(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Uni<?>> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
        service = new TransactionServiceImpl();
        inject("telemetryHelper", telemetryHelper);
        inject("transactionQueryService", transactionQueryService);
        inject("transactionCommandService", transactionCommandService);
        inject("transactionStatsAmountService", transactionStatsAmountService);
        inject("transactionStatsMethodService", transactionStatsMethodService);
        inject("transactionStatsStatusService", transactionStatsStatusService);
    }

    @Test
    void listTransactions_PropagatesResponse() {
        pb.transaction.TransactionQuery.ApiResponsePaginationTransaction proto = pb.transaction.TransactionQuery.ApiResponsePaginationTransaction.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(transactionQueryService.findAllTransaction(any(pb.transaction.TransactionQuery.FindAllTransactionRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.listTransactions(new com.sanedge.gateway.domain.requests.FindAllTransactionsRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getTransaction_PropagatesResponse() {
        pb.transaction.Transaction.ApiResponseTransaction proto = pb.transaction.Transaction.ApiResponseTransaction.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(transactionQueryService.findByIdTransaction(any(pb.transaction.Transaction.FindByIdTransactionRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getTransaction(1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void createTransaction_PropagatesResponse() {
        pb.transaction.Transaction.ApiResponseTransaction proto = pb.transaction.Transaction.ApiResponseTransaction.newBuilder()
                .setStatus("success").setMessage("created").build();
        lenient().when(transactionCommandService.createTransaction(any(pb.transaction.TransactionCommand.CreateTransactionRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.createTransaction(new TransactionDto.CreateRequest(null, "1234", 100, "cash", 1, null, null)).await().indefinitely();
        assertThat(result.message()).isEqualTo("created");
    }

    @Test
    void deleteTransaction_TrashStub_Propagates() {
        pb.transaction.Transaction.ApiResponseTransactionDeleteAt proto = pb.transaction.Transaction.ApiResponseTransactionDeleteAt.newBuilder()
                .setStatus("success").setMessage("trashed").build();
        lenient().when(transactionCommandService.trashedTransaction(any(pb.transaction.Transaction.FindByIdTransactionRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.deleteTransaction(1).await().indefinitely();
        assertThat(result.message()).isEqualTo("trashed");
    }

    @Test
    void restoreTransaction_RestoreStub_Propagates() {
        pb.transaction.Transaction.ApiResponseTransactionDeleteAt proto = pb.transaction.Transaction.ApiResponseTransactionDeleteAt.newBuilder()
                .setStatus("success").setMessage("restored").build();
        lenient().when(transactionCommandService.restoreTransaction(any(pb.transaction.Transaction.FindByIdTransactionRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.restoreTransaction(1).await().indefinitely();
        assertThat(result.message()).isEqualTo("restored");
    }
}
