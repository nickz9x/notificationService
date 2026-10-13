package com.example.notificationservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;


@Service
public class NotificationService {

    private final RestClient restClient;
    private final String webhookUrl;

    public NotificationService(
            RestClient.Builder restClientBuilder,
            @Value("${notification.discord.webhook-url}") String webhookUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.webhookUrl = webhookUrl;
    }

    public void sendAlerts(String message) {
        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", message))
                .retrieve()
                .toBodilessEntity();
    }
}
