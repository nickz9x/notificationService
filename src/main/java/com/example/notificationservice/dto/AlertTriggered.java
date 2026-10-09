package com.example.notificationservice.dto;

import java.math.BigDecimal;

public record AlertTriggered(
        String assetId,
        BigDecimal price,
        String userId
) {}