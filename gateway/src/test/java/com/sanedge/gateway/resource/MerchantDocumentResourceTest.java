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

import com.sanedge.gateway.dto.MerchantDocumentDto;
import com.sanedge.gateway.service.MerchantDocumentService;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class MerchantDocumentResourceTest {
    @Mock
    MerchantDocumentService merchantDocumentService;
    private MerchantDocumentResource merchantDocumentResource;

    @BeforeEach
    void setUp() throws Exception {
        merchantDocumentResource = new MerchantDocumentResource();
        Field f = MerchantDocumentResource.class.getDeclaredField("merchantDocumentService");
        f.setAccessible(true);
        f.set(merchantDocumentResource, merchantDocumentService);
    }

    @Test
    void listMerchantDocuments_Success() {
        MerchantDocumentDto.ApiResponsePaginationMerchantDocument dto = new MerchantDocumentDto.ApiResponsePaginationMerchantDocument(
                "success", "ok", List.of(), null);
        lenient().when(merchantDocumentService.listMerchantDocuments(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = merchantDocumentResource.listMerchantDocuments(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getMerchantDocument_Success() {
        MerchantDocumentDto.ApiResponseMerchantDocument dto = new MerchantDocumentDto.ApiResponseMerchantDocument(
                "success", "ok", null);
        lenient().when(merchantDocumentService.getMerchantDocument(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = merchantDocumentResource.getMerchantDocument(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void createMerchantDocument_Success_Returns201() {
        MerchantDocumentDto.ApiResponseMerchantDocument dto = new MerchantDocumentDto.ApiResponseMerchantDocument(
                "success", "created", null);
        lenient().when(merchantDocumentService.createMerchantDocument(any())).thenReturn(Uni.createFrom().item(dto));
        MerchantDocumentDto.CreateRequest req = new MerchantDocumentDto.CreateRequest(1, "ktp", "url");
        Response r = merchantDocumentResource.createMerchantDocument(req).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(201);
    }

    @Test
    void deleteMerchantDocument_Success() {
        MerchantDocumentDto.ApiResponseMerchantDocumentDeleteAt dto = new MerchantDocumentDto.ApiResponseMerchantDocumentDeleteAt(
                "success", "trashed", null);
        lenient().when(merchantDocumentService.deleteMerchantDocument(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = merchantDocumentResource.deleteMerchantDocument(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void restoreMerchantDocument_Success() {
        MerchantDocumentDto.ApiResponseMerchantDocumentDeleteAt dto = new MerchantDocumentDto.ApiResponseMerchantDocumentDeleteAt(
                "success", "restored", null);
        lenient().when(merchantDocumentService.restoreMerchantDocument(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = merchantDocumentResource.restoreMerchantDocument(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }
}
