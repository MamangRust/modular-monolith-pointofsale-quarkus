package com.sanedge.cashier.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "FindCashierMonthSalesByMerchant", description = "Request for cashier monthly sales by merchant ID")
public class FindCashierMonthSalesByMerchant {
    @NotNull
    @Parameter(description = "Merchant ID", example = "1")
    private Long merchantId;

    @NotNull
    @Parameter(description = "Year", example = "2025")
    private Integer year;

    @NotNull
    @Parameter(description = "Start month", example = "1")
    private Integer startMonth;

    @NotNull
    @Parameter(description = "End month", example = "12")
    private Integer endMonth;
}
