package com.sanedge.order.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "FindOrderMonthRange", description = "Request for order monthly stats range")
public class FindOrderMonthRange {
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
