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

import com.sanedge.gateway.dto.ProductDto;
import com.sanedge.gateway.telemetry.TelemetryHelper;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private TelemetryHelper telemetryHelper;
    @Mock
    private pb.product.MutinyProductServiceGrpc.MutinyProductServiceStub productQueryService;
    @Mock
    private pb.product.MutinyProductCommandServiceGrpc.MutinyProductCommandServiceStub productCommandService;

    private ProductServiceImpl service;

    private void inject(String name, Object value) throws Exception {
        Field f = ProductServiceImpl.class.getDeclaredField(name);
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
        service = new ProductServiceImpl();
        inject("telemetryHelper", telemetryHelper);
        inject("productQueryService", productQueryService);
        inject("productCommandService", productCommandService);
    }

    @Test
    void findAll_PropagatesResponse() {
        pb.product.ProductQuery.ApiResponsePaginationProduct proto = pb.product.ProductQuery.ApiResponsePaginationProduct.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(productQueryService.findAll(any(pb.product.Product.FindAllProductRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.findAll(new com.sanedge.gateway.domain.requests.FindAllProductsRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void findById_PropagatesResponse() {
        pb.product.Product.ApiResponseProduct proto = pb.product.Product.ApiResponseProduct.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(productQueryService.findById(any(pb.product.Product.FindByIdProductRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.findById(1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void findByActive_PropagatesResponse() {
        pb.product.ProductQuery.ApiResponsePaginationProductDeleteAt proto = pb.product.ProductQuery.ApiResponsePaginationProductDeleteAt.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(productQueryService.findByActive(any(pb.product.Product.FindAllProductRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.findByActive(new com.sanedge.gateway.domain.requests.FindAllProductsRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void findByTrashed_PropagatesResponse() {
        pb.product.ProductQuery.ApiResponsePaginationProductDeleteAt proto = pb.product.ProductQuery.ApiResponsePaginationProductDeleteAt.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(productQueryService.findByTrashed(any(pb.product.Product.FindAllProductRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.findByTrashed(new com.sanedge.gateway.domain.requests.FindAllProductsRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void create_PropagatesResponse() {
        pb.product.Product.ApiResponseProduct proto = pb.product.Product.ApiResponseProduct.newBuilder()
                .setStatus("success").setMessage("created").build();
        lenient().when(productCommandService.create(any(pb.product.ProductCommand.CreateProductRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.create(new ProductDto.CreateRequest(1, 1, "Test", "Desc", 100, 10, "Brand", 1, null)).await().indefinitely();
        assertThat(result.message()).isEqualTo("created");
    }

    @Test
    void trashed_TrashStub_Propagates() {
        pb.product.Product.ApiResponseProductDeleteAt proto = pb.product.Product.ApiResponseProductDeleteAt.newBuilder()
                .setStatus("success").setMessage("trashed").build();
        lenient().when(productCommandService.trashedProduct(any(pb.product.Product.FindByIdProductRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.trashed(1).await().indefinitely();
        assertThat(result.message()).isEqualTo("trashed");
    }

    @Test
    void restore_RestoreStub_Propagates() {
        pb.product.Product.ApiResponseProductDeleteAt proto = pb.product.Product.ApiResponseProductDeleteAt.newBuilder()
                .setStatus("success").setMessage("restored").build();
        lenient().when(productCommandService.restoreProduct(any(pb.product.Product.FindByIdProductRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.restore(1).await().indefinitely();
        assertThat(result.message()).isEqualTo("restored");
    }

    @Test
    void deletePermanent_Propagates() {
        pb.product.ProductCommand.ApiResponseProductDelete proto = pb.product.ProductCommand.ApiResponseProductDelete.newBuilder()
                .setStatus("success").setMessage("deleted").build();
        lenient().when(productCommandService.deleteProductPermanent(any(pb.product.Product.FindByIdProductRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.deletePermanent(1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }
}
