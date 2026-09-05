package com.sanedge.category.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "FindCategoryYearTotalPriceById", description = "Request for category yearly total price by category ID")
public class FindCategoryYearTotalPriceById {
    @NotNull
    @Parameter(description = "Category ID", example = "1")
    private Long categoryId;

    @NotNull
    @Parameter(description = "Year", example = "2025")
    private Integer year;

    @NotNull
    @Parameter(description = "Year minus one", example = "2024")
    private Integer yearMinusOne;
}
