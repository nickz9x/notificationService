package com.example.notificationservice.service;

import com.example.notificationservice.client.PortfolioClient;
import com.example.notificationservice.dto.PortfolioResponseDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PortfolioService {
    private PortfolioClient client;




    public List<PortfolioResponseDto> getUserPortfolio(String userId){
       return client.getPortfolios(userId);
    }
}
