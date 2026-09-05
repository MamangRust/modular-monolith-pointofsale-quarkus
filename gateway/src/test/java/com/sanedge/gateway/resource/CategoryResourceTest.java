package com.sanedge.gateway.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.gateway.dto.CategoryDto;
import com.sanedge.gateway.service.CategoryService;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class CategoryResourceTest {
    @Mock
    CategoryService categoryService;
    private CategoryResource categoryResource;

    @BeforeEach
    void setUp() throws Exception {
        categoryResource = new CategoryResource();
        Field f = CategoryResource.class.getDeclaredField("categoryService");
        f.setAccessible(true);
        f.set(categoryResource, categoryService);
    }

    private CategoryDto.CategoryResponse mk(int id) {
        return new CategoryDto.CategoryResponse(id, "name", "desc", "slug", "", "", "");
    }

    @Test
    void listCategories_Success() {
        CategoryDto.ApiResponsePaginationCategory dto = new CategoryDto.ApiResponsePaginationCategory(
                "success", "ok", List.of(mk(1)), null);
        lenient().when(categoryService.listCategories(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = categoryResource.listCategories(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getActiveCategories_Success() {
        CategoryDto.ApiResponsePaginationCategoryDeleteAt dto = new CategoryDto.ApiResponsePaginationCategoryDeleteAt(
                "success", "ok", List.of(), null);
        lenient().when(categoryService.getActiveCategories(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = categoryResource.getActiveCategories(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getTrashedCategories_Success() {
        CategoryDto.ApiResponsePaginationCategoryDeleteAt dto = new CategoryDto.ApiResponsePaginationCategoryDeleteAt(
                "success", "ok", List.of(), null);
        lenient().when(categoryService.getTrashedCategories(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = categoryResource.getTrashedCategories(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getCategory_Success() {
        CategoryDto.ApiResponseCategory dto = new CategoryDto.ApiResponseCategory("success", "ok", mk(1));
        lenient().when(categoryService.getCategory(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = categoryResource.getCategory(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void createCategory_Success_Returns201() {
        CategoryDto.ApiResponseCategory dto = new CategoryDto.ApiResponseCategory("success", "ok", mk(1));
        lenient().when(categoryService.createCategory(any())).thenReturn(Uni.createFrom().item(dto));
        CategoryDto.CreateRequest req = new CategoryDto.CreateRequest("Test", "Desc", null);
        Response r = categoryResource.createCategory(req).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(201);
    }

    @Test
    void updateCategory_Success() {
        CategoryDto.ApiResponseCategory dto = new CategoryDto.ApiResponseCategory("success", "ok", mk(1));
        lenient().when(categoryService.updateCategory(anyInt(), any())).thenReturn(Uni.createFrom().item(dto));
        CategoryDto.UpdateRequest req = new CategoryDto.UpdateRequest(1, "Test", "Desc", null);
        Response r = categoryResource.updateCategory(1, req).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void deleteCategory_Success() {
        CategoryDto.ApiResponseCategoryDeleteAt dto = new CategoryDto.ApiResponseCategoryDeleteAt("success", "ok", null);
        lenient().when(categoryService.deleteCategory(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = categoryResource.deleteCategory(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void restoreCategory_Success() {
        CategoryDto.ApiResponseCategoryDeleteAt dto = new CategoryDto.ApiResponseCategoryDeleteAt("success", "ok", null);
        lenient().when(categoryService.restoreCategory(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = categoryResource.restoreCategory(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void deleteCategoryPermanent_Success() {
        CategoryDto.SimpleResponse dto = new CategoryDto.SimpleResponse("success", "ok");
        lenient().when(categoryService.deleteCategoryPermanent(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = categoryResource.deleteCategoryPermanent(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void restoreAllCategories_Success() {
        CategoryDto.SimpleResponse dto = new CategoryDto.SimpleResponse("success", "ok");
        lenient().when(categoryService.restoreAllCategory()).thenReturn(Uni.createFrom().item(dto));
        Response r = categoryResource.restoreAllCategory().await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void deleteAllCategoriesPermanent_Success() {
        CategoryDto.SimpleResponse dto = new CategoryDto.SimpleResponse("success", "ok");
        lenient().when(categoryService.deleteAllCategoryPermanent()).thenReturn(Uni.createFrom().item(dto));
        Response r = categoryResource.deleteAllCategoryPermanent().await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }
}
