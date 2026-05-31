package com.sanedge.gateway.dto;

import java.util.List;
import java.util.stream.Collectors;

public class CategoryDto {
    public record CreateRequest(
            String name,
            String description,
            String imageCategory) {
    }

    public record UpdateRequest(
            int categoryId,
            String name,
            String description,
            String imageCategory) {
    }

    public record CategoryResponse(
            int id,
            String name,
            String description,
            String slugCategory,
            String imageCategory,
            String createdAt,
            String updatedAt) {
        public static CategoryResponse from(pb.category.Category.CategoryResponse proto) {
            return new CategoryResponse(
                    proto.getId(),
                    proto.getName(),
                    proto.getDescription(),
                    proto.getSlugCategory(),
                    proto.getImageCategory(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt());
        }
    }

    public record CategoryResponseDeleteAt(
            int id,
            String name,
            String description,
            String slugCategory,
            String imageCategory,
            String createdAt,
            String updatedAt,
            String deletedAt) {
        public static CategoryResponseDeleteAt from(pb.category.Category.CategoryResponseDeleteAt proto) {
            return new CategoryResponseDeleteAt(
                    proto.getId(),
                    proto.getName(),
                    proto.getDescription(),
                    proto.getSlugCategory(),
                    proto.getImageCategory(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt(),
                    proto.hasDeletedAt() ? proto.getDeletedAt().getValue() : null);
        }
    }

    public record CategoryMonthPriceResponse(
            String month,
            int categoryId,
            String categoryName,
            int orderCount,
            int itemsSold,
            int totalRevenue) {
        public static CategoryMonthPriceResponse from(pb.category.Category.CategoryMonthPriceResponse proto) {
            return new CategoryMonthPriceResponse(
                    proto.getMonth(),
                    proto.getCategoryId(),
                    proto.getCategoryName(),
                    proto.getOrderCount(),
                    proto.getItemsSold(),
                    proto.getTotalRevenue());
        }
    }

    public record CategoryYearPriceResponse(
            String year,
            int categoryId,
            String categoryName,
            int orderCount,
            int itemsSold,
            int totalRevenue,
            int uniqueProductsSold) {
        public static CategoryYearPriceResponse from(pb.category.Category.CategoryYearPriceResponse proto) {
            return new CategoryYearPriceResponse(
                    proto.getYear(),
                    proto.getCategoryId(),
                    proto.getCategoryName(),
                    proto.getOrderCount(),
                    proto.getItemsSold(),
                    proto.getTotalRevenue(),
                    proto.getUniqueProductsSold());
        }
    }

    public record CategoriesMonthlyTotalPriceResponse(
            String year,
            String month,
            int totalRevenue) {
        public static CategoriesMonthlyTotalPriceResponse from(pb.category.Category.CategoriesMonthlyTotalPriceResponse proto) {
            return new CategoriesMonthlyTotalPriceResponse(
                    proto.getYear(),
                    proto.getMonth(),
                    proto.getTotalRevenue());
        }
    }

    public record CategoriesYearlyTotalPriceResponse(
            String year,
            int totalRevenue) {
        public static CategoriesYearlyTotalPriceResponse from(pb.category.Category.CategoriesYearlyTotalPriceResponse proto) {
            return new CategoriesYearlyTotalPriceResponse(
                    proto.getYear(),
                    proto.getTotalRevenue());
        }
    }

    public record ApiResponseCategory(
            String status,
            String message,
            CategoryResponse data) {
        public static ApiResponseCategory from(pb.category.Category.ApiResponseCategory proto) {
            return new ApiResponseCategory(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? CategoryResponse.from(proto.getData()) : null);
        }
    }

    public record ApiResponseCategoryDeleteAt(
            String status,
            String message,
            CategoryResponseDeleteAt data) {
        public static ApiResponseCategoryDeleteAt from(pb.category.Category.ApiResponseCategoryDeleteAt proto) {
            return new ApiResponseCategoryDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? CategoryResponseDeleteAt.from(proto.getData()) : null);
        }
    }

    public record ApiResponseCategoryMonthPrice(
            String status,
            String message,
            List<CategoryMonthPriceResponse> data) {
        public static ApiResponseCategoryMonthPrice from(pb.category.Category.ApiResponseCategoryMonthPrice proto) {
            List<CategoryMonthPriceResponse> list = proto.getDataList().stream()
                    .map(CategoryMonthPriceResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponseCategoryMonthPrice(
                    proto.getStatus(),
                    proto.getMessage(),
                    list);
        }
    }

    public record ApiResponseCategoryYearPrice(
            String status,
            String message,
            List<CategoryYearPriceResponse> data) {
        public static ApiResponseCategoryYearPrice from(pb.category.Category.ApiResponseCategoryYearPrice proto) {
            List<CategoryYearPriceResponse> list = proto.getDataList().stream()
                    .map(CategoryYearPriceResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponseCategoryYearPrice(
                    proto.getStatus(),
                    proto.getMessage(),
                    list);
        }
    }

    public record ApiResponseCategoryMonthlyTotalPrice(
            String status,
            String message,
            List<CategoriesMonthlyTotalPriceResponse> data) {
        public static ApiResponseCategoryMonthlyTotalPrice from(pb.category.Category.ApiResponseCategoryMonthlyTotalPrice proto) {
            List<CategoriesMonthlyTotalPriceResponse> list = proto.getDataList().stream()
                    .map(CategoriesMonthlyTotalPriceResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponseCategoryMonthlyTotalPrice(
                    proto.getStatus(),
                    proto.getMessage(),
                    list);
        }
    }

    public record ApiResponseCategoryYearlyTotalPrice(
            String status,
            String message,
            List<CategoriesYearlyTotalPriceResponse> data) {
        public static ApiResponseCategoryYearlyTotalPrice from(pb.category.Category.ApiResponseCategoryYearlyTotalPrice proto) {
            List<CategoriesYearlyTotalPriceResponse> list = proto.getDataList().stream()
                    .map(CategoriesYearlyTotalPriceResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponseCategoryYearlyTotalPrice(
                    proto.getStatus(),
                    proto.getMessage(),
                    list);
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

    public record ApiResponsePaginationCategory(
            String status,
            String message,
            List<CategoryResponse> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationCategory from(pb.category.CategoryQuery.ApiResponsePaginationCategory proto) {
            List<CategoryResponse> list = proto.getDataList().stream()
                    .map(CategoryResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationCategory(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPagination() ? PaginationMeta.from(proto.getPagination()) : null);
        }
    }

    public record ApiResponsePaginationCategoryDeleteAt(
            String status,
            String message,
            List<CategoryResponseDeleteAt> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationCategoryDeleteAt from(pb.category.CategoryQuery.ApiResponsePaginationCategoryDeleteAt proto) {
            List<CategoryResponseDeleteAt> list = proto.getDataList().stream()
                    .map(CategoryResponseDeleteAt::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationCategoryDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPagination() ? PaginationMeta.from(proto.getPagination()) : null);
        }
    }

    public record SimpleResponse(
            String status,
            String message) {
        public static SimpleResponse from(pb.category.CategoryCommand.ApiResponseCategoryDelete proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }

        public static SimpleResponse from(pb.category.CategoryCommand.ApiResponseCategoryAll proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }
    }
}
