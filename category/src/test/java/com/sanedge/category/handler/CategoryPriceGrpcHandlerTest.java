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

import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.service.stats.CategoryPriceService;
import com.sanedge.category.service.statsbyid.CategoryPriceByIdService;
import com.sanedge.category.service.statsbymerchant.CategoryPriceByMerchantService;
import com.sanedge.common.domain.response.ApiResponse;

import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.category.Category;

@ExtendWith(MockitoExtension.class)
class CategoryPriceGrpcHandlerTest {

    @Mock private CategoryPriceService categoryPriceService;
    @Mock private CategoryPriceByIdService categoryPriceByIdService;
    @Mock private CategoryPriceByMerchantService categoryPriceByMerchantService;

    private CategoryPriceGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CategoryPriceGrpcHandler();
        handler.categoryPriceService = categoryPriceService;
        handler.categoryPriceByIdService = categoryPriceByIdService;
        handler.categoryPriceByMerchantService = categoryPriceByMerchantService;
    }

    private CategoriesMonthPriceResponse monthPrice() {
        CategoriesMonthPriceResponse r = new CategoriesMonthPriceResponse();
        r.setMonth("Jan");
        r.setCategoryId(1);
        r.setCategoryName("Test Cat");
        r.setOrderCount(10);
        r.setItemsSold(50);
        r.setTotalRevenue(5000L);
        return r;
    }

    private CategoriesYearPriceResponse yearPrice() {
        CategoriesYearPriceResponse r = new CategoriesYearPriceResponse();
        r.setYear("2024");
        r.setCategoryId(1);
        r.setCategoryName("Test Cat");
        r.setOrderCount(20);
        r.setItemsSold(100);
        r.setTotalRevenue(20000L);
        r.setUniqueProductsSold(5);
        return r;
    }

    @Test @DisplayName("findMonthPrice - success")
    void findMonthPrice_Success() {
        Category.FindYearCategory request = Category.FindYearCategory.newBuilder().setYear(2024).build();
        List<CategoriesMonthPriceResponse> data = List.of(monthPrice());
        ApiResponse<List<CategoriesMonthPriceResponse>> apiResp = ApiResponse.success("Success", data);
        when(categoryPriceService.findMonthPrice(anyInt())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryMonthPrice response = handler.findMonthPrice(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
        assertThat(response.getData(0).getMonth()).isEqualTo("Jan");
    }

    @Test @DisplayName("findYearPrice - success")
    void findYearPrice_Success() {
        Category.FindYearCategory request = Category.FindYearCategory.newBuilder().setYear(2024).build();
        List<CategoriesYearPriceResponse> data = List.of(yearPrice());
        ApiResponse<List<CategoriesYearPriceResponse>> apiResp = ApiResponse.success("Success", data);
        when(categoryPriceService.findYearPrice(anyInt())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryYearPrice response = handler.findYearPrice(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData(0).getYear()).isEqualTo("2024");
    }

    @Test @DisplayName("findMonthPriceById - success")
    void findMonthPriceById_Success() {
        Category.FindYearCategoryById request = Category.FindYearCategoryById.newBuilder()
                .setYear(2024).setCategoryId(1).build();
        List<CategoriesMonthPriceResponse> data = List.of(monthPrice());
        ApiResponse<List<CategoriesMonthPriceResponse>> apiResp = ApiResponse.success("Success", data);
        when(categoryPriceByIdService.findMonthPriceById(any())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryMonthPrice response = handler.findMonthPriceById(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test @DisplayName("findYearPriceById - success")
    void findYearPriceById_Success() {
        Category.FindYearCategoryById request = Category.FindYearCategoryById.newBuilder()
                .setYear(2024).setCategoryId(1).build();
        List<CategoriesYearPriceResponse> data = List.of(yearPrice());
        ApiResponse<List<CategoriesYearPriceResponse>> apiResp = ApiResponse.success("Success", data);
        when(categoryPriceByIdService.findYearPriceById(any())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryYearPrice response = handler.findYearPriceById(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData(0).getYear()).isEqualTo("2024");
    }

    @Test @DisplayName("findMonthPriceByMerchant - success")
    void findMonthPriceByMerchant_Success() {
        Category.FindYearCategoryByMerchant request = Category.FindYearCategoryByMerchant.newBuilder()
                .setYear(2024).setMerchantId(100).build();
        List<CategoriesMonthPriceResponse> data = List.of(monthPrice());
        ApiResponse<List<CategoriesMonthPriceResponse>> apiResp = ApiResponse.success("Success", data);
        when(categoryPriceByMerchantService.findMonthPriceByMerchant(any())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryMonthPrice response = handler.findMonthPriceByMerchant(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData(0).getMonth()).isEqualTo("Jan");
    }

    @Test @DisplayName("findYearPriceByMerchant - success")
    void findYearPriceByMerchant_Success() {
        Category.FindYearCategoryByMerchant request = Category.FindYearCategoryByMerchant.newBuilder()
                .setYear(2024).setMerchantId(100).build();
        List<CategoriesYearPriceResponse> data = List.of(yearPrice());
        ApiResponse<List<CategoriesYearPriceResponse>> apiResp = ApiResponse.success("Success", data);
        when(categoryPriceByMerchantService.findYearPriceByMerchant(any())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryYearPrice response = handler.findYearPriceByMerchant(request).await().indefinitely();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData(0).getYear()).isEqualTo("2024");
    }

    @Test @DisplayName("findMonthPrice - error")
    void findMonthPrice_Error() {
        Category.FindYearCategory request = Category.FindYearCategory.newBuilder().build();
        when(categoryPriceService.findMonthPrice(anyInt())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));
        try {
            handler.findMonthPrice(request).await().indefinitely();
        } catch (StatusRuntimeException e) { assertThat(e).isNotNull(); }
    }

    @Test @DisplayName("findMonthPrice - null data")
    void findMonthPrice_NullData() {
        Category.FindYearCategory request = Category.FindYearCategory.newBuilder().setYear(2024).build();
        ApiResponse<List<CategoriesMonthPriceResponse>> apiResp = ApiResponse.success("Success", null);
        when(categoryPriceService.findMonthPrice(anyInt())).thenReturn(Uni.createFrom().item(apiResp));

        Category.ApiResponseCategoryMonthPrice response = handler.findMonthPrice(request).await().indefinitely();
        assertThat(response.getDataCount()).isZero();
    }
}
