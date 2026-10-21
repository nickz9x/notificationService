package com.example.notificationservice.service;

import com.example.notificationservice.client.AlertClient;
import com.example.notificationservice.domain.Alert;
import com.example.notificationservice.domain.LinkedUser;
import com.example.notificationservice.dto.AlertRequestDto;
import com.example.notificationservice.dto.KafkaAlert;
import com.example.notificationservice.enums.AlertStatus;
import com.example.notificationservice.enums.PositionType;
import com.example.notificationservice.mapper.AlertMapper;
import com.example.notificationservice.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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
    private final IdentityService identityService;
    private final TelegramService telegramService;
    private final AlertClient alertClient;

    private static final String ALERT_KEY_PREFIX = "alertas:";
    private static final DateTimeFormatter TELEGRAM_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    /**
     * Criacao de alerta disparada pelo bot do Telegram. A rota usada e a copia
     * publica do portfolioService; a original continua exigindo JWT.
     */
    public void createAlert(String userId, PositionType type, String assetId, BigDecimal price) {
        alertClient.createAlert(userId, type, assetId, price);
    }

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
                        + "> # 💲 `$" + alert.getPrice().setScale(2, RoundingMode.HALF_UP) + "`\n\n"
                        +"> # Preço Atual💲 `$" + currentPrice + "`\n\n"
                        + "-# 📊 Alerta de preço • <t:" + agora + ":R>";

                // O Discord e um destino extra; se o webhook falhar, o alerta
                // ainda precisa chegar no Telegram.
                try {
                    notificationService.sendAlerts(conteudo);
                } catch (Exception e) {
                    log.warn("Falha ao enviar alerta para o Discord: {}", e.getMessage());
                }

                log.info(
                        "TRIGGERED: usuario={}, ativo={}, precoAlvo={}, precoAtual={}",
                        alert.getUserId(),
                        alert.getAssetId(),
                        alert.getPrice(),
                        currentPrice
                );

                // Cada alerta pertence a um usuario: a mensagem bonita vai para
                // o chat do Telegram vinculado a ele.
                try {
                    LinkedUser usuario = identityService.findByUserId(alert.getUserId().toString());
                    telegramService.sendHtml(usuario.getChatId(), telegramMessage(alert, currentPrice));
                } catch (Exception e) {
                    log.warn(
                            "Usuario {} sem Telegram vinculado; alerta {} nao enviado no Telegram",
                            alert.getUserId(),
                            alert.getId()
                    );
                }

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

    /**
     * Mensagem do Telegram em HTML. Os valores dinamicos sao escapados para
     * nao quebrar a formatacao (o ativo e digitado pelo usuario).
     */
    private String telegramMessage(Alert alert, BigDecimal currentPrice) {
        BigDecimal acimaDoAlvo = alert.getPrice().signum() == 0
                ? BigDecimal.ZERO
                : currentPrice.subtract(alert.getPrice())
                        .divide(alert.getPrice(), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        return "🚨 <b>ALERTA ATIVADO</b> 🚨\n"
                + "━━━━━━━━━━━━━━━━━━\n\n"
                + "🪙 <b>" + escapeHtml(alert.getAssetId().toUpperCase()) + "</b>\n\n"
                + "🎯 <b>Preço alvo:</b> <code>$" + formatPrice(alert.getPrice()) + "</code>\n"
                + "💲 <b>Preço atual:</b> <code>$" + formatPrice(currentPrice) + "</code>\n"
                + "📈 <b>Acima do alvo:</b> +"
                + acimaDoAlvo.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%\n\n"
                + "✅ Meta atingida!\n\n"
                + "━━━━━━━━━━━━━━━━━━\n"
                + "📊 <i>Chain Tracker • " + LocalDateTime.now().format(TELEGRAM_DATE) + "</i>";
    }

    private String formatPrice(BigDecimal value) {
        DecimalFormat format = new DecimalFormat(
                "#,##0.##########",
                DecimalFormatSymbols.getInstance(Locale.US)
        );
        return format.format(value);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
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