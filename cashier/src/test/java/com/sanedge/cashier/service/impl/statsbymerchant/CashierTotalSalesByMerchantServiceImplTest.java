package com.sanedge.cashier.service.impl.statsbymerchant;

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

import com.sanedge.cashier.domain.requests.MonthTotalSalesMerchant;
import com.sanedge.cashier.domain.requests.YearTotalSalesMerchant;
import com.sanedge.cashier.domain.response.CashierResponseMonthTotalSales;
import com.sanedge.cashier.domain.response.CashierResponseYearTotalSales;
import com.sanedge.cashier.entity.CashierMonthTotalSales;
import com.sanedge.cashier.entity.CashierYearTotalSales;
import com.sanedge.cashier.repository.statsbymerchant.CashierTotalSalesByMerchantRepository;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CashierTotalSalesByMerchantServiceImplTest {

    @Mock
    private CashierTotalSalesByMerchantRepository cashierTotalSalesByMerchantRepository;

    @Mock
    private TracingMetrics tracingMetrics;

    private CashierTotalSalesByMerchantServiceImpl cashierTotalSalesByMerchantService;

    @BeforeEach
    void setUp() {
        cashierTotalSalesByMerchantService = new CashierTotalSalesByMerchantServiceImpl(
                cashierTotalSalesByMerchantRepository, tracingMetrics);

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
    void findMonthlyTotalSalesByMerchant_nullMerchantId_returnsError() {
        MonthTotalSalesMerchant req = new MonthTotalSalesMerchant();
        req.setMerchantId(null);
        req.setYear(2024);
        req.setMonth(1);

        ApiResponse<List<CashierResponseMonthTotalSales>> response = cashierTotalSalesByMerchantService
                .findMonthlyTotalSalesByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("must not be null");
    }

    @Test
    void findMonthlyTotalSalesByMerchant_nullYear_returnsError() {
        MonthTotalSalesMerchant req = new MonthTotalSalesMerchant();
        req.setMerchantId(1);
        req.setYear(null);
        req.setMonth(1);

        ApiResponse<List<CashierResponseMonthTotalSales>> response = cashierTotalSalesByMerchantService
                .findMonthlyTotalSalesByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("must not be null");
    }

    @Test
    void findMonthlyTotalSalesByMerchant_nullMonth_returnsError() {
        MonthTotalSalesMerchant req = new MonthTotalSalesMerchant();
        req.setMerchantId(1);
        req.setYear(2024);
        req.setMonth(null);

        ApiResponse<List<CashierResponseMonthTotalSales>> response = cashierTotalSalesByMerchantService
                .findMonthlyTotalSalesByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("must not be null");
    }

    @Test
    void findMonthlyTotalSalesByMerchant_success() {
        MonthTotalSalesMerchant req = new MonthTotalSalesMerchant();
        req.setMerchantId(1);
        req.setYear(2024);
        req.setMonth(1);

        List<CashierMonthTotalSales> mockList = List.of(
                createMockMonthTotalSales("2024", "1", 5000L));

        when(cashierTotalSalesByMerchantRepository.findMonthTotalSalesByMerchant(any()))
                .thenReturn(Uni.createFrom().item(mockList));

        ApiResponse<List<CashierResponseMonthTotalSales>> response = cashierTotalSalesByMerchantService
                .findMonthlyTotalSalesByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).getMonth()).isEqualTo("1");
        assertThat(response.data().get(0).getTotalSales()).isEqualTo(5000L);
    }

    @Test
    void findMonthlyTotalSalesByMerchant_error_returnsErrorResponse() {
        MonthTotalSalesMerchant req = new MonthTotalSalesMerchant();
        req.setMerchantId(1);
        req.setYear(2024);
        req.setMonth(1);

        when(cashierTotalSalesByMerchantRepository.findMonthTotalSalesByMerchant(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        ApiResponse<List<CashierResponseMonthTotalSales>> response = cashierTotalSalesByMerchantService
                .findMonthlyTotalSalesByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("DB error");
    }

    @Test
    void findYearlyTotalSalesByMerchant_nullMerchantId_returnsError() {
        YearTotalSalesMerchant req = new YearTotalSalesMerchant();
        req.setMerchantId(null);
        req.setYear(2024);

        ApiResponse<List<CashierResponseYearTotalSales>> response = cashierTotalSalesByMerchantService
                .findYearlyTotalSalesByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("must not be null");
    }

    @Test
    void findYearlyTotalSalesByMerchant_nullYear_returnsError() {
        YearTotalSalesMerchant req = new YearTotalSalesMerchant();
        req.setMerchantId(1);
        req.setYear(null);

        ApiResponse<List<CashierResponseYearTotalSales>> response = cashierTotalSalesByMerchantService
                .findYearlyTotalSalesByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("must not be null");
    }

    @Test
    void findYearlyTotalSalesByMerchant_success() {
        YearTotalSalesMerchant req = new YearTotalSalesMerchant();
        req.setMerchantId(1);
        req.setYear(2024);

        List<CashierYearTotalSales> mockList = List.of(
                createMockYearTotalSales("2024", 60000L));

        when(cashierTotalSalesByMerchantRepository.findYearTotalSalesByMerchant(any()))
                .thenReturn(Uni.createFrom().item(mockList));

        ApiResponse<List<CashierResponseYearTotalSales>> response = cashierTotalSalesByMerchantService
                .findYearlyTotalSalesByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).getYear()).isEqualTo("2024");
        assertThat(response.data().get(0).getTotalSales()).isEqualTo(60000L);
    }

    @Test
    void findYearlyTotalSalesByMerchant_error_returnsErrorResponse() {
        YearTotalSalesMerchant req = new YearTotalSalesMerchant();
        req.setMerchantId(1);
        req.setYear(2024);

        when(cashierTotalSalesByMerchantRepository.findYearTotalSalesByMerchant(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        ApiResponse<List<CashierResponseYearTotalSales>> response = cashierTotalSalesByMerchantService
                .findYearlyTotalSalesByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("DB error");
    }
}
