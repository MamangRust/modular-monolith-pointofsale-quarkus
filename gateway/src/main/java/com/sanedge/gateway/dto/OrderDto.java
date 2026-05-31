package com.sanedge.gateway.dto;

import java.util.List;
import java.util.stream.Collectors;

public class OrderDto {
    public record CreateOrderItemRequest(
            int productId,
            int quantity) {
    }

    public record CreateRequest(
            int merchantId,
            int cashierId,
            List<CreateOrderItemRequest> items) {
    }

    public record UpdateOrderItemRequest(
            int orderItemId,
            int productId,
            int quantity) {
    }

    public record UpdateRequest(
            int orderId,
            int cashierId,
            List<UpdateOrderItemRequest> items) {
    }

    public record OrderResponse(
            int id,
            int merchantId,
            int cashierId,
            int totalPrice,
            String createdAt,
            String updatedAt) {
        public static OrderResponse from(pb.order.Order.OrderResponse proto) {
            return new OrderResponse(
                    proto.getId(),
                    proto.getMerchantId(),
                    proto.getCashierId(),
                    proto.getTotalPrice(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt());
        }
    }

    public record OrderResponseDeleteAt(
            int id,
            int merchantId,
            int cashierId,
            int totalPrice,
            String createdAt,
            String updatedAt,
            String deletedAt) {
        public static OrderResponseDeleteAt from(pb.order.Order.OrderResponseDeleteAt proto) {
            return new OrderResponseDeleteAt(
                    proto.getId(),
                    proto.getMerchantId(),
                    proto.getCashierId(),
                    proto.getTotalPrice(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt(),
                    proto.hasDeletedAt() ? proto.getDeletedAt().getValue() : null);
        }
    }

    public record OrderMonthlyResponse(
            String month,
            int orderCount,
            int totalRevenue,
            int totalItemsSold) {
        public static OrderMonthlyResponse from(pb.order.Order.OrderMonthlyResponse proto) {
            return new OrderMonthlyResponse(
                    proto.getMonth(),
                    proto.getOrderCount(),
                    proto.getTotalRevenue(),
                    proto.getTotalItemsSold());
        }
    }

    public record OrderYearlyResponse(
            String year,
            int orderCount,
            int totalRevenue,
            int totalItemsSold,
            int activeCashiers,
            int uniqueProductsSold) {
        public static OrderYearlyResponse from(pb.order.Order.OrderYearlyResponse proto) {
            return new OrderYearlyResponse(
                    proto.getYear(),
                    proto.getOrderCount(),
                    proto.getTotalRevenue(),
                    proto.getTotalItemsSold(),
                    proto.getActiveCashiers(),
                    proto.getUniqueProductsSold());
        }
    }

    public record OrderMonthlyTotalRevenueResponse(
            String year,
            String month,
            int orderCount,
            int totalRevenue,
            int totalItemsSold) {
        public static OrderMonthlyTotalRevenueResponse from(pb.order.Order.OrderMonthlyTotalRevenueResponse proto) {
            return new OrderMonthlyTotalRevenueResponse(
                    proto.getYear(),
                    proto.getMonth(),
                    proto.getOrderCount(),
                    proto.getTotalRevenue(),
                    proto.getTotalItemsSold());
        }
    }

    public record OrderYearlyTotalRevenueResponse(
            String year,
            int orderCount,
            int totalRevenue,
            int totalItemsSold,
            int activeCashiers,
            int uniqueProductsSold) {
        public static OrderYearlyTotalRevenueResponse from(pb.order.Order.OrderYearlyTotalRevenueResponse proto) {
            return new OrderYearlyTotalRevenueResponse(
                    proto.getYear(),
                    proto.getOrderCount(),
                    proto.getTotalRevenue(),
                    proto.getTotalItemsSold(),
                    proto.getActiveCashiers(),
                    proto.getUniqueProductsSold());
        }
    }

    public record ApiResponseOrder(
            String status,
            String message,
            OrderResponse data) {
        public static ApiResponseOrder from(pb.order.Order.ApiResponseOrder proto) {
            return new ApiResponseOrder(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? OrderResponse.from(proto.getData()) : null);
        }
    }

    public record ApiResponseOrderDeleteAt(
            String status,
            String message,
            OrderResponseDeleteAt data) {
        public static ApiResponseOrderDeleteAt from(pb.order.Order.ApiResponseOrderDeleteAt proto) {
            return new ApiResponseOrderDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? OrderResponseDeleteAt.from(proto.getData()) : null);
        }
    }

    public record ApiResponseOrderMonthly(
            String status,
            String message,
            List<OrderMonthlyResponse> data) {
        public static ApiResponseOrderMonthly from(pb.order.Order.ApiResponseOrderMonthly proto) {
            List<OrderMonthlyResponse> list = proto.getDataList().stream()
                    .map(OrderMonthlyResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponseOrderMonthly(
                    proto.getStatus(),
                    proto.getMessage(),
                    list);
        }
    }

    public record ApiResponseOrderYearly(
            String status,
            String message,
            List<OrderYearlyResponse> data) {
        public static ApiResponseOrderYearly from(pb.order.Order.ApiResponseOrderYearly proto) {
            List<OrderYearlyResponse> list = proto.getDataList().stream()
                    .map(OrderYearlyResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponseOrderYearly(
                    proto.getStatus(),
                    proto.getMessage(),
                    list);
        }
    }

    public record ApiResponseOrderMonthlyTotalRevenue(
            String status,
            String message,
            List<OrderMonthlyTotalRevenueResponse> data) {
        public static ApiResponseOrderMonthlyTotalRevenue from(
                pb.order.Order.ApiResponseOrderMonthlyTotalRevenue proto) {
            List<OrderMonthlyTotalRevenueResponse> list = proto.getDataList().stream()
                    .map(OrderMonthlyTotalRevenueResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponseOrderMonthlyTotalRevenue(
                    proto.getStatus(),
                    proto.getMessage(),
                    list);
        }
    }

    public record ApiResponseOrderYearlyTotalRevenue(
            String status,
            String message,
            List<OrderYearlyTotalRevenueResponse> data) {
        public static ApiResponseOrderYearlyTotalRevenue from(pb.order.Order.ApiResponseOrderYearlyTotalRevenue proto) {
            List<OrderYearlyTotalRevenueResponse> list = proto.getDataList().stream()
                    .map(OrderYearlyTotalRevenueResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponseOrderYearlyTotalRevenue(
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

    public record ApiResponsePaginationOrder(
            String status,
            String message,
            List<OrderResponse> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationOrder from(pb.order.OrderQuery.ApiResponsePaginationOrder proto) {
            List<OrderResponse> list = proto.getDataList().stream()
                    .map(OrderResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationOrder(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPagination() ? PaginationMeta.from(proto.getPagination()) : null);
        }
    }

    public record ApiResponsePaginationOrderDeleteAt(
            String status,
            String message,
            List<OrderResponseDeleteAt> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationOrderDeleteAt from(
                pb.order.OrderQuery.ApiResponsePaginationOrderDeleteAt proto) {
            List<OrderResponseDeleteAt> list = proto.getDataList().stream()
                    .map(OrderResponseDeleteAt::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationOrderDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPagination() ? PaginationMeta.from(proto.getPagination()) : null);
        }
    }

    public record SimpleResponse(
            String status,
            String message) {
        public static SimpleResponse from(pb.order.Order.ApiResponseOrderDelete proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }

        public static SimpleResponse from(pb.order.Order.ApiResponseOrderAll proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }
    }
}
