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
@Schema(description = "Request for category monthly total price by category ID")
public class GetCategoryMonthlyTotalRequest {
    @NotNull
    @Parameter(description = "Category ID", example = "1")
    private Integer categoryId;

    @NotNull
    @Parameter(description = "Year", example = "2026")
    private Integer year;

    @NotNull
    @Parameter(description = "Month", example = "6")
    private Integer month;
}
