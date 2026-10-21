package com.example.notificationservice.dto;

import java.math.BigDecimal;

/**
 * Corpo do POST de criacao de posicao a preco de mercado. O tipo vai no path
 * (/cripto ou /stock), entao nao precisa repetir no JSON.
 */
public record PositionRequestDto(String assetId, BigDecimal quantity) {
}
