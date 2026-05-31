package com.sanedge.gateway.dto;

import java.util.List;
import java.util.stream.Collectors;

public class ProductDto {
    public record CreateRequest(
            int merchantId,
            int categoryId,
            String name,
            String description,
            int price,
            int countInStock,
            String brand,
            int weight,
            String imageProduct) {
    }

    public record UpdateRequest(
            int productId,
            int merchantId,
            int categoryId,
            String name,
            String description,
            int price,
            int countInStock,
            String brand,
            int weight,
            String imageProduct) {
    }

    public record ProductResponse(
            int id,
            int merchantId,
            int categoryId,
            String name,
            String description,
            int price,
            int countInStock,
            String brand,
            int weight,
            float rating,
            String slugProduct,
            String imageProduct,
            String barcode,
            String createdAt,
            String updatedAt) {
        public static ProductResponse from(pb.product.Product.ProductResponse proto) {
            return new ProductResponse(
                    proto.getId(),
                    proto.getMerchantId(),
                    proto.getCategoryId(),
                    proto.getName(),
                    proto.getDescription(),
                    proto.getPrice(),
                    proto.getCountInStock(),
                    proto.getBrand(),
                    proto.getWeight(),
                    proto.getRating(),
                    proto.getSlugProduct(),
                    proto.getImageProduct(),
                    proto.getBarcode(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt());
        }
    }

    public record ProductResponseDeleteAt(
            int id,
            int merchantId,
            int categoryId,
            String name,
            String description,
            int price,
            int countInStock,
            String brand,
            int weight,
            float rating,
            String slugProduct,
            String imageProduct,
            String barcode,
            String createdAt,
            String updatedAt,
            String deletedAt) {
        public static ProductResponseDeleteAt from(pb.product.Product.ProductResponseDeleteAt proto) {
            return new ProductResponseDeleteAt(
                    proto.getId(),
                    proto.getMerchantId(),
                    proto.getCategoryId(),
                    proto.getName(),
                    proto.getDescription(),
                    proto.getPrice(),
                    proto.getCountInStock(),
                    proto.getBrand(),
                    proto.getWeight(),
                    proto.getRating(),
                    proto.getSlugProduct(),
                    proto.getImageProduct(),
                    proto.getBarcode(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt(),
                    proto.hasDeletedAt() ? proto.getDeletedAt().getValue() : null);
        }
    }

    public record ApiResponseProduct(
            String status,
            String message,
            ProductResponse data) {
        public static ApiResponseProduct from(pb.product.Product.ApiResponseProduct proto) {
            return new ApiResponseProduct(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? ProductResponse.from(proto.getData()) : null);
        }
    }

    public record ApiResponseProductDeleteAt(
            String status,
            String message,
            ProductResponseDeleteAt data) {
        public static ApiResponseProductDeleteAt from(pb.product.Product.ApiResponseProductDeleteAt proto) {
            return new ApiResponseProductDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? ProductResponseDeleteAt.from(proto.getData()) : null);
        }
    }

    public record PaginationMeta(
            int currentPage,
            int pageSize,
            int totalPage,
            int totalRecords) {
        public static PaginationMeta from(pb.common.PaginationMeta proto) {
            return new PaginationMeta(
                    proto.getCurrentPage(),
                    proto.getPageSize(),
                    proto.getTotalPages(),
                    proto.getTotalRecords());
        }
    }

    public record ApiResponsePaginationProduct(
            String status,
            String message,
            List<ProductResponse> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationProduct from(pb.product.ProductQuery.ApiResponsePaginationProduct proto) {
            List<ProductResponse> list = proto.getDataList().stream()
                    .map(ProductResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationProduct(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPagination() ? PaginationMeta.from(proto.getPagination()) : null);
        }
    }

    public record ApiResponsePaginationProductDeleteAt(
            String status,
            String message,
            List<ProductResponseDeleteAt> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationProductDeleteAt from(pb.product.ProductQuery.ApiResponsePaginationProductDeleteAt proto) {
            List<ProductResponseDeleteAt> list = proto.getDataList().stream()
                    .map(ProductResponseDeleteAt::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationProductDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPagination() ? PaginationMeta.from(proto.getPagination()) : null);
        }
    }

    public record SimpleResponse(
            String status,
            String message) {
        public static SimpleResponse from(pb.product.ProductCommand.ApiResponseProductDelete proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }

        public static SimpleResponse from(pb.product.ProductCommand.ApiResponseProductAll proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }
    }
}
