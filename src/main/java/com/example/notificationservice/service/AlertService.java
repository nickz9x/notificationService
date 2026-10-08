package com.example.notificationservice.service;

import com.example.notificationservice.domain.Alert;
import com.example.notificationservice.dto.AlertRequestDto;
import com.example.notificationservice.mapper.AlertMapper;
import com.example.notificationservice.repository.AlertRepository;
import lombok.AllArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@AllArgsConstructor
public class AlertService {
    private final AlertRepository alertRepository;
    private KafkaTemplate<String,Object> kafkaTemplate;
    private ObjectMapper objectMapper;
//    @KafkaListener(topics = "alert.created",groupId = "notification.service")
    //tem q enviar um payload assim (key do kafka)
    public void save(String payload) {
        AlertRequestDto alertRequestDto = objectMapper.readValue(payload, AlertRequestDto.class);
        Alert alert = AlertMapper.toAlert(alertRequestDto);
        alertRepository.save(alert);
    }

}
