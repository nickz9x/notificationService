package com.example.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AlertRequestDto(@NotBlank String userId
        , @NotBlank String assetId,
                              @NotNull BigDecimal price) {
}
