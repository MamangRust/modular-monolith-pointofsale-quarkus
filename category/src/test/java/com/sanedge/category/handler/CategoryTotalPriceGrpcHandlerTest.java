package com.sanedge.category.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.service.stats.CategoryTotalPriceService;
import com.sanedge.category.service.statsbyid.CategoryTotalPriceByIdService;
import com.sanedge.category.service.statsbymerchant.CategoryTotalPriceByMerchantService;
import com.sanedge.common.domain.response.ApiResponse;

import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.category.Category;

@ExtendWith(MockitoExtension.class)
class CategoryTotalPriceGrpcHandlerTest {

    @Mock private CategoryTotalPriceService categoryTotalPriceService;
    @Mock private CategoryTotalPriceByIdService categoryTotalPriceByIdService;
    @Mock private CategoryTotalPriceByMerchantService categoryTotalPriceByMerchantService;

    private CategoryTotalPriceGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CategoryTotalPriceGrpcHandler();
        handler.categoryTotalPriceService = categoryTotalPriceService;
        handler.categoryTotalPriceByIdService = categoryTotalPriceByIdService;
        handler.categoryTotalPriceByMerchantService = categoryTotalPriceByMerchantService;
    }

    private CategoriesMonthlyTotalPriceResponse monthlyTotalPrice() {
        CategoriesMonthlyTotalPriceResponse r = new CategoriesMonthlyTotalPriceResponse();
        r.setYear("2024");
        r.setMonth("Jun");
        r.setTotalRevenue(500000L);
        return r;
    }

    private CategoriesYearlyTotalPriceResponse yearlyTotalPrice() {
        CategoriesYearlyTotalPriceResponse r = new CategoriesYearlyTotalPriceResponse();
        r.setYear("2024");
        r.setTotalRevenue(1200000L);
        return r;
    }

    @Test @DisplayName("findMonthlyTotalPrices - success")
    void findMonthlyTotalPrices_Success() {
        Category.FindYearMonthTotalPrices request = Category.FindYearMonthTotalPrices.newBuilder()
                .setYear(2024).setMonth(6).build();
        List<CategoriesMonthlyTotalPriceResponse> data = List.of(monthlyTotalPrice());
        ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> apiResp = ApiResponse.success("Success", data);
        when(categoryTotalPriceService.findMonthlyTotalPrice(any())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryMonthlyTotalPrice response = handler.findMonthlyTotalPrices(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
        assertThat(response.getData(0).getMonth()).isEqualTo("Jun");
        assertThat(response.getData(0).getTotalRevenue()).isEqualTo(500000);
    }

    @Test @DisplayName("findYearlyTotalPrices - success")
    void findYearlyTotalPrices_Success() {
        Category.FindYearTotalPrices request = Category.FindYearTotalPrices.newBuilder().setYear(2024).build();
        List<CategoriesYearlyTotalPriceResponse> data = List.of(yearlyTotalPrice());
        ApiResponse<List<CategoriesYearlyTotalPriceResponse>> apiResp = ApiResponse.success("Success", data);
        when(categoryTotalPriceService.findYearlyTotalPrice(anyInt())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryYearlyTotalPrice response = handler.findYearlyTotalPrices(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData(0).getYear()).isEqualTo("2024");
        assertThat(response.getData(0).getTotalRevenue()).isEqualTo(1200000);
    }

    @Test @DisplayName("findMonthlyTotalPricesById - success")
    void findMonthlyTotalPricesById_Success() {
        Category.FindYearMonthTotalPriceById request = Category.FindYearMonthTotalPriceById.newBuilder()
                .setYear(2024).setMonth(6).setCategoryId(1).build();
        List<CategoriesMonthlyTotalPriceResponse> data = List.of(monthlyTotalPrice());
        ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> apiResp = ApiResponse.success("Success", data);
        when(categoryTotalPriceByIdService.findMonthlyTotalPriceById(any())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryMonthlyTotalPrice response = handler.findMonthlyTotalPricesById(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test @DisplayName("findYearlyTotalPricesById - success")
    void findYearlyTotalPricesById_Success() {
        Category.FindYearTotalPriceById request = Category.FindYearTotalPriceById.newBuilder()
                .setYear(2024).setCategoryId(1).build();
        List<CategoriesYearlyTotalPriceResponse> data = List.of(yearlyTotalPrice());
        ApiResponse<List<CategoriesYearlyTotalPriceResponse>> apiResp = ApiResponse.success("Success", data);
        when(categoryTotalPriceByIdService.findYearlyTotalPriceById(any())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryYearlyTotalPrice response = handler.findYearlyTotalPricesById(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData(0).getYear()).isEqualTo("2024");
    }

    @Test @DisplayName("findMonthlyTotalPricesByMerchant - success")
    void findMonthlyTotalPricesByMerchant_Success() {
        Category.FindYearMonthTotalPriceByMerchant request = Category.FindYearMonthTotalPriceByMerchant.newBuilder()
                .setYear(2024).setMonth(6).setMerchantId(100).build();
        List<CategoriesMonthlyTotalPriceResponse> data = List.of(monthlyTotalPrice());
        ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> apiResp = ApiResponse.success("Success", data);
        when(categoryTotalPriceByMerchantService.findMonthlyTotalPriceByMerchant(any())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryMonthlyTotalPrice response = handler.findMonthlyTotalPricesByMerchant(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData(0).getMonth()).isEqualTo("Jun");
    }

    @Test @DisplayName("findYearlyTotalPricesByMerchant - success")
    void findYearlyTotalPricesByMerchant_Success() {
        Category.FindYearTotalPriceByMerchant request = Category.FindYearTotalPriceByMerchant.newBuilder()
                .setYear(2024).setMerchantId(100).build();
        List<CategoriesYearlyTotalPriceResponse> data = List.of(yearlyTotalPrice());
        ApiResponse<List<CategoriesYearlyTotalPriceResponse>> apiResp = ApiResponse.success("Success", data);
        when(categoryTotalPriceByMerchantService.findYearlyTotalPriceByMerchant(any())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryYearlyTotalPrice response = handler.findYearlyTotalPricesByMerchant(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData(0).getYear()).isEqualTo("2024");
    }

    @Test @DisplayName("findMonthlyTotalPrices - error")
    void findMonthlyTotalPrices_Error() {
        Category.FindYearMonthTotalPrices request = Category.FindYearMonthTotalPrices.newBuilder().build();
        when(categoryTotalPriceService.findMonthlyTotalPrice(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));
        try {
            handler.findMonthlyTotalPrices(request).await().indefinitely();
        } catch (StatusRuntimeException e) { assertThat(e).isNotNull(); }
    }

    @Test @DisplayName("findMonthlyTotalPrices - null data")
    void findMonthlyTotalPrices_NullData() {
        Category.FindYearMonthTotalPrices request = Category.FindYearMonthTotalPrices.newBuilder()
                .setYear(2024).setMonth(6).build();
        ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> apiResp = ApiResponse.success("Success", null);
        when(categoryTotalPriceService.findMonthlyTotalPrice(any())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryMonthlyTotalPrice response = handler.findMonthlyTotalPrices(request).await().indefinitely();
        assertThat(response.getDataCount()).isZero();
    }
}
