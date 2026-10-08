package com.example.notificationservice.domain;

import com.example.notificationservice.enums.AlertStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    private String assetId;

    @Enumerated(EnumType.STRING)
    private AlertStatus status;

    @UpdateTimestamp
    private Instant lastNotification;
}
