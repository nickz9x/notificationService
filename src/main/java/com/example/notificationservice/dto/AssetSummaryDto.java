package com.example.notificationservice.dto;

public record AssetSummaryDto(
        String type,
        String assetId,
        String symbol,
        String name,
        String exchange,
        String status
) {

    public AssetSummaryDto {
        symbol = symbol == null ? null : symbol.toUpperCase();
    }
}
