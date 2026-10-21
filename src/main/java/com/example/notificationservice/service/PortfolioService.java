package com.example.notificationservice.service;

import com.example.notificationservice.client.PortfolioClient;
import com.example.notificationservice.dto.PortfolioResponseDto;
import com.example.notificationservice.dto.PositionTotalResume;
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

    public PositionTotalResume getPortfolioResume(String portfolioId,String userId){
        return client.getPortfolioResume(portfolioId, userId);
    }

    public void createPortfolio(String userId, String name) {
        client.createPortfolio(userId, name);
    }

    public void deletePortfolio(String portfolioId, String userId) {
        client.deletePortfolio(portfolioId, userId);
    }
}
