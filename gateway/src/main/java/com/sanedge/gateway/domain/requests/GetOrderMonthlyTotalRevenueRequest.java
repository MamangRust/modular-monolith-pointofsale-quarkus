package com.sanedge.gateway.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for order monthly total revenue by order ID")
public class GetOrderMonthlyTotalRevenueRequest {
    @NotNull
    @Parameter(description = "Order ID", example = "1")
    private Integer orderId;

    @NotNull
    @Parameter(description = "Year", example = "2026")
    private Integer year;

    @NotNull
    @Parameter(description = "Month", example = "6")
    private Integer month;
}
