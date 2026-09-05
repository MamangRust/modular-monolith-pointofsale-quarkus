package com.sanedge.cashier.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "FindCashierYearTotalSalesById", description = "Request for cashier yearly total sales by cashier ID")
public class FindCashierYearTotalSalesById {
    @NotNull
    @Parameter(description = "Cashier ID", example = "101")
    private Long cashierId;

    @NotNull
    @Parameter(description = "Year", example = "2025")
    private Integer year;

    @NotNull
    @Parameter(description = "Year minus one", example = "2024")
    private Integer yearMinusOne;
}
