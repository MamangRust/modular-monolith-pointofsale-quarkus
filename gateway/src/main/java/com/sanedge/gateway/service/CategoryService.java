package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.CategoryDto;
import io.smallrye.mutiny.Uni;

public interface CategoryService {
    Uni<CategoryDto.ApiResponsePaginationCategory> listCategories(int page, int size, String search);
    Uni<CategoryDto.ApiResponseCategory> getCategory(int id);
    Uni<CategoryDto.ApiResponsePaginationCategoryDeleteAt> getActiveCategories(int page, int size, String search);
    Uni<CategoryDto.ApiResponsePaginationCategoryDeleteAt> getTrashedCategories(int page, int size, String search);
    Uni<CategoryDto.ApiResponseCategory> createCategory(CategoryDto.CreateRequest body);
    Uni<CategoryDto.ApiResponseCategory> updateCategory(int id, CategoryDto.UpdateRequest body);
    Uni<CategoryDto.ApiResponseCategoryDeleteAt> deleteCategory(int id);
    Uni<CategoryDto.ApiResponseCategoryDeleteAt> restoreCategory(int id);
    Uni<CategoryDto.SimpleResponse> deleteCategoryPermanent(int id);
    Uni<CategoryDto.SimpleResponse> restoreAllCategory();
    Uni<CategoryDto.SimpleResponse> deleteAllCategoryPermanent();

    // Stats
    Uni<CategoryDto.ApiResponseCategoryMonthlyTotalPrice> getMonthlyTotalPrices(int year, int month);
    Uni<CategoryDto.ApiResponseCategoryYearlyTotalPrice> getYearlyTotalPrices(int year);
    Uni<CategoryDto.ApiResponseCategoryMonthlyTotalPrice> getMonthlyTotalPricesById(int categoryId, int year, int month);
    Uni<CategoryDto.ApiResponseCategoryYearlyTotalPrice> getYearlyTotalPricesById(int categoryId, int year);
    Uni<CategoryDto.ApiResponseCategoryMonthlyTotalPrice> getMonthlyTotalPricesByMerchant(int merchantId, int year, int month);
    Uni<CategoryDto.ApiResponseCategoryYearlyTotalPrice> getYearlyTotalPricesByMerchant(int merchantId, int year);
    Uni<CategoryDto.ApiResponseCategoryMonthPrice> getMonthlyPrices(int year);
    Uni<CategoryDto.ApiResponseCategoryYearPrice> getYearlyPrices(int year);
    Uni<CategoryDto.ApiResponseCategoryMonthPrice> getMonthlyPricesByMerchant(int merchantId, int year);
    Uni<CategoryDto.ApiResponseCategoryYearPrice> getYearlyPricesByMerchant(int merchantId, int year);
    Uni<CategoryDto.ApiResponseCategoryMonthPrice> getMonthlyPricesById(int categoryId, int year);
    Uni<CategoryDto.ApiResponseCategoryYearPrice> getYearlyPricesById(int categoryId, int year);
}
