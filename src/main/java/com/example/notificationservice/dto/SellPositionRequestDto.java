package com.example.notificationservice.dto;

import com.example.notificationservice.enums.PositionType;

import java.math.BigDecimal;

/**
 * Corpo do POST de venda. O price e o preco informado pelo usuario; o
 * portfolioService hoje so reduz a quantidade, sem registrar PnL realizado.
 */
public record SellPositionRequestDto(PositionType type, String assetId,
                                     BigDecimal quantity, BigDecimal price) {
}
