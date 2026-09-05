package com.sanedge.gateway.service.impl;

import com.sanedge.gateway.dto.ProductDto;
import com.sanedge.gateway.domain.requests.FindAllProductsRequest;
import com.sanedge.gateway.domain.requests.FindProductsByMerchantRequest;
import com.sanedge.gateway.domain.requests.FindProductsByCategoryRequest;
import com.sanedge.gateway.service.ProductService;
import com.sanedge.gateway.telemetry.TelemetryHelper;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProductServiceImpl implements ProductService {

    private static final Logger LOG = Logger.getLogger(ProductServiceImpl.class);

    @Inject
    TelemetryHelper telemetryHelper;

    @GrpcClient("product")
    pb.product.MutinyProductServiceGrpc.MutinyProductServiceStub productQueryService;

    @GrpcClient("product")
    pb.product.MutinyProductCommandServiceGrpc.MutinyProductCommandServiceStub productCommandService;

    @Override
    public Uni<ProductDto.ApiResponsePaginationProduct> findAll(FindAllProductsRequest request) {
        return telemetryHelper.traceAndMetric("product.findAll", () -> productQueryService.findAll(pb.product.Product.FindAllProductRequest.newBuilder()
                .setPage(request.getPage())
                .setPageSize(request.getSize())
                .setSearch(request.getSearch() == null ? "" : request.getSearch())
                .build())
                .map(ProductDto.ApiResponsePaginationProduct::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to find all products: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<ProductDto.ApiResponsePaginationProductDeleteAt> findByActive(FindAllProductsRequest request) {
        return telemetryHelper.traceAndMetric("product.findByActive", () -> productQueryService.findByActive(pb.product.Product.FindAllProductRequest.newBuilder()
                .setPage(request.getPage())
                .setPageSize(request.getSize())
                .setSearch(request.getSearch() == null ? "" : request.getSearch())
                .build())
                .map(ProductDto.ApiResponsePaginationProductDeleteAt::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to find active products: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<ProductDto.ApiResponsePaginationProductDeleteAt> findByTrashed(FindAllProductsRequest request) {
        return telemetryHelper.traceAndMetric("product.findByTrashed", () -> productQueryService.findByTrashed(pb.product.Product.FindAllProductRequest.newBuilder()
                .setPage(request.getPage())
                .setPageSize(request.getSize())
                .setSearch(request.getSearch() == null ? "" : request.getSearch())
                .build())
                .map(ProductDto.ApiResponsePaginationProductDeleteAt::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to find trashed products: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<ProductDto.ApiResponseProduct> findById(int id) {
        return telemetryHelper.traceAndMetric("product.findById", () -> productQueryService.findById(pb.product.Product.FindByIdProductRequest.newBuilder()
                .setId(id)
                .build())
                .map(ProductDto.ApiResponseProduct::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to find product by id " + id + ": " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<ProductDto.ApiResponsePaginationProduct> findByMerchant(FindProductsByMerchantRequest request) {
        return telemetryHelper.traceAndMetric("product.findByMerchant", () -> productQueryService.findByMerchant(pb.product.Product.FindAllProductMerchantRequest.newBuilder()
                .setMerchantId(request.getMerchantId())
                .setSearch(request.getSearch() == null ? "" : request.getSearch())
                .setCategoryId(request.getCategoryId())
                .setMinPrice(request.getMinPrice())
                .setMaxPrice(request.getMaxPrice())
                .setPage(request.getPage())
                .setPageSize(request.getSize())
                .build())
                .map(ProductDto.ApiResponsePaginationProduct::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to find products by merchant " + request.getMerchantId() + ": " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<ProductDto.ApiResponsePaginationProduct> findByCategory(FindProductsByCategoryRequest request) {
        return telemetryHelper.traceAndMetric("product.findByCategory", () -> productQueryService.findByCategory(pb.product.Product.FindAllProductCategoryRequest.newBuilder()
                .setCategoryName(request.getCategoryName() == null ? "" : request.getCategoryName())
                .setPage(request.getPage())
                .setPageSize(request.getSize())
                .setSearch(request.getSearch() == null ? "" : request.getSearch())
                .setMinprice(request.getMinPrice())
                .setMaxprice(request.getMaxPrice())
                .build())
                .map(ProductDto.ApiResponsePaginationProduct::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to find products by category " + request.getCategoryName() + ": " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<ProductDto.ApiResponseProduct> create(ProductDto.CreateRequest body) {
        return telemetryHelper.traceAndMetric("product.create", () -> productCommandService.create(pb.product.ProductCommand.CreateProductRequest.newBuilder()
                .setMerchantId(body.merchantId())
                .setCategoryId(body.categoryId())
                .setName(body.name())
                .setDescription(body.description() == null ? "" : body.description())
                .setPrice(body.price())
                .setCountInStock(body.countInStock())
                .setBrand(body.brand() == null ? "" : body.brand())
                .setWeight(body.weight())
                .setImageProduct(body.imageProduct() == null ? "" : body.imageProduct())
                .build())
                .map(ProductDto.ApiResponseProduct::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to create product: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<ProductDto.ApiResponseProduct> update(int id, ProductDto.UpdateRequest body) {
        return telemetryHelper.traceAndMetric("product.update", () -> productCommandService.update(pb.product.ProductCommand.UpdateProductRequest.newBuilder()
                .setProductId(id)
                .setMerchantId(body.merchantId())
                .setCategoryId(body.categoryId())
                .setName(body.name())
                .setDescription(body.description() == null ? "" : body.description())
                .setPrice(body.price())
                .setCountInStock(body.countInStock())
                .setBrand(body.brand() == null ? "" : body.brand())
                .setWeight(body.weight())
                .setImageProduct(body.imageProduct() == null ? "" : body.imageProduct())
                .build())
                .map(ProductDto.ApiResponseProduct::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to update product " + id + ": " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<ProductDto.ApiResponseProductDeleteAt> trashed(int id) {
        return telemetryHelper.traceAndMetric("product.trashed", () -> productCommandService.trashedProduct(pb.product.Product.FindByIdProductRequest.newBuilder()
                .setId(id)
                .build())
                .map(ProductDto.ApiResponseProductDeleteAt::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to soft-delete product " + id + ": " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<ProductDto.ApiResponseProductDeleteAt> restore(int id) {
        return telemetryHelper.traceAndMetric("product.restore", () -> productCommandService.restoreProduct(pb.product.Product.FindByIdProductRequest.newBuilder()
                .setId(id)
                .build())
                .map(ProductDto.ApiResponseProductDeleteAt::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to restore product " + id + ": " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<ProductDto.SimpleResponse> deletePermanent(int id) {
        return telemetryHelper.traceAndMetric("product.deletePermanent", () -> productCommandService.deleteProductPermanent(pb.product.Product.FindByIdProductRequest.newBuilder()
                .setId(id)
                .build())
                .map(ProductDto.SimpleResponse::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to permanently delete product " + id + ": " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<ProductDto.SimpleResponse> restoreAll() {
        return telemetryHelper.traceAndMetric("product.restoreAll", () -> productCommandService.restoreAllProduct(com.google.protobuf.Empty.getDefaultInstance())
                .map(ProductDto.SimpleResponse::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to restore all products: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<ProductDto.SimpleResponse> deleteAllPermanent() {
        return telemetryHelper.traceAndMetric("product.deleteAllPermanent", () -> productCommandService.deleteAllProductPermanent(com.google.protobuf.Empty.getDefaultInstance())
                .map(ProductDto.SimpleResponse::from)
                .onFailure().invoke(throwable -> LOG.error("Failed to permanently delete all products: " + throwable.getMessage(), throwable)));
    }
}
