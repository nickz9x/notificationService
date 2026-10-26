package com.example.notificationservice.service;

import com.example.notificationservice.client.PortfolioClient;
import com.example.notificationservice.dto.PositionResponseDto;
import com.example.notificationservice.enums.PositionType;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@AllArgsConstructor
public class PositionService {
    private PortfolioClient portfolioClient;



    public List<PositionResponseDto> getUserPositions(String portfolioId,String userId){
        return portfolioClient.getUserPositions(portfolioId, userId);
    }

    /**
     * Adiciona (ou soma) uma posicao no portfolio.
     * price == null usa o preco de mercado; price informado usa o preco da compra.
     */
    public PositionResponseDto addPosition(String portfolioId, String userId, PositionType type,
                                           String assetId, BigDecimal quantity, BigDecimal price) {
        if (price == null) {
            return portfolioClient.addPositionAtMarketPrice(portfolioId, userId, type, assetId, quantity);
        }
        return portfolioClient.addPositionAtCustomPrice(portfolioId, userId, type, assetId, quantity, price);
    }

    /**
     * Vende (diminui/remove) uma posicao do portfolio.
     * price e o preco da venda informado pelo usuario; o portfolioService hoje
     * apenas reduz a quantidade, sem registrar PnL realizado.
     */
    public void sellPosition(String portfolioId, String userId, PositionType type,
                             String assetId, BigDecimal quantity, BigDecimal price) {
        portfolioClient.sellPosition(portfolioId, userId, type, assetId, quantity, price);
    }

}
