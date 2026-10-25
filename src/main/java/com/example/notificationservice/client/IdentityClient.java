package com.example.notificationservice.client;

import com.example.notificationservice.dto.TelegramLinkDto;
import com.example.notificationservice.dto.TelegramRegisterDto;
import com.example.notificationservice.enums.RegistrationStatus;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class IdentityClient {
    private final RestClient restClient;
    private String url = "http://gateway:3006/api";

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

    public RegistrationStatus registerAccount(TelegramRegisterDto dto) {
        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/bot/auth/register")
                            .build()
                    )
                    .body(dto)
                    .retrieve()
                    .toBodilessEntity();

            return RegistrationStatus.SUCCESS;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 409) {
                return RegistrationStatus.CONFLICT;
            }
            if (status == 400) {
                return RegistrationStatus.INVALID;
            }
            return RegistrationStatus.ERROR;
        } catch (Exception e) {
            return RegistrationStatus.ERROR;
        }
    }
}
