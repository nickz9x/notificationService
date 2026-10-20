package com.example.notificationservice.service;

import com.example.notificationservice.client.PortfolioClient;
import com.example.notificationservice.dto.PositionResponseDto;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PositionService {
    private PortfolioClient portfolioClient;



    public List<PositionResponseDto> getUserPositions(String portfolioId,String userId){
        return portfolioClient.getUserPositions(portfolioId, userId);
    }

}
