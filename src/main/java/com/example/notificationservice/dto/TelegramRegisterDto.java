package com.example.notificationservice.dto;

public record TelegramRegisterDto(String username, String email, String password, Long chatId) {
}
