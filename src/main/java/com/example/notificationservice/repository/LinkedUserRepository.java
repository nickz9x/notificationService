package com.example.notificationservice.repository;

import com.example.notificationservice.domain.LinkedUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkedUserRepository extends JpaRepository<LinkedUser, UUID> {
    Optional<LinkedUser> findByChatId(Long chatId);
}
