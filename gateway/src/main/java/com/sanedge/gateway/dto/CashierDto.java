package com.sanedge.gateway.dto;

import java.util.List;
import java.util.stream.Collectors;

public class CashierDto {
    public record CreateRequest(
            int merchantId,
            int userId,
            String name) {
    }

    public record UpdateRequest(
            int cashierId,
            String name) {
    }

    public record CashierResponse(
            int id,
            int merchantId,
            String name,
            String createdAt,
            String updatedAt) {
        public static CashierResponse from(pb.cashier.Cashier.CashierResponse proto) {
            return new CashierResponse(
                    proto.getId(),
                    proto.getMerchantId(),
                    proto.getName(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt());
        }
    }

    public record CashierResponseDeleteAt(
            int id,
            int merchantId,
            String name,
            String createdAt,
            String updatedAt,
            String deletedAt) {
        public static CashierResponseDeleteAt from(pb.cashier.Cashier.CashierResponseDeleteAt proto) {
            return new CashierResponseDeleteAt(
                    proto.getId(),
                    proto.getMerchantId(),
                    proto.getName(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt(),
                    proto.hasDeletedAt() ? proto.getDeletedAt().getValue() : null);
        }
    }

    public record CashierResponseMonthSales(
            String month,
            int cashierId,
            String cashierName,
            int orderCount,
            int totalSales) {
        public static CashierResponseMonthSales from(pb.cashier.Cashier.CashierResponseMonthSales proto) {
            return new CashierResponseMonthSales(
                    proto.getMonth(),
                    proto.getCashierId(),
                    proto.getCashierName(),
                    proto.getOrderCount(),
                    proto.getTotalSales());
        }
    }

    public record CashierResponseYearSales(
            String year,
            int cashierId,
            String cashierName,
            int orderCount,
            int totalSales) {
        public static CashierResponseYearSales from(pb.cashier.Cashier.CashierResponseYearSales proto) {
            return new CashierResponseYearSales(
                    proto.getYear(),
                    proto.getCashierId(),
                    proto.getCashierName(),
                    proto.getOrderCount(),
                    proto.getTotalSales());
        }
    }

    public record CashierResponseMonthTotalSales(
            String year,
            String month,
            int totalSales) {
        public static CashierResponseMonthTotalSales from(pb.cashier.Cashier.CashierResponseMonthTotalSales proto) {
            return new CashierResponseMonthTotalSales(
                    proto.getYear(),
                    proto.getMonth(),
                    proto.getTotalSales());
        }
    }

    public record CashierResponseYearTotalSales(
            String year,
            int totalSales) {
        public static CashierResponseYearTotalSales from(pb.cashier.Cashier.CashierResponseYearTotalSales proto) {
            return new CashierResponseYearTotalSales(
                    proto.getYear(),
                    proto.getTotalSales());
        }
    }

    public record ApiResponseCashier(
            String status,
            String message,
            CashierResponse data) {
        public static ApiResponseCashier from(pb.cashier.Cashier.ApiResponseCashier proto) {
            return new ApiResponseCashier(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? CashierResponse.from(proto.getData()) : null);
        }
    }

    public record ApiResponseCashierDeleteAt(
            String status,
            String message,
            CashierResponseDeleteAt data) {
        public static ApiResponseCashierDeleteAt from(pb.cashier.Cashier.ApiResponseCashierDeleteAt proto) {
            return new ApiResponseCashierDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? CashierResponseDeleteAt.from(proto.getData()) : null);
        }
    }

    public record ApiResponseCashierMonthSales(
            String status,
            String message,
            List<CashierResponseMonthSales> data) {
        public static ApiResponseCashierMonthSales from(pb.cashier.Cashier.ApiResponseCashierMonthSales proto) {
            List<CashierResponseMonthSales> list = proto.getDataList().stream()
                    .map(CashierResponseMonthSales::from)
                    .collect(Collectors.toList());
            return new ApiResponseCashierMonthSales(
                    proto.getStatus(),
                    proto.getMessage(),
                    list);
        }
    }

    public record ApiResponseCashierYearSales(
            String status,
            String message,
            List<CashierResponseYearSales> data) {
        public static ApiResponseCashierYearSales from(pb.cashier.Cashier.ApiResponseCashierYearSales proto) {
            List<CashierResponseYearSales> list = proto.getDataList().stream()
                    .map(CashierResponseYearSales::from)
                    .collect(Collectors.toList());
            return new ApiResponseCashierYearSales(
                    proto.getStatus(),
                    proto.getMessage(),
                    list);
        }
    }

    public record ApiResponseCashierMonthlyTotalSales(
            String status,
            String message,
            List<CashierResponseMonthTotalSales> data) {
        public static ApiResponseCashierMonthlyTotalSales from(pb.cashier.stats.CashierTotalSales.ApiResponseCashierMonthlyTotalSales proto) {
            List<CashierResponseMonthTotalSales> list = proto.getDataList().stream()
                    .map(CashierResponseMonthTotalSales::from)
                    .collect(Collectors.toList());
            return new ApiResponseCashierMonthlyTotalSales(
                    proto.getStatus(),
                    proto.getMessage(),
                    list);
        }
    }

    public record ApiResponseCashierYearlyTotalSales(
            String status,
            String message,
            List<CashierResponseYearTotalSales> data) {
        public static ApiResponseCashierYearlyTotalSales from(pb.cashier.stats.CashierTotalSales.ApiResponseCashierYearlyTotalSales proto) {
            List<CashierResponseYearTotalSales> list = proto.getDataList().stream()
                    .map(CashierResponseYearTotalSales::from)
                    .collect(Collectors.toList());
            return new ApiResponseCashierYearlyTotalSales(
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

    public record ApiResponsePaginationCashier(
            String status,
            String message,
            List<CashierResponse> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationCashier from(pb.cashier.CashierQuery.ApiResponsePaginationCashier proto) {
            List<CashierResponse> list = proto.getDataList().stream()
                    .map(CashierResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationCashier(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPagination() ? PaginationMeta.from(proto.getPagination()) : null);
        }
    }

    public record ApiResponsePaginationCashierDeleteAt(
            String status,
            String message,
            List<CashierResponseDeleteAt> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationCashierDeleteAt from(pb.cashier.CashierQuery.ApiResponsePaginationCashierDeleteAt proto) {
            List<CashierResponseDeleteAt> list = proto.getDataList().stream()
                    .map(CashierResponseDeleteAt::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationCashierDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPagination() ? PaginationMeta.from(proto.getPagination()) : null);
        }
    }

    public record SimpleResponse(
            String status,
            String message) {
        public static SimpleResponse from(pb.cashier.CashierCommand.ApiResponseCashierDelete proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }

        public static SimpleResponse from(pb.cashier.CashierCommand.ApiResponseCashierAll proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }
    }
}
