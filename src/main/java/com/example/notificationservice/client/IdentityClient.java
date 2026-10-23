package com.example.notificationservice.client;

import com.example.notificationservice.dto.TelegramLinkDto;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IdentityClient {
    private final RestClient restClient;
    private String url = "http://identity-service:3002/api";

    public IdentityClient(RestClient.Builder restClientBuilder, OAuth2ClientHttpRequestInterceptor interceptor) {
        this.restClient = restClientBuilder
                .baseUrl(url)
                .requestInterceptor(interceptor)
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
