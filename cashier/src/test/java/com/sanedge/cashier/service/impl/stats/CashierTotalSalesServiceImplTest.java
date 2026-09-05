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

import com.sanedge.cashier.domain.requests.MonthTotalSales;
import com.sanedge.cashier.domain.response.CashierResponseMonthTotalSales;
import com.sanedge.cashier.domain.response.CashierResponseYearTotalSales;
import com.sanedge.cashier.entity.CashierMonthTotalSales;
import com.sanedge.cashier.entity.CashierYearTotalSales;
import com.sanedge.cashier.repository.stats.CashierTotalSalesRepository;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CashierTotalSalesServiceImplTest {

    @Mock
    private CashierTotalSalesRepository cashierTotalSalesRepository;

    @Mock
    private TracingMetrics tracingMetrics;

    private CashierTotalSalesServiceImpl cashierTotalSalesService;

    @BeforeEach
    void setUp() {
        cashierTotalSalesService = new CashierTotalSalesServiceImpl(cashierTotalSalesRepository, tracingMetrics);

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

    private CashierMonthTotalSales createMockMonthTotalSales(String year, String month, Long totalSales) {
        CashierMonthTotalSales entity = new CashierMonthTotalSales();
        entity.setYear(year);
        entity.setMonth(month);
        entity.setTotalSales(totalSales);
        return entity;
    }

    private CashierYearTotalSales createMockYearTotalSales(String year, Long totalSales) {
        CashierYearTotalSales entity = new CashierYearTotalSales();
        entity.setYear(year);
        entity.setTotalSales(totalSales);
        return entity;
    }

    @Test
    void findMonthlyTotalSales_nullYear_returnsError() {
        MonthTotalSales req = new MonthTotalSales();
        req.setYear(null);
        req.setMonth(1);

        ApiResponse<List<CashierResponseMonthTotalSales>> response = cashierTotalSalesService.findMonthlyTotalSales(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("must not be null");
    }

    @Test
    void findMonthlyTotalSales_nullMonth_returnsError() {
        MonthTotalSales req = new MonthTotalSales();
        req.setYear(2024);
        req.setMonth(null);

        ApiResponse<List<CashierResponseMonthTotalSales>> response = cashierTotalSalesService.findMonthlyTotalSales(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("must not be null");
    }

    @Test
    void findMonthlyTotalSales_success() {
        MonthTotalSales req = new MonthTotalSales();
        req.setYear(2024);
        req.setMonth(1);

        List<CashierMonthTotalSales> mockList = List.of(
                createMockMonthTotalSales("2024", "1", 5000L));

        when(cashierTotalSalesRepository.findMonthTotalSales(any()))
                .thenReturn(Uni.createFrom().item(mockList));

        ApiResponse<List<CashierResponseMonthTotalSales>> response = cashierTotalSalesService.findMonthlyTotalSales(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).getMonth()).isEqualTo("1");
        assertThat(response.data().get(0).getTotalSales()).isEqualTo(5000L);
    }

    @Test
    void findMonthlyTotalSales_error_returnsErrorResponse() {
        MonthTotalSales req = new MonthTotalSales();
        req.setYear(2024);
        req.setMonth(1);

        when(cashierTotalSalesRepository.findMonthTotalSales(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        ApiResponse<List<CashierResponseMonthTotalSales>> response = cashierTotalSalesService.findMonthlyTotalSales(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("DB error");
    }

    @Test
    void findYearlyTotalSales_nullYear_returnsError() {
        ApiResponse<List<CashierResponseYearTotalSales>> response = cashierTotalSalesService.findYearlyTotalSales(null)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("must not be null");
    }

    @Test
    void findYearlyTotalSales_success() {
        List<CashierYearTotalSales> mockList = List.of(
                createMockYearTotalSales("2024", 60000L));

        when(cashierTotalSalesRepository.findYearTotalSales(2024, 2023))
                .thenReturn(Uni.createFrom().item(mockList));

        ApiResponse<List<CashierResponseYearTotalSales>> response = cashierTotalSalesService.findYearlyTotalSales(2024)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).getYear()).isEqualTo("2024");
        assertThat(response.data().get(0).getTotalSales()).isEqualTo(60000L);
    }

    @Test
    void findYearlyTotalSales_error_returnsErrorResponse() {
        when(cashierTotalSalesRepository.findYearTotalSales(2024, 2023))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        ApiResponse<List<CashierResponseYearTotalSales>> response = cashierTotalSalesService.findYearlyTotalSales(2024)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("DB error");
    }

    @Test
    void findYearlyTotalSales_emptyList_returnsEmptyData() {
        when(cashierTotalSalesRepository.findYearTotalSales(2024, 2023))
                .thenReturn(Uni.createFrom().item(List.of()));

        ApiResponse<List<CashierResponseYearTotalSales>> response = cashierTotalSalesService.findYearlyTotalSales(2024)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).isEmpty();
    }
}
