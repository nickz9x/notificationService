package com.example.notificationservice.client;

import com.example.notificationservice.dto.CreateAlertRequestDto;
import com.example.notificationservice.enums.PositionType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class AlertClient {
    private final RestClient restClient;
    private String url = "http://localhost:8087/api";

    public AlertClient(RestClient.Builder restClientBuilder, JwtForwardingInterceptor jwtForwardingInterceptor) {
        this.restClient = restClientBuilder
                .baseUrl(url)
                .requestInterceptor(jwtForwardingInterceptor)
                .build();
    }

    /**
     * Usa a copia publica da criacao de alerta (/api/bot/alert). O userId vai
     * por query param porque nao ha JWT no fluxo do Telegram.
     */
    public void createAlert(String userId, PositionType type, String assetId, BigDecimal price) {
        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot/alert")
                        .queryParam("userId", "{userId}")
                        .build(Map.of("userId", userId)))
                .body(new CreateAlertRequestDto(assetId, price, type))
                .retrieve()
                .toBodilessEntity();
    }
}
