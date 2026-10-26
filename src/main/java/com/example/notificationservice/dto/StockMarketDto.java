package com.example.notificationservice.dto;

import java.math.BigDecimal;

public record StockMarketDto(
        String symbol,
        BigDecimal currentPrice,
        BigDecimal openingPrice,
        BigDecimal highestPrice,
        BigDecimal lowestPrice
) {

    public StockMarketDto {
        symbol = symbol == null ? null : symbol.toUpperCase();
    }
}
