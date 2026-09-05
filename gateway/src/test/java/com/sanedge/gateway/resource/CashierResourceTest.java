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

import com.sanedge.gateway.dto.CashierDto;
import com.sanedge.gateway.service.CashierService;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class CashierResourceTest {
    @Mock
    CashierService cashierService;
    private CashierResource cashierResource;

    @BeforeEach
    void setUp() throws Exception {
        cashierResource = new CashierResource();
        Field f = CashierResource.class.getDeclaredField("cashierService");
        f.setAccessible(true);
        f.set(cashierResource, cashierService);
    }

    @Test
    void listCashiers_Success() {
        CashierDto.ApiResponsePaginationCashier dto = new CashierDto.ApiResponsePaginationCashier(
                "success", "ok", List.of(), null);
        lenient().when(cashierService.listCashiers(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = cashierResource.listCashiers(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getCashier_Success() {
        CashierDto.ApiResponseCashier dto = new CashierDto.ApiResponseCashier("success", "ok", null);
        lenient().when(cashierService.getCashier(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = cashierResource.getCashier(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void createCashier_Success_Returns201() {
        CashierDto.ApiResponseCashier dto = new CashierDto.ApiResponseCashier("success", "created", null);
        lenient().when(cashierService.createCashier(any())).thenReturn(Uni.createFrom().item(dto));
        CashierDto.CreateRequest req = new CashierDto.CreateRequest(1, 1, "Test Cashier");
        Response r = cashierResource.createCashier(req).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(201);
    }

    @Test
    void deleteCashier_Success() {
        CashierDto.ApiResponseCashierDeleteAt dto = new CashierDto.ApiResponseCashierDeleteAt("success", "trashed", null);
        lenient().when(cashierService.deleteCashier(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = cashierResource.deleteCashier(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void restoreCashier_Success() {
        CashierDto.ApiResponseCashierDeleteAt dto = new CashierDto.ApiResponseCashierDeleteAt("success", "restored", null);
        lenient().when(cashierService.restoreCashier(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = cashierResource.restoreCashier(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }
}
