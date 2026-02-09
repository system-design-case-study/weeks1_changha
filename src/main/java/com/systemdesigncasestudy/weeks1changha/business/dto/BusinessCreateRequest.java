package com.systemdesigncasestudy.weeks1changha.business.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BusinessCreateRequest(
    @NotNull Long ownerId,
    @NotBlank String name,
    @NotBlank String category,
    String phone,
    @NotBlank String address,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
) {
}
