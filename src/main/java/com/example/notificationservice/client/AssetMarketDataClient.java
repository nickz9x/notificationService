package com.example.notificationservice.client;

import com.example.notificationservice.dto.AssetSummaryDto;
import com.example.notificationservice.dto.CryptoMarketDto;
import com.example.notificationservice.dto.StockMarketDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class AssetMarketDataClient {
    private final RestClient restClient;

    public AssetMarketDataClient(RestClient.Builder restClientBuilder, OAuth2ClientHttpRequestInterceptor interceptor) {
        this.restClient = restClientBuilder
                .baseUrl("http://assetmarketdata-service:8082/api")
                .requestInterceptor(interceptor)
                .build();
    }

    public List<AssetSummaryDto> search(String term) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot/assets/search")
                        .queryParam("q", term)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<AssetSummaryDto>>() {
                });
    }

    public CryptoMarketDto getCryptoMarket(String assetId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot/currency/{assetId}")
                        .build(assetId))
                .retrieve()
                .body(CryptoMarketDto.class);
    }

    public StockMarketDto getStockMarket(String symbol) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot/stock/{symbol}")
                        .build(symbol))
                .retrieve()
                .body(StockMarketDto.class);
    }
}
