package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.ProductDto;
import com.sanedge.gateway.domain.requests.FindAllProductsRequest;
import com.sanedge.gateway.domain.requests.FindProductsByMerchantRequest;
import com.sanedge.gateway.domain.requests.FindProductsByCategoryRequest;
import io.smallrye.mutiny.Uni;

public interface ProductService {
    Uni<ProductDto.ApiResponsePaginationProduct> findAll(FindAllProductsRequest request);
    Uni<ProductDto.ApiResponsePaginationProductDeleteAt> findByActive(FindAllProductsRequest request);
    Uni<ProductDto.ApiResponsePaginationProductDeleteAt> findByTrashed(FindAllProductsRequest request);
    Uni<ProductDto.ApiResponseProduct> findById(int id);
    Uni<ProductDto.ApiResponsePaginationProduct> findByMerchant(FindProductsByMerchantRequest request);
    Uni<ProductDto.ApiResponsePaginationProduct> findByCategory(FindProductsByCategoryRequest request);
    Uni<ProductDto.ApiResponseProduct> create(ProductDto.CreateRequest body);
    Uni<ProductDto.ApiResponseProduct> update(int id, ProductDto.UpdateRequest body);
    Uni<ProductDto.ApiResponseProductDeleteAt> trashed(int id);
    Uni<ProductDto.ApiResponseProductDeleteAt> restore(int id);
    Uni<ProductDto.SimpleResponse> deletePermanent(int id);
    Uni<ProductDto.SimpleResponse> restoreAll();
    Uni<ProductDto.SimpleResponse> deleteAllPermanent();
}
