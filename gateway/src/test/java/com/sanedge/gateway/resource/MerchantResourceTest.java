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

import com.sanedge.gateway.dto.MerchantDto;
import com.sanedge.gateway.service.MerchantService;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class MerchantResourceTest {
    @Mock
    MerchantService merchantService;
    private MerchantResource merchantResource;

    @BeforeEach
    void setUp() throws Exception {
        merchantResource = new MerchantResource();
        Field f = MerchantResource.class.getDeclaredField("merchantService");
        f.setAccessible(true);
        f.set(merchantResource, merchantService);
    }

    @Test
    void listMerchants_Success() {
        MerchantDto.ApiResponsePaginationMerchant dto = new MerchantDto.ApiResponsePaginationMerchant(
                "success", "ok", List.of(), null);
        lenient().when(merchantService.listMerchants(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = merchantResource.listMerchants(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getMerchant_Success() {
        MerchantDto.ApiResponseMerchant dto = new MerchantDto.ApiResponseMerchant("success", "ok", null);
        lenient().when(merchantService.getMerchant(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = merchantResource.getMerchant(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getActiveMerchants_Success() {
        MerchantDto.ApiResponsePaginationMerchantDeleteAt dto = new MerchantDto.ApiResponsePaginationMerchantDeleteAt(
                "success", "ok", List.of(), null);
        lenient().when(merchantService.getActiveMerchants(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = merchantResource.getActiveMerchants(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getTrashedMerchants_Success() {
        MerchantDto.ApiResponsePaginationMerchantDeleteAt dto = new MerchantDto.ApiResponsePaginationMerchantDeleteAt(
                "success", "ok", List.of(), null);
        lenient().when(merchantService.getTrashedMerchants(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = merchantResource.getTrashedMerchants(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void createMerchant_Success_Returns201() {
        MerchantDto.ApiResponseMerchant dto = new MerchantDto.ApiResponseMerchant("success", "created", null);
        lenient().when(merchantService.createMerchant(any())).thenReturn(Uni.createFrom().item(dto));
        MerchantDto.CreateRequest req = new MerchantDto.CreateRequest("Test", 1);
        Response r = merchantResource.createMerchant(req).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(201);
    }

    @Test
    void deleteMerchant_Success() {
        MerchantDto.ApiResponseMerchantDeleteAt dto = new MerchantDto.ApiResponseMerchantDeleteAt("success", "trashed", null);
        lenient().when(merchantService.deleteMerchant(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = merchantResource.deleteMerchant(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void restoreMerchant_Success() {
        MerchantDto.ApiResponseMerchantDeleteAt dto = new MerchantDto.ApiResponseMerchantDeleteAt("success", "restored", null);
        lenient().when(merchantService.restoreMerchant(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = merchantResource.restoreMerchant(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void deleteMerchantPermanent_Success() {
        MerchantDto.SimpleResponse dto = new MerchantDto.SimpleResponse("success", "deleted");
        lenient().when(merchantService.deleteMerchantPermanent(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = merchantResource.deleteMerchantPermanent(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void restoreAllMerchant_Success() {
        MerchantDto.SimpleResponse dto = new MerchantDto.SimpleResponse("success", "restored");
        lenient().when(merchantService.restoreAllMerchant()).thenReturn(Uni.createFrom().item(dto));
        Response r = merchantResource.restoreAllMerchant().await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }
}
