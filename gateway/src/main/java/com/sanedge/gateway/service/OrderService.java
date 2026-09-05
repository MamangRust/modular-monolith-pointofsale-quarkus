package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.OrderDto;
import com.sanedge.gateway.domain.requests.FindAllOrdersRequest;
import com.sanedge.gateway.domain.requests.FindOrdersByMerchantRequest;
import com.sanedge.gateway.domain.requests.GetOrderMonthlyTotalRevenueRequest;
import com.sanedge.gateway.domain.requests.GetOrderMonthlyTotalRevenueByMerchantRequest;
import io.smallrye.mutiny.Uni;

public interface OrderService {
    Uni<OrderDto.ApiResponsePaginationOrder> listOrders(FindAllOrdersRequest request);
    Uni<OrderDto.ApiResponseOrder> getOrder(int id);
    Uni<OrderDto.ApiResponsePaginationOrder> listOrdersByMerchant(FindOrdersByMerchantRequest request);
    Uni<OrderDto.ApiResponsePaginationOrderDeleteAt> getActiveOrders(FindAllOrdersRequest request);
    Uni<OrderDto.ApiResponsePaginationOrderDeleteAt> getTrashedOrders(FindAllOrdersRequest request);
    Uni<OrderDto.ApiResponseOrder> createOrder(OrderDto.CreateRequest body);
    Uni<OrderDto.ApiResponseOrder> updateOrder(int id, OrderDto.UpdateRequest body);
    Uni<OrderDto.ApiResponseOrderDeleteAt> deleteOrder(int id);
    Uni<OrderDto.ApiResponseOrderDeleteAt> restoreOrder(int id);
    Uni<OrderDto.SimpleResponse> deleteOrderPermanent(int id);
    Uni<OrderDto.SimpleResponse> restoreAllOrder();
    Uni<OrderDto.SimpleResponse> deleteAllOrderPermanent();

    // Stats
    Uni<OrderDto.ApiResponseOrderMonthlyTotalRevenue> getMonthlyTotalRevenue(int year, int month);
    Uni<OrderDto.ApiResponseOrderYearlyTotalRevenue> getYearlyTotalRevenue(int year);
    Uni<OrderDto.ApiResponseOrderMonthlyTotalRevenue> getMonthlyTotalRevenueById(GetOrderMonthlyTotalRevenueRequest request);
    Uni<OrderDto.ApiResponseOrderYearlyTotalRevenue> getYearlyTotalRevenueById(int orderId, int year);
    Uni<OrderDto.ApiResponseOrderMonthlyTotalRevenue> getMonthlyTotalRevenueByMerchant(GetOrderMonthlyTotalRevenueByMerchantRequest request);
    Uni<OrderDto.ApiResponseOrderYearlyTotalRevenue> getYearlyTotalRevenueByMerchant(int merchantId, int year);

    Uni<OrderDto.ApiResponseOrderMonthly> getMonthlyRevenue(int year);
    Uni<OrderDto.ApiResponseOrderYearly> getYearlyRevenue(int year);
    Uni<OrderDto.ApiResponseOrderMonthly> getMonthlyRevenueByMerchant(int merchantId, int year);
    Uni<OrderDto.ApiResponseOrderYearly> getYearlyRevenueByMerchant(int merchantId, int year);
}
