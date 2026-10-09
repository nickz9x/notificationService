package com.example.notificationservice.service;

import com.example.notificationservice.dto.AlertTriggered;
import lombok.AllArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class NotificationService {
    private KafkaTemplate<String,Object> kafkaTemplate;




    public void checkAlerts(){
        //a mensagem esta chegando, savlar no cache com zset, e mudanca de status
    }
}
