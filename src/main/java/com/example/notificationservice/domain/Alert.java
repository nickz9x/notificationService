package com.example.notificationservice.domain;

import com.example.notificationservice.enums.AlertStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    private String assetId;


    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private AlertStatus status = AlertStatus.WAITING;

    @UpdateTimestamp
    private Instant lastNotification;
}
