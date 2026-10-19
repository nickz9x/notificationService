package com.example.notificationservice.client;

import com.example.notificationservice.dto.PortfolioResponseDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class PortfolioClient {
    private final RestClient restClient;
    private String url = "http://localhost:8087/api";

    public PortfolioClient(RestClient.Builder restClientBuilder, JwtForwardingInterceptor jwtForwardingInterceptor) {
        this.restClient = restClientBuilder
                .baseUrl(url)
                .requestInterceptor(jwtForwardingInterceptor)
                .build();
    }


    public List<PortfolioResponseDto> getPortfolios(String userId){
      return   restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/portfolio/bot")
                        .queryParam("userId",userId)
                        .build()).retrieve()
                .body(new ParameterizedTypeReference<List<PortfolioResponseDto>>() {
                });
    }
}
