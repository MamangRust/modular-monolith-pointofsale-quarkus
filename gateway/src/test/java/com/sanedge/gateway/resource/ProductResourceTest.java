package com.sanedge.gateway.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.gateway.dto.ProductDto;
import com.sanedge.gateway.service.ProductService;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class ProductResourceTest {
    @Mock
    ProductService productService;
    private ProductResource productResource;

    @BeforeEach
    void setUp() throws Exception {
        productResource = new ProductResource();
        Field f = ProductResource.class.getDeclaredField("productService");
        f.setAccessible(true);
        f.set(productResource, productService);
    }

    @Test
    void findAll_Success() {
        ProductDto.ApiResponsePaginationProduct dto = new ProductDto.ApiResponsePaginationProduct(
                "success", "ok", List.of(), null);
        lenient().when(productService.findAll(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = productResource.findAll(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void findById_Success() {
        ProductDto.ApiResponseProduct dto = new ProductDto.ApiResponseProduct("success", "ok", null);
        lenient().when(productService.findById(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = productResource.findById(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void findByActive_Success() {
        ProductDto.ApiResponsePaginationProductDeleteAt dto = new ProductDto.ApiResponsePaginationProductDeleteAt(
                "success", "ok", List.of(), null);
        lenient().when(productService.findByActive(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = productResource.findByActive(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void findByTrashed_Success() {
        ProductDto.ApiResponsePaginationProductDeleteAt dto = new ProductDto.ApiResponsePaginationProductDeleteAt(
                "success", "ok", List.of(), null);
        lenient().when(productService.findByTrashed(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = productResource.findByTrashed(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void createProduct_Success_Returns201() {
        ProductDto.ApiResponseProduct dto = new ProductDto.ApiResponseProduct("success", "created", null);
        lenient().when(productService.create(any())).thenReturn(Uni.createFrom().item(dto));
        ProductDto.CreateRequest req = new ProductDto.CreateRequest(1, 1, "Test", "Desc", 100, 10, "Brand", 1, null);
        Response r = productResource.createProduct(req).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(201);
    }

    @Test
    void deleteProduct_Success() {
        ProductDto.ApiResponseProductDeleteAt dto = new ProductDto.ApiResponseProductDeleteAt("success", "trashed", null);
        lenient().when(productService.trashed(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = productResource.deleteProduct(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void restoreProduct_Success() {
        ProductDto.ApiResponseProductDeleteAt dto = new ProductDto.ApiResponseProductDeleteAt("success", "restored", null);
        lenient().when(productService.restore(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = productResource.restoreProduct(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void deleteProductPermanent_Success() {
        ProductDto.SimpleResponse dto = new ProductDto.SimpleResponse("success", "deleted");
        lenient().when(productService.deletePermanent(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = productResource.deleteProductPermanent(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }
}
