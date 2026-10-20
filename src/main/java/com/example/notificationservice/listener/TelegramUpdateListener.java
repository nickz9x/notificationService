package com.example.notificationservice.listener;

import com.example.notificationservice.domain.LinkedUser;
import com.example.notificationservice.dto.PortfolioResponseDto;
import com.example.notificationservice.dto.TelegramLinkDto;
import com.example.notificationservice.service.IdentityService;
import com.example.notificationservice.service.PortfolioService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TelegramUpdateListener {
    private final TelegramBot bot;
    private final IdentityService identityService;
    private final PortfolioService portfolioService;
    @PostConstruct
    public void start() {
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                    continue;
                }

                Long chatId = update.message().chat().id();
                String text = update.message().text().trim();

                String[] parts = text.split("\\s+", 2);
                String command = parts[0];

                switch (command) {
                    case "/start" ->
                            send(chatId, "Chain Tracker está online! 🟢");

                    case "/help" ->
                            send(chatId, "/vincular <codigo> - Vincular sua conta\n"
                                    + "/portfolios - Consultar portfolios\n"
                                    + "/newportfolio - Criar portfolio");

                    case "/teste" ->
                            send(chatId, "testado as " + Instant.now() + " por " + chatId);

                    case "/vincular" -> {
                        if (parts.length > 1 && !parts[1].isBlank()) {
                            String codigo = parts[1].trim();

                            boolean sucesso = identityService.linkUser(new TelegramLinkDto(codigo,chatId));

                            if (sucesso) {
                                send(chatId, "Conta vinculada com sucesso! 🎉 Agora você receberá notificações aqui.");
                            } else {
                                send(chatId, "❌ Código inválido ou expirado. Gere um novo código no site.");
                            }
                        } else {
                            send(chatId, "⚠️ Por favor, informe o código gerado no site.\nExemplo: `/vincular 123456`");
                        }
                    }

                    case "/portfolio", "/portfolios" -> {
                        try {
                            UUID userByChatId = identityService.getUserByChatId(chatId).getUserId();

                            List<PortfolioResponseDto> portfolios = portfolioService.getUserPortfolio(userByChatId.toString());

                            if (portfolios == null || portfolios.isEmpty()) {
                                send(chatId, "📁 Você ainda não possui nenhum portfólio cadastrado.");
                                break;
                            }

                            StringBuilder mensagem = new StringBuilder("📁 *Seus Portfólios:*\n\n");

                            for (PortfolioResponseDto p : portfolios) {
                            }


                        } catch (Exception e) {
                            send(chatId, "❌ Não foi possível carregar seus portfólios. Certifique-se de que sua conta está vinculada usando `/vincular <codigo>`.");
                        }
                    }

                    default ->
                            send(chatId, "Comando não reconhecido.");
                }
            }

            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private void send(Long chatId, String text) {
    }

}
