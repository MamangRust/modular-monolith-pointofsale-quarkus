package com.sanedge.cashier.service.impl.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.cashier.domain.response.CashierResponseMonthSales;
import com.sanedge.cashier.domain.response.CashierResponseYearSales;
import com.sanedge.cashier.entity.CashierMonthSales;
import com.sanedge.cashier.entity.CashierYearSales;
import com.sanedge.cashier.repository.stats.CashierSalesRepository;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CashierSalesServiceImplTest {

    @Mock
    private CashierSalesRepository cashierSalesRepository;

    @Mock
    private TracingMetrics tracingMetrics;

    private CashierSalesServiceImpl cashierSalesService;

    @BeforeEach
    void setUp() {
        cashierSalesService = new CashierSalesServiceImpl(cashierSalesRepository, tracingMetrics);

        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics)
                .traceAndMeasure(
                        anyString(),
                        anyString(),
                        any(Attributes.class),
                        any());
    }

    private CashierMonthSales createMockMonthSales(Integer cashierId, String cashierName, String month, Long totalSales) {
        CashierMonthSales entity = new CashierMonthSales();
        entity.setCashierId(cashierId);
        entity.setCashierName(cashierName);
        entity.setMonth(month);
        entity.setTotalSales(totalSales);
        return entity;
    }

    private CashierYearSales createMockYearSales(Integer cashierId, String cashierName, String year, Long totalSales) {
        CashierYearSales entity = new CashierYearSales();
        entity.setCashierId(cashierId);
        entity.setCashierName(cashierName);
        entity.setYear(year);
        entity.setTotalSales(totalSales);
        return entity;
    }

    @Test
    void findMonthlySales_success() {
        List<CashierMonthSales> mockList = List.of(
                createMockMonthSales(1, "Cashier1", "1", 1000L));

        when(cashierSalesRepository.findMonthSales(any()))
                .thenReturn(Uni.createFrom().item(mockList));

        ApiResponse<List<CashierResponseMonthSales>> response = cashierSalesService.findMonthlySales(2024).await()
                .indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).getCashierName()).isEqualTo("Cashier1");
        assertThat(response.data().get(0).getMonth()).isEqualTo("1");
    }

    @Test
    void findMonthlySales_error_returnsErrorResponse() {
        when(cashierSalesRepository.findMonthSales(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        ApiResponse<List<CashierResponseMonthSales>> response = cashierSalesService.findMonthlySales(2024).await()
                .indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("DB error");
    }

    @Test
    void findMonthlySales_emptyList_returnsEmptyData() {
        when(cashierSalesRepository.findMonthSales(any()))
                .thenReturn(Uni.createFrom().item(List.of()));

        ApiResponse<List<CashierResponseMonthSales>> response = cashierSalesService.findMonthlySales(2024).await()
                .indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).isEmpty();
    }

    @Test
    void findYearlySales_success() {
        List<CashierYearSales> mockList = List.of(
                createMockYearSales(1, "Cashier1", "2024", 12000L));

        when(cashierSalesRepository.findYearSales(2024))
                .thenReturn(Uni.createFrom().item(mockList));

        ApiResponse<List<CashierResponseYearSales>> response = cashierSalesService.findYearlySales(2024).await()
                .indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).getCashierName()).isEqualTo("Cashier1");
        assertThat(response.data().get(0).getYear()).isEqualTo("2024");
    }

    @Test
    void findYearlySales_error_returnsErrorResponse() {
        when(cashierSalesRepository.findYearSales(2024))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        ApiResponse<List<CashierResponseYearSales>> response = cashierSalesService.findYearlySales(2024).await()
                .indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("DB error");
    }

    @Test
    void findYearlySales_emptyList_returnsEmptyData() {
        when(cashierSalesRepository.findYearSales(2024))
                .thenReturn(Uni.createFrom().item(List.of()));

        ApiResponse<List<CashierResponseYearSales>> response = cashierSalesService.findYearlySales(2024).await()
                .indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).isEmpty();
    }
}
