package com.example.notificationservice.service;

import lombok.AllArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class NotificationService {
    private KafkaTemplate<String,Object> kafkaTemplate;




    @KafkaListener(topics = {"market.cripto.updated", "market.tradfi.updated"}, groupId = "notification.service")
    public void updatePrices(List<String> payload){

    }
}
