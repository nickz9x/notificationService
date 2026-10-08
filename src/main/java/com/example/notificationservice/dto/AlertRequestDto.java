package com.example.notificationservice.dto;

import java.math.BigDecimal;

public record AlertRequestDto(String userId, String assetId, BigDecimal price) {
}
