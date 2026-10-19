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
}
