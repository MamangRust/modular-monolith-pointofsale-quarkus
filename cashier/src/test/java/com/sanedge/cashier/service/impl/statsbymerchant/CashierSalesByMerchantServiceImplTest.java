package com.sanedge.cashier.service.impl.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import com.sanedge.cashier.domain.requests.MonthCashierMerchantRequest;
import com.sanedge.cashier.domain.requests.YearCashierMerchantRequest;
import com.sanedge.cashier.domain.response.CashierResponseMonthSales;
import com.sanedge.cashier.domain.response.CashierResponseYearSales;
import com.sanedge.cashier.entity.CashierMonthSales;
import com.sanedge.cashier.entity.CashierYearSales;
import com.sanedge.cashier.repository.statsbymerchant.CashierSalesByMerchantRepository;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CashierSalesByMerchantServiceImplTest {

    @Mock
    private CashierSalesByMerchantRepository cashierSalesByMerchantRepository;

    @Mock
    private TracingMetrics tracingMetrics;

    private CashierSalesByMerchantServiceImpl cashierSalesByMerchantService;

    @BeforeEach
    void setUp() {
        cashierSalesByMerchantService = new CashierSalesByMerchantServiceImpl(
                cashierSalesByMerchantRepository, tracingMetrics);

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

    private CashierMonthSales createMockMonthSales(Integer cashierId, String cashierName, String month,
            Long totalSales) {
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
    void findMonthlyCashierByMerchant_nullMerchantId_returnsError() {
        MonthCashierMerchantRequest req = new MonthCashierMerchantRequest();
        req.setMerchantId(null);
        req.setYear(2024);

        ApiResponse<List<CashierResponseMonthSales>> response = cashierSalesByMerchantService
                .findMonthlyCashierByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("must not be null");
    }

    @Test
    void findMonthlyCashierByMerchant_nullYear_returnsError() {
        MonthCashierMerchantRequest req = new MonthCashierMerchantRequest();
        req.setMerchantId(1);
        req.setYear(null);

        ApiResponse<List<CashierResponseMonthSales>> response = cashierSalesByMerchantService
                .findMonthlyCashierByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("must not be null");
    }

    @Test
    void findMonthlyCashierByMerchant_success() {
        MonthCashierMerchantRequest req = new MonthCashierMerchantRequest();
        req.setMerchantId(1);
        req.setYear(2024);

        List<CashierMonthSales> mockList = List.of(
                createMockMonthSales(1, "Cashier1", "1", 1000L));

        when(cashierSalesByMerchantRepository.findMonthSalesByMerchant(any()))
                .thenReturn(Uni.createFrom().item(mockList));

        ApiResponse<List<CashierResponseMonthSales>> response = cashierSalesByMerchantService
                .findMonthlyCashierByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).getCashierName()).isEqualTo("Cashier1");
    }

    @Test
    void findMonthlyCashierByMerchant_error_returnsErrorResponse() {
        MonthCashierMerchantRequest req = new MonthCashierMerchantRequest();
        req.setMerchantId(1);
        req.setYear(2024);

        when(cashierSalesByMerchantRepository.findMonthSalesByMerchant(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        ApiResponse<List<CashierResponseMonthSales>> response = cashierSalesByMerchantService
                .findMonthlyCashierByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("DB error");
    }

    @Test
    void findYearlyCashierByMerchant_nullMerchantId_returnsError() {
        YearCashierMerchantRequest req = new YearCashierMerchantRequest();
        req.setMerchantId(null);
        req.setYear(2024);

        ApiResponse<List<CashierResponseYearSales>> response = cashierSalesByMerchantService
                .findYearlyCashierByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("must not be null");
    }

    @Test
    void findYearlyCashierByMerchant_success() {
        YearCashierMerchantRequest req = new YearCashierMerchantRequest();
        req.setMerchantId(1);
        req.setYear(2024);

        List<CashierYearSales> mockList = List.of(
                createMockYearSales(1, "Cashier1", "2024", 12000L));

        when(cashierSalesByMerchantRepository.findYearSalesByMerchant(anyLong(), any()))
                .thenReturn(Uni.createFrom().item(mockList));

        ApiResponse<List<CashierResponseYearSales>> response = cashierSalesByMerchantService
                .findYearlyCashierByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).getCashierName()).isEqualTo("Cashier1");
    }

    @Test
    void findYearlyCashierByMerchant_error_returnsErrorResponse() {
        YearCashierMerchantRequest req = new YearCashierMerchantRequest();
        req.setMerchantId(1);
        req.setYear(2024);

        when(cashierSalesByMerchantRepository.findYearSalesByMerchant(anyLong(), any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        ApiResponse<List<CashierResponseYearSales>> response = cashierSalesByMerchantService
                .findYearlyCashierByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("DB error");
    }

    @Test
    void findYearlyCashierByMerchant_nullYear_returnsError() {
        YearCashierMerchantRequest req = new YearCashierMerchantRequest();
        req.setMerchantId(1);
        req.setYear(null);

        ApiResponse<List<CashierResponseYearSales>> response = cashierSalesByMerchantService
                .findYearlyCashierByMerchant(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.message()).contains("must not be null");
    }
}
