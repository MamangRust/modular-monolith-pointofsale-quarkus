package com.sanedge.cashier.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "FindCashierMonthSalesById", description = "Request for cashier monthly sales by cashier ID")
public class FindCashierMonthSalesById {
    @NotNull
    @Parameter(description = "Cashier ID", example = "101")
    private Long cashierId;

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
