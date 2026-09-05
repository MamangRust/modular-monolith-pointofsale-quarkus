package com.sanedge.category.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "FindCategoryYearTotalPriceByMerchant", description = "Request for category yearly total price by merchant ID")
public class FindCategoryYearTotalPriceByMerchant {
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
