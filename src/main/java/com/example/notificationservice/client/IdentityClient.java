package com.example.notificationservice.client;

import com.example.notificationservice.dto.TelegramLinkDto;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IdentityClient {
    private final RestClient restClient;
    private String url = "http://localhost:8080/api";

    public IdentityClient(RestClient.Builder restClientBuilder, JwtForwardingInterceptor jwtForwardingInterceptor) {
        this.restClient = restClientBuilder
                .baseUrl(url)
                .requestInterceptor(jwtForwardingInterceptor)
                .build();
    }


    public boolean linkAccount(TelegramLinkDto dto) {
        try {
            Boolean success = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/bot/auth/link")
                            .build()
                    )
                    .body(dto)
                    .retrieve()
                    .body(Boolean.class);

            return success != null && success;
        } catch (Exception e) {
            return false;
        }
    }
}
