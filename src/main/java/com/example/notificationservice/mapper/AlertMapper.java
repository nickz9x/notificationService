package com.example.notificationservice.mapper;

import com.example.notificationservice.domain.Alert;
import com.example.notificationservice.dto.AlertRequestDto;

import java.util.UUID;

public class AlertMapper{

    public static Alert toAlert(AlertRequestDto dto){
        Alert alert = new Alert();
        alert.setUserId(UUID.fromString(dto.userId()));
        alert.setAssetId(dto.assetId());
        alert.setPrice(dto.price());
        return alert;
    }
}
