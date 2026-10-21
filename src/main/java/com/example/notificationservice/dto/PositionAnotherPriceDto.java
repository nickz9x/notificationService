package com.example.notificationservice.dto;

import java.math.BigDecimal;

/**
 * Corpo do POST de criacao de posicao com preco informado pelo usuario.
 */
public record PositionAnotherPriceDto(String assetId, BigDecimal quantity, BigDecimal price) {
}
