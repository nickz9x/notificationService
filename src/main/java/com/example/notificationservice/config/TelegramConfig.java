package com.example.notificationservice.config;

import com.pengrad.telegrambot.TelegramBot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelegramConfig {

    @Bean
    public TelegramBot telegramBot(@Value("${notification.telegram.key}") String token) {
        return new TelegramBot(token);
    }
}
