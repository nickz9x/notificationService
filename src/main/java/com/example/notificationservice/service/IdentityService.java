package com.example.notificationservice.service;

import com.example.notificationservice.client.IdentityClient;
import com.example.notificationservice.domain.LinkedUser;
import com.example.notificationservice.dto.LinkedUserResponseDto;
import com.example.notificationservice.dto.TelegramLinkDto;
import com.example.notificationservice.repository.LinkedUserRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
@AllArgsConstructor
public class IdentityService {
    private IdentityClient identityClient;
    private LinkedUserRepository linkedUserRepository;
    private ObjectMapper objectMapper;


    public boolean linkUser(TelegramLinkDto dto){
        try {
            identityClient.linkAccount(dto);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @KafkaListener(topics = "linked.users", groupId = "notificationService", batch = "false")
    public void saveLinkedUsers(String payload){
        LinkedUserResponseDto dto = objectMapper.readValue(payload, LinkedUserResponseDto.class);
        LinkedUser linkedUser = new LinkedUser();
        linkedUser.setUserId(UUID.fromString(dto.userId()));
        linkedUser.setChatId(dto.chatId());
        linkedUserRepository.save(linkedUser);
    }

    public LinkedUser getUserByChatId(Long chatId){
        return linkedUserRepository.findByChatId(chatId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
    }

    /**
     * Indica se este chat do Telegram ja foi vinculado a uma conta. A tabela
     * local e alimentada pelo evento do identity-service (topico linked.users).
     */
    public boolean isChatLinked(Long chatId) {
        return linkedUserRepository.findByChatId(chatId).isPresent();
    }

    public LinkedUser findByUserId(String userId){
        return linkedUserRepository.findByUserId(UUID.fromString(userId)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
