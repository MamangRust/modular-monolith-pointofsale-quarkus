package com.sanedge.cashier.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "FindCashierMonthTotalSalesById", description = "Request for cashier monthly total sales by cashier ID")
public class FindCashierMonthTotalSalesById {
    @NotNull
    @Parameter(description = "Cashier ID", example = "101")
    private Long cashierId;

    @NotNull
    @Parameter(description = "Start year", example = "2025")
    private Integer startYear;

    @NotNull
    @Parameter(description = "Start month", example = "1")
    private Integer startMonth;

    @NotNull
    @Parameter(description = "End year", example = "2025")
    private Integer endYear;

    @NotNull
    @Parameter(description = "End month", example = "12")
    private Integer endMonth;
}
