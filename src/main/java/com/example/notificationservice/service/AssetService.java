package com.example.notificationservice.service;

import com.example.notificationservice.client.AssetMarketDataClient;
import com.example.notificationservice.dto.AssetSummaryDto;
import com.example.notificationservice.dto.CryptoMarketDto;
import com.example.notificationservice.dto.StockMarketDto;
import com.example.notificationservice.enums.PositionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetService {
    private final AssetMarketDataClient client;

    public List<AssetSummaryDto> search(String term) {
        return client.search(term);
    }

    public CryptoMarketDto getCryptoDetails(String assetId) {
        return client.getCryptoMarket(assetId);
    }

    public StockMarketDto getStockDetails(String symbol) {
        return client.getStockMarket(symbol);
    }

    public BigDecimal getCurrentPrice(PositionType type, String assetId) {
        try {
            if (type == PositionType.CRIPTO) {
                CryptoMarketDto crypto = client.getCryptoMarket(assetId);
                return crypto == null ? null : crypto.currentPrice();
            }
            StockMarketDto stock = client.getStockMarket(assetId);
            return stock == null ? null : stock.currentPrice();
        } catch (Exception e) {
            return null;
        }
    }
}
