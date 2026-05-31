package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.ProductDto;
import io.smallrye.mutiny.Uni;

public interface ProductService {
    Uni<ProductDto.ApiResponsePaginationProduct> findAll(int page, int size, String search);
    Uni<ProductDto.ApiResponsePaginationProductDeleteAt> findByActive(int page, int size, String search);
    Uni<ProductDto.ApiResponsePaginationProductDeleteAt> findByTrashed(int page, int size, String search);
    Uni<ProductDto.ApiResponseProduct> findById(int id);
    Uni<ProductDto.ApiResponsePaginationProduct> findByMerchant(int merchantId, String search, int categoryId, int minPrice, int maxPrice, int page, int size);
    Uni<ProductDto.ApiResponsePaginationProduct> findByCategory(String categoryName, int page, int size, String search, int minPrice, int maxPrice);
    Uni<ProductDto.ApiResponseProduct> create(ProductDto.CreateRequest body);
    Uni<ProductDto.ApiResponseProduct> update(int id, ProductDto.UpdateRequest body);
    Uni<ProductDto.ApiResponseProductDeleteAt> trashed(int id);
    Uni<ProductDto.ApiResponseProductDeleteAt> restore(int id);
    Uni<ProductDto.SimpleResponse> deletePermanent(int id);
    Uni<ProductDto.SimpleResponse> restoreAll();
    Uni<ProductDto.SimpleResponse> deleteAllPermanent();
}
