package com.sanedge.order_item.service;

import java.util.List;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface OrderItemQueryService {
    Uni<ApiResponse<PagedResult<OrderItemResponse>>> findAll(String search, int page, int pageSize);
    Uni<ApiResponse<PagedResult<OrderItemResponseDeleteAt>>> findByActive(String search, int page, int pageSize);
    Uni<ApiResponse<PagedResult<OrderItemResponseDeleteAt>>> findByTrashed(String search, int page, int pageSize);
    Uni<ApiResponse<List<OrderItemResponse>>> findOrderItemByOrder(Integer orderId);
}
