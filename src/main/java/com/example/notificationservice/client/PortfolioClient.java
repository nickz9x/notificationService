package com.example.notificationservice.client;

import com.example.notificationservice.dto.PortfolioRequestDto;
import com.example.notificationservice.dto.PortfolioResponseDto;
import com.example.notificationservice.dto.PositionAnotherPriceDto;
import com.example.notificationservice.dto.PositionRequestDto;
import com.example.notificationservice.dto.PositionResponseDto;
import com.example.notificationservice.dto.PositionTotalResume;
import com.example.notificationservice.dto.SellPositionRequestDto;
import com.example.notificationservice.enums.PositionType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class PortfolioClient {
    private final RestClient restClient;
    private String url = "http://gateway:3006/api";

    public PortfolioClient(RestClient.Builder restClientBuilder, OAuth2ClientHttpRequestInterceptor interceptor) {
        this.restClient = restClientBuilder
                .baseUrl(url)
                .requestInterceptor(interceptor)
                .build();
    }


    public List<PortfolioResponseDto> getPortfolios(String userId){
      return   restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot/portfolio")
                        .queryParam("userId",userId)
                        .build()).retrieve()
                .body(new ParameterizedTypeReference<List<PortfolioResponseDto>>() {
                });
    }

    public List<PositionResponseDto>  getUserPositions(String portfolioId,String userId){
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot/portfolio/{portfolioId}/positions")
                        .queryParam("userId","{userId}")
                        .build(Map.of("portfolioId",portfolioId,"userId",userId))
                ).retrieve().body(new ParameterizedTypeReference<List<PositionResponseDto>>() {
                });
    }

    public PositionTotalResume getPortfolioResume(String portfolioId,String userId){
        return  restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot/portfolio/{portfolioId}/resume")
                        .queryParam("userId","{userId}")
                        .build(Map.of("portfolioId",portfolioId,"userId",userId))
                ).retrieve().body(PositionTotalResume.class);
    }

    /**
     * Cria/soma posicao usando o preco de mercado do assetMarketDataService.
     */
    public PositionResponseDto addPositionAtMarketPrice(String portfolioId, String userId, PositionType type,
                                                         String assetId, BigDecimal quantity) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot/portfolio/{portfolioId}/positions/{tipo}/market")
                        .queryParam("userId", "{userId}")
                        .build(Map.of("portfolioId", portfolioId, "userId", userId, "tipo", tipoDo(type))))
                .body(new PositionRequestDto(assetId, quantity))
                .retrieve()
                .body(PositionResponseDto.class);
    }

    /**
     * Cria/soma posicao usando o preco informado pelo usuario.
     */
    public PositionResponseDto addPositionAtCustomPrice(String portfolioId, String userId, PositionType type,
                                                         String assetId, BigDecimal quantity, BigDecimal price) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot/portfolio/{portfolioId}/positions/{tipo}")
                        .queryParam("userId", "{userId}")
                        .build(Map.of("portfolioId", portfolioId, "userId", userId, "tipo", tipoDo(type))))
                .body(new PositionAnotherPriceDto(assetId, quantity, price))
                .retrieve()
                .body(PositionResponseDto.class);
    }

    /**
     * Vende (diminui ou remove) uma posicao. O price vai no corpo para o
     * portfolioService poder usa-lo quando passar a registrar PnL realizado;
     * hoje a venda apenas reduz a quantidade.
     */
    public void sellPosition(String portfolioId, String userId, PositionType type,
                             String assetId, BigDecimal quantity, BigDecimal price) {
        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot/portfolio/{portfolioId}/positions/sell")
                        .queryParam("userId", "{userId}")
                        .build(Map.of("portfolioId", portfolioId, "userId", userId)))
                .body(new SellPositionRequestDto(type, assetId, quantity, price))
                .retrieve()
                .toBodilessEntity();
    }

    public void createPortfolio(String userId, String name) {
        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot/portfolio")
                        .queryParam("userId", "{userId}")
                        .build(Map.of("userId", userId)))
                .body(new PortfolioRequestDto(name))
                .retrieve()
                .toBodilessEntity();
    }

    public void deletePortfolio(String portfolioId, String userId) {
        restClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/bot/portfolio/{portfolioId}")
                        .queryParam("userId", "{userId}")
                        .build(Map.of("portfolioId", portfolioId, "userId", userId)))
                .retrieve()
                .toBodilessEntity();
    }

    private static String tipoDo(PositionType type) {
        return type == PositionType.STOCK ? "stock" : "cripto";
    }
}
