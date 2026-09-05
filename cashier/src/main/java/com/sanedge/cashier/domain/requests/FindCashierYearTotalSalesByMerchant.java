package com.sanedge.cashier.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "FindCashierYearTotalSalesByMerchant", description = "Request for cashier yearly total sales by merchant ID")
public class FindCashierYearTotalSalesByMerchant {
    @NotNull
    @Parameter(description = "Merchant ID", example = "1")
    private Long merchantId;

    @NotNull
    @Parameter(description = "Year", example = "2025")
    private Integer year;

    @NotNull
    @Parameter(description = "Year minus one", example = "2024")
    private Integer yearMinusOne;
}
