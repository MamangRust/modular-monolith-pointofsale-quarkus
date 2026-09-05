package com.sanedge.order.domain.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request untuk mengambil total revenue tahunan dari order tertentu")
public class YearTotalRevenueByIdRequest {

    @NotNull(message = "ID order wajib diisi")
    @Schema(description = "ID order", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orderId;

    @NotNull(message = "Tahun wajib diisi")
    @Min(value = 1900, message = "Tahun harus valid")
    @Schema(description = "Tahun (misalnya: 2024)", example = "2024", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer year;
}
