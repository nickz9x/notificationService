package com.example.notificationservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KafkaAlert(

        String id,

        String symbol,

        @JsonAlias({"currentPrice", "current_price"})
        BigDecimal price

) {

    // Cripto manda "id" (id do CoinGecko) junto com "symbol";
    // tradfi manda so "symbol". Os dois como alias do mesmo campo
    // faziam o "symbol" sobrescrever o "id".
    public String assetId() {
        return id != null ? id : symbol;
    }
}
