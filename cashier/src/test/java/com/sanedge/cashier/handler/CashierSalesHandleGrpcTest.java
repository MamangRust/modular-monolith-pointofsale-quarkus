package com.sanedge.cashier.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.cashier.domain.response.CashierResponseMonthSales;
import com.sanedge.cashier.domain.response.CashierResponseYearSales;
import com.sanedge.cashier.service.stats.CashierSalesService;
import com.sanedge.cashier.service.statsbyid.CashierSalesByIdService;
import com.sanedge.cashier.service.statsbymerchant.CashierSalesByMerchantService;
import com.sanedge.common.domain.response.ApiResponse;

import io.smallrye.mutiny.Uni;
import pb.cashier.Cashier.FindYearCashier;
import pb.cashier.Cashier.FindYearCashierById;
import pb.cashier.Cashier.FindYearCashierByMerchant;

@ExtendWith(MockitoExtension.class)
class CashierSalesHandleGrpcTest {

    @Mock
    private CashierSalesService cashierSalesService;

    @Mock
    private CashierSalesByIdService cashierSalesByIdService;

    @Mock
    private CashierSalesByMerchantService cashierSalesByMerchantService;

    private CashierSalesGrpcHandler salesHandler;

    @BeforeEach
    void setUp() {
        salesHandler = new CashierSalesGrpcHandler();
        injectField(salesHandler, "cashierSalesService", cashierSalesService);
        injectField(salesHandler, "cashierSalesByIdService", cashierSalesByIdService);
        injectField(salesHandler, "cashierSalesByMerchantService", cashierSalesByMerchantService);
    }

    @Test
    void findMonthSales_success() {
        FindYearCashier request = FindYearCashier.newBuilder().setYear(2024).build();
        List<CashierResponseMonthSales> mockList = List.of(
                CashierResponseMonthSales.builder().cashierId(1).cashierName("C1").month("1").totalSales(1000L).build());
        when(cashierSalesService.findMonthlySales(2024))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("Found", mockList)));

        pb.cashier.Cashier.ApiResponseCashierMonthSales response = salesHandler.findMonthSales(request).await()
                .indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void findYearSales_success() {
        FindYearCashier request = FindYearCashier.newBuilder().setYear(2024).build();
        List<CashierResponseYearSales> mockList = List.of(
                CashierResponseYearSales.builder().cashierId(1).cashierName("C1").year("2024").totalSales(12000L).build());
        when(cashierSalesService.findYearlySales(2024))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("Found", mockList)));

        pb.cashier.Cashier.ApiResponseCashierYearSales response = salesHandler.findYearSales(request).await()
                .indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void findMonthSalesByMerchant_success() {
        FindYearCashierByMerchant request = FindYearCashierByMerchant.newBuilder()
                .setYear(2024).setMerchantId(1).build();
        List<CashierResponseMonthSales> mockList = List.of(
                CashierResponseMonthSales.builder().cashierId(1).cashierName("C1").month("1").totalSales(1000L).build());
        when(cashierSalesByMerchantService.findMonthlyCashierByMerchant(any()))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("Found", mockList)));

        pb.cashier.Cashier.ApiResponseCashierMonthSales response = salesHandler.findMonthSalesByMerchant(request)
                .await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void findYearSalesByMerchant_success() {
        FindYearCashierByMerchant request = FindYearCashierByMerchant.newBuilder()
                .setYear(2024).setMerchantId(1).build();
        List<CashierResponseYearSales> mockList = List.of(
                CashierResponseYearSales.builder().cashierId(1).cashierName("C1").year("2024").totalSales(12000L).build());
        when(cashierSalesByMerchantService.findYearlyCashierByMerchant(any()))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("Found", mockList)));

        pb.cashier.Cashier.ApiResponseCashierYearSales response = salesHandler.findYearSalesByMerchant(request)
                .await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void findMonthSalesById_success() {
        FindYearCashierById request = FindYearCashierById.newBuilder()
                .setYear(2024).setCashierId(1).build();
        List<CashierResponseMonthSales> mockList = List.of(
                CashierResponseMonthSales.builder().cashierId(1).cashierName("C1").month("1").totalSales(1000L).build());
        when(cashierSalesByIdService.findMonthlyCashierById(any()))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("Found", mockList)));

        pb.cashier.Cashier.ApiResponseCashierMonthSales response = salesHandler.findMonthSalesById(request)
                .await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void findYearSalesById_success() {
        FindYearCashierById request = FindYearCashierById.newBuilder()
                .setYear(2024).setCashierId(1).build();
        List<CashierResponseYearSales> mockList = List.of(
                CashierResponseYearSales.builder().cashierId(1).cashierName("C1").year("2024").totalSales(12000L).build());
        when(cashierSalesByIdService.findYearlyCashierById(any()))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("Found", mockList)));

        pb.cashier.Cashier.ApiResponseCashierYearSales response = salesHandler.findYearSalesById(request)
                .await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + fieldName, e);
        }
    }
}
