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

import com.sanedge.gateway.dto.MerchantDto;
import com.sanedge.gateway.telemetry.TelemetryHelper;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class MerchantServiceImplTest {

    @Mock
    private TelemetryHelper telemetryHelper;
    @Mock
    private pb.merchant.MutinyMerchantQueryServiceGrpc.MutinyMerchantQueryServiceStub merchantQueryService;
    @Mock
    private pb.merchant.MutinyMerchantCommandServiceGrpc.MutinyMerchantCommandServiceStub merchantCommandService;

    private MerchantServiceImpl service;

    private void inject(String name, Object value) throws Exception {
        Field f = MerchantServiceImpl.class.getDeclaredField(name);
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
        service = new MerchantServiceImpl();
        inject("telemetryHelper", telemetryHelper);
        inject("merchantQueryService", merchantQueryService);
        inject("merchantCommandService", merchantCommandService);
    }

    @Test
    void listMerchants_PropagatesResponse() {
        pb.merchant.MerchantQuery.ApiResponsePaginationMerchant proto = pb.merchant.MerchantQuery.ApiResponsePaginationMerchant.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(merchantQueryService.findAllMerchant(any(pb.merchant.Merchant.FindAllMerchantRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.listMerchants(new com.sanedge.gateway.domain.requests.FindAllMerchantsRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getMerchant_PropagatesResponse() {
        pb.merchant.Merchant.ApiResponseMerchant proto = pb.merchant.Merchant.ApiResponseMerchant.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(merchantQueryService.findByIdMerchant(any(pb.merchant.Merchant.FindByIdMerchantRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getMerchant(1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getActiveMerchants_PropagatesResponse() {
        pb.merchant.MerchantQuery.ApiResponsePaginationMerchantDeleteAt proto = pb.merchant.MerchantQuery.ApiResponsePaginationMerchantDeleteAt.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(merchantQueryService.findByActive(any(pb.merchant.Merchant.FindAllMerchantRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getActiveMerchants(new com.sanedge.gateway.domain.requests.FindAllMerchantsRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getTrashedMerchants_PropagatesResponse() {
        pb.merchant.MerchantQuery.ApiResponsePaginationMerchantDeleteAt proto = pb.merchant.MerchantQuery.ApiResponsePaginationMerchantDeleteAt.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(merchantQueryService.findByTrashed(any(pb.merchant.Merchant.FindAllMerchantRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getTrashedMerchants(new com.sanedge.gateway.domain.requests.FindAllMerchantsRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void createMerchant_PropagatesResponse() {
        pb.merchant.Merchant.ApiResponseMerchant proto = pb.merchant.Merchant.ApiResponseMerchant.newBuilder()
                .setStatus("success").setMessage("created").build();
        lenient().when(merchantCommandService.createMerchant(any(pb.merchant.MerchantCommand.CreateMerchantRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.createMerchant(new MerchantDto.CreateRequest("Test Merchant", 1)).await().indefinitely();
        assertThat(result.message()).isEqualTo("created");
    }

    @Test
    void updateMerchant_PropagatesResponse() {
        pb.merchant.Merchant.ApiResponseMerchant proto = pb.merchant.Merchant.ApiResponseMerchant.newBuilder()
                .setStatus("success").setMessage("updated").build();
        lenient().when(merchantCommandService.updateMerchant(any(pb.merchant.MerchantCommand.UpdateMerchantRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.updateMerchant(1, new MerchantDto.UpdateRequest(1, "Test", 1, null)).await().indefinitely();
        assertThat(result.message()).isEqualTo("updated");
    }

    @Test
    void deleteMerchant_TrashStub_Propagates() {
        pb.merchant.Merchant.ApiResponseMerchantDeleteAt proto = pb.merchant.Merchant.ApiResponseMerchantDeleteAt.newBuilder()
                .setStatus("success").setMessage("trashed").build();
        lenient().when(merchantCommandService.trashedMerchant(any(pb.merchant.Merchant.FindByIdMerchantRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.deleteMerchant(1).await().indefinitely();
        assertThat(result.message()).isEqualTo("trashed");
    }

    @Test
    void restoreMerchant_RestoreStub_Propagates() {
        pb.merchant.Merchant.ApiResponseMerchantDeleteAt proto = pb.merchant.Merchant.ApiResponseMerchantDeleteAt.newBuilder()
                .setStatus("success").setMessage("restored").build();
        lenient().when(merchantCommandService.restoreMerchant(any(pb.merchant.Merchant.FindByIdMerchantRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.restoreMerchant(1).await().indefinitely();
        assertThat(result.message()).isEqualTo("restored");
    }

    @Test
    void deleteMerchantPermanent_Propagates() {
        pb.merchant.MerchantCommand.ApiResponseMerchantDelete proto = pb.merchant.MerchantCommand.ApiResponseMerchantDelete.newBuilder()
                .setStatus("success").setMessage("deleted").build();
        lenient().when(merchantCommandService.deleteMerchantPermanent(any(pb.merchant.Merchant.FindByIdMerchantRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.deleteMerchantPermanent(1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }
}
