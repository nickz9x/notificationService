package com.example.notificationservice.service;

import com.example.notificationservice.domain.Alert;
import com.example.notificationservice.dto.AlertRequestDto;
import com.example.notificationservice.dto.KafkaAlert;
import com.example.notificationservice.enums.AlertStatus;
import com.example.notificationservice.mapper.AlertMapper;
import com.example.notificationservice.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationService notificationService;

    private static final String ALERT_KEY_PREFIX = "alertas:";

    @KafkaListener(
            topics = "alert.created",
            groupId = "notification.service"
    )
    public void save(String payload) {

        AlertRequestDto dto = objectMapper.readValue(
                payload,
                AlertRequestDto.class
        );

        Alert alert = AlertMapper.toAlert(dto);
        alert.setStatus(AlertStatus.WAITING);

        alert = alertRepository.save(alert);

        String key = ALERT_KEY_PREFIX + alert.getAssetId();

        Boolean added = stringRedisTemplate.opsForZSet().add(
                key,
                alert.getId().toString(),
                alert.getPrice().doubleValue()
        );

        log.info(
                "ALERTA CRIADO: id={}, usuario={}, ativo={}, preco={}, redisKey={}, added={}",
                alert.getId(),
                alert.getUserId(),
                alert.getAssetId(),
                alert.getPrice(),
                key,
                added
        );
    }

    public void checkAlerts(
            String assetId,
            BigDecimal currentPrice
    ) {

        if (assetId == null || currentPrice == null) {
            log.warn(
                    "Cotação inválida: assetId={}, price={}",
                    assetId,
                    currentPrice
            );
            return;
        }

        String key = ALERT_KEY_PREFIX + assetId;

        Set<String> alertIds =
                stringRedisTemplate.opsForZSet().rangeByScore(
                        key,
                        0,
                        currentPrice.doubleValue()
                );

        if (alertIds == null || alertIds.isEmpty()) {
            return;
        }


        List<UUID> ids = alertIds.stream()
                .map(UUID::fromString)
                .toList();

        List<Alert> alerts = alertRepository.findAllById(ids);

        for (Alert alert : alerts) {

            if (currentPrice.compareTo(alert.getPrice()) >= 0
                    && alert.getStatus() == AlertStatus.WAITING) {
                long agora = Instant.now().getEpochSecond();
                String conteudo = "# 🚨 ALERTA ATIVADO 🚨\n\n"
                        + "> ### **" + alert.getAssetId().toUpperCase() + "** atingiu\n"
                        + "> # 💲 `$" + alert.getPrice().setScale(2) + "`\n\n"
                        +"> # Preço Atual💲 `$" + currentPrice + "`\n\n"
                        + "-# 📊 Alerta de preço • <t:" + agora + ":R>";
                notificationService.sendAlerts(conteudo);
                log.info(
                        "TRIGGERED: usuario={}, ativo={}, precoAlvo={}, precoAtual={}",
                        alert.getUserId(),
                        alert.getAssetId(),
                        alert.getPrice(),
                        currentPrice
                );
                alert.setStatus(AlertStatus.TRIGERED);
                alert.setLastNotification(Instant.now());
                alertRepository.save(alert);
                // Por enquanto apenas registra o disparo.
                // Posteriormente:
                // 1. Atualizar status para TRIGGERED de forma atômica
                // 2. Enfileirar a notificação
                // 3. Remover do ZSET com segurança
            }
        }
    }

    @KafkaListener(
            topics = {
                    "market.cripto.updated",
                    "market.tradfi.updated"
            },
            groupId = "notification.service",
            batch = "true"
    )
    public void updatePrices(List<String> records) {

        for (String record : records) {

            KafkaAlert dto = objectMapper.readValue(
                    record,
                    KafkaAlert.class
            );

            if (dto.assetId() == null || dto.price() == null) {
                log.warn("Evento de mercado inválido: {}", record);
                continue;
            }

            checkAlerts(dto.assetId(), dto.price());
        }
    }
}