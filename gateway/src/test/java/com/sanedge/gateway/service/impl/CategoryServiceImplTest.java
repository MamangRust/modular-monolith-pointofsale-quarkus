package com.sanedge.gateway.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.gateway.dto.CategoryDto;
import com.sanedge.gateway.telemetry.TelemetryHelper;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private TelemetryHelper telemetryHelper;
    @Mock
    private pb.category.MutinyCategoryQueryServiceGrpc.MutinyCategoryQueryServiceStub categoryQueryService;
    @Mock
    private pb.category.MutinyCategoryCommandServiceGrpc.MutinyCategoryCommandServiceStub categoryCommandService;
    @Mock
    private pb.category.stats.MutinyCategoryTotalPriceServiceGrpc.MutinyCategoryTotalPriceServiceStub categoryTotalPriceServiceStub;
    @Mock
    private pb.category.stats.MutinyCategoryPriceServiceGrpc.MutinyCategoryPriceServiceStub categoryPriceServiceStub;

    private CategoryServiceImpl service;

    private void inject(String name, Object value) throws Exception {
        Field f = CategoryServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(telemetryHelper.traceAndMetric(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Uni<?>> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
        service = new CategoryServiceImpl();
        inject("telemetryHelper", telemetryHelper);
        inject("categoryQueryService", categoryQueryService);
        inject("categoryCommandService", categoryCommandService);
        inject("categoryTotalPriceServiceStub", categoryTotalPriceServiceStub);
        inject("categoryPriceServiceStub", categoryPriceServiceStub);
    }

    @Test
    void listCategories_PropagatesResponse() {
        pb.category.CategoryQuery.ApiResponsePaginationCategory proto = pb.category.CategoryQuery.ApiResponsePaginationCategory.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(categoryQueryService.findAll(any(pb.category.Category.FindAllCategoryRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.listCategories(new com.sanedge.gateway.domain.requests.FindAllCategoriesRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getCategory_PropagatesResponse() {
        pb.category.Category.ApiResponseCategory proto = pb.category.Category.ApiResponseCategory.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(categoryQueryService.findById(any(pb.category.Category.FindByIdCategoryRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getCategory(1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getActiveCategories_PropagatesResponse() {
        pb.category.CategoryQuery.ApiResponsePaginationCategoryDeleteAt proto = pb.category.CategoryQuery.ApiResponsePaginationCategoryDeleteAt.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(categoryQueryService.findByActive(any(pb.category.Category.FindAllCategoryRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getActiveCategories(new com.sanedge.gateway.domain.requests.FindAllCategoriesRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getTrashedCategories_PropagatesResponse() {
        pb.category.CategoryQuery.ApiResponsePaginationCategoryDeleteAt proto = pb.category.CategoryQuery.ApiResponsePaginationCategoryDeleteAt.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(categoryQueryService.findByTrashed(any(pb.category.Category.FindAllCategoryRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getTrashedCategories(new com.sanedge.gateway.domain.requests.FindAllCategoriesRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void createCategory_PropagatesResponse() {
        pb.category.Category.ApiResponseCategory proto = pb.category.Category.ApiResponseCategory.newBuilder()
                .setStatus("success").setMessage("created").build();
        lenient().when(categoryCommandService.create(any(pb.category.CategoryCommand.CreateCategoryRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.createCategory(new CategoryDto.CreateRequest("Test", "Desc", null)).await().indefinitely();
        assertThat(result.message()).isEqualTo("created");
    }

    @Test
    void updateCategory_PropagatesResponse() {
        pb.category.Category.ApiResponseCategory proto = pb.category.Category.ApiResponseCategory.newBuilder()
                .setStatus("success").setMessage("updated").build();
        lenient().when(categoryCommandService.update(any(pb.category.CategoryCommand.UpdateCategoryRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.updateCategory(1, new CategoryDto.UpdateRequest(1, "Test", "Desc", null)).await().indefinitely();
        assertThat(result.message()).isEqualTo("updated");
    }

    @Test
    void deleteCategory_TrashStub_Propagates() {
        pb.category.Category.ApiResponseCategoryDeleteAt proto = pb.category.Category.ApiResponseCategoryDeleteAt.newBuilder()
                .setStatus("success").setMessage("trashed").build();
        lenient().when(categoryCommandService.trashedCategory(any(pb.category.Category.FindByIdCategoryRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.deleteCategory(1).await().indefinitely();
        assertThat(result.message()).isEqualTo("trashed");
    }

    @Test
    void restoreCategory_RestoreStub_Propagates() {
        pb.category.Category.ApiResponseCategoryDeleteAt proto = pb.category.Category.ApiResponseCategoryDeleteAt.newBuilder()
                .setStatus("success").setMessage("restored").build();
        lenient().when(categoryCommandService.restoreCategory(any(pb.category.Category.FindByIdCategoryRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.restoreCategory(1).await().indefinitely();
        assertThat(result.message()).isEqualTo("restored");
    }

    @Test
    void deleteCategoryPermanent_Propagates() {
        pb.category.CategoryCommand.ApiResponseCategoryDelete proto = pb.category.CategoryCommand.ApiResponseCategoryDelete.newBuilder()
                .setStatus("success").setMessage("deleted").build();
        lenient().when(categoryCommandService.deleteCategoryPermanent(any(pb.category.Category.FindByIdCategoryRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.deleteCategoryPermanent(1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void restoreAllCategory_Propagates() {
        pb.category.CategoryCommand.ApiResponseCategoryAll proto = pb.category.CategoryCommand.ApiResponseCategoryAll.newBuilder()
                .setStatus("success").setMessage("all restored").build();
        lenient().when(categoryCommandService.restoreAllCategory(any(com.google.protobuf.Empty.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.restoreAllCategory().await().indefinitely();
        assertThat(result.message()).isEqualTo("all restored");
    }
}
