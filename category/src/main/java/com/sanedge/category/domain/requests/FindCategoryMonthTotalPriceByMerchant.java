package com.sanedge.category.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "FindCategoryMonthTotalPriceByMerchant", description = "Request for category monthly total price by merchant ID")
public class FindCategoryMonthTotalPriceByMerchant {
    @NotNull
    @Parameter(description = "Merchant ID", example = "1")
    private Long merchantId;

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
