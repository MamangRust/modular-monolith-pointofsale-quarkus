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

import com.sanedge.cashier.domain.response.CashierResponseMonthTotalSales;
import com.sanedge.cashier.domain.response.CashierResponseYearTotalSales;
import com.sanedge.cashier.service.stats.CashierTotalSalesService;
import com.sanedge.cashier.service.statsbyid.CashierTotalSalesByIdService;
import com.sanedge.cashier.service.statsbymerchant.CashierTotalSalesByMerchantService;
import com.sanedge.common.domain.response.ApiResponse;

import io.smallrye.mutiny.Uni;
import pb.cashier.Cashier.FindYearMonthTotalSales;
import pb.cashier.Cashier.FindYearMonthTotalSalesById;
import pb.cashier.Cashier.FindYearMonthTotalSalesByMerchant;
import pb.cashier.Cashier.FindYearTotalSales;
import pb.cashier.Cashier.FindYearTotalSalesById;
import pb.cashier.Cashier.FindYearTotalSalesByMerchant;
import pb.cashier.stats.CashierTotalSales.ApiResponseCashierMonthlyTotalSales;
import pb.cashier.stats.CashierTotalSales.ApiResponseCashierYearlyTotalSales;

@ExtendWith(MockitoExtension.class)
class CashierTotalSalesHandleGrpcTest {

    @Mock
    private CashierTotalSalesService cashierTotalSalesService;

    @Mock
    private CashierTotalSalesByIdService cashierTotalSalesByIdService;

    @Mock
    private CashierTotalSalesByMerchantService cashierTotalSalesByMerchantService;

    private CashierTotalSalesGrpcHandler totalSalesHandler;

    @BeforeEach
    void setUp() {
        totalSalesHandler = new CashierTotalSalesGrpcHandler();
        injectField(totalSalesHandler, "cashierTotalSalesService", cashierTotalSalesService);
        injectField(totalSalesHandler, "cashierTotalSalesByIdService", cashierTotalSalesByIdService);
        injectField(totalSalesHandler, "cashierTotalSalesByMerchantService", cashierTotalSalesByMerchantService);
    }

    @Test
    void findMonthlyTotalSales_success() {
        FindYearMonthTotalSales request = FindYearMonthTotalSales.newBuilder().setYear(2024).setMonth(1).build();
        List<CashierResponseMonthTotalSales> mockList = List.of(
                CashierResponseMonthTotalSales.builder().year("2024").month("1").totalSales(5000L).build());
        when(cashierTotalSalesService.findMonthlyTotalSales(any()))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("Found", mockList)));

        ApiResponseCashierMonthlyTotalSales response = totalSalesHandler.findMonthlyTotalSales(request).await()
                .indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void findYearlyTotalSales_success() {
        FindYearTotalSales request = FindYearTotalSales.newBuilder().setYear(2024).build();
        List<CashierResponseYearTotalSales> mockList = List.of(
                CashierResponseYearTotalSales.builder().year("2024").totalSales(60000L).build());
        when(cashierTotalSalesService.findYearlyTotalSales(2024))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("Found", mockList)));

        ApiResponseCashierYearlyTotalSales response = totalSalesHandler.findYearlyTotalSales(request).await()
                .indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void findMonthlyTotalSalesById_success() {
        FindYearMonthTotalSalesById request = FindYearMonthTotalSalesById.newBuilder()
                .setYear(2024).setMonth(1).setCashierId(1).build();
        List<CashierResponseMonthTotalSales> mockList = List.of(
                CashierResponseMonthTotalSales.builder().year("2024").month("1").totalSales(5000L).build());
        when(cashierTotalSalesByIdService.findMonthlyTotalSalesById(any()))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("Found", mockList)));

        ApiResponseCashierMonthlyTotalSales response = totalSalesHandler.findMonthlyTotalSalesById(request).await()
                .indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void findYearlyTotalSalesById_success() {
        FindYearTotalSalesById request = FindYearTotalSalesById.newBuilder()
                .setYear(2024).setCashierId(1).build();
        List<CashierResponseYearTotalSales> mockList = List.of(
                CashierResponseYearTotalSales.builder().year("2024").totalSales(60000L).build());
        when(cashierTotalSalesByIdService.findYearlyTotalSalesById(any()))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("Found", mockList)));

        ApiResponseCashierYearlyTotalSales response = totalSalesHandler.findYearlyTotalSalesById(request).await()
                .indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void findMonthlyTotalSalesByMerchant_success() {
        FindYearMonthTotalSalesByMerchant request = FindYearMonthTotalSalesByMerchant.newBuilder()
                .setYear(2024).setMonth(1).setMerchantId(1).build();
        List<CashierResponseMonthTotalSales> mockList = List.of(
                CashierResponseMonthTotalSales.builder().year("2024").month("1").totalSales(5000L).build());
        when(cashierTotalSalesByMerchantService.findMonthlyTotalSalesByMerchant(any()))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("Found", mockList)));

        ApiResponseCashierMonthlyTotalSales response = totalSalesHandler.findMonthlyTotalSalesByMerchant(request).await()
                .indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void findYearlyTotalSalesByMerchant_success() {
        FindYearTotalSalesByMerchant request = FindYearTotalSalesByMerchant.newBuilder()
                .setYear(2024).setMerchantId(1).build();
        List<CashierResponseYearTotalSales> mockList = List.of(
                CashierResponseYearTotalSales.builder().year("2024").totalSales(60000L).build());
        when(cashierTotalSalesByMerchantService.findYearlyTotalSalesByMerchant(any()))
                .thenReturn(Uni.createFrom().item(ApiResponse.success("Found", mockList)));

        ApiResponseCashierYearlyTotalSales response = totalSalesHandler.findYearlyTotalSalesByMerchant(request).await()
                .indefinitely();
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
