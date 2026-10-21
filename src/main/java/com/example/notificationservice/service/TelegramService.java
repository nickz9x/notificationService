package com.example.notificationservice.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Envio de mensagens avulsas para um chat especifico, fora do fluxo de
 * updates do listener (por exemplo, quando um alerta e disparado).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {
    private final TelegramBot bot;

    public void sendHtml(Long chatId, String html) {
        try {
            BaseResponse response = bot.execute(
                    new SendMessage(chatId, html).parseMode(ParseMode.HTML)
            );
            if (!response.isOk()) {
                log.error(
                        "Falha ao enviar mensagem para chatId={}: {}",
                        chatId,
                        response.description()
                );
            }
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem para chatId={}", chatId, e);
        }
    }
}
