package com.example.notificationservice.dto;

import com.example.notificationservice.enums.PositionType;

import java.math.BigDecimal;

/**
 * Corpo enviado para POST /api/bot/alert no portfolioService.
 * Nao confundir com AlertRequestDto, que representa o evento do Kafka
 * (userId, assetId, price).
 */
public record CreateAlertRequestDto(
        String assetId,
        BigDecimal price,
        PositionType type
) {
}
