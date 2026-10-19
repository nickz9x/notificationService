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
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
                if (update.message() == null
                        || update.message().text() == null) {
                    continue;
                }

                Long chatId = update.message().chat().id();
                String text = update.message().text().trim();

                // Divide a mensagem em até 2 partes: [comando, argumento]
                // Ex: "/vincular 123456" vira parts[0] = "/vincular" e parts[1] = "123456"
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
                        // Verifica se o usuário enviou o código junto (ex: /vincular 123456)
                        if (parts.length > 1 && !parts[1].isBlank()) {
                            String codigo = parts[1].trim();

                            // Chama o seu serviço para validar o código e salvar o chatId
                            boolean sucesso = identityService.linkUser(new TelegramLinkDto(codigo,chatId));

                            if (sucesso) {
                                send(chatId, "Conta vinculada com sucesso! 🎉 Agora você receberá notificações aqui.");
                            } else {
                                send(chatId, "❌ Código inválido ou expirado. Gere um novo código no site.");
                            }
                        } else {
                            // Se mandou apenas /vincular sem o código
                            send(chatId, "⚠️ Por favor, informe o código gerado no site.\nExemplo: `/vincular 123456`");
                        }
                    }

                    case "/portfolio", "/portfolios" -> {
                        try {
                            // 1. Pega o UUID do usuário através do chatId do Telegram
                            UUID userByChatId = identityService.getUserByChatId(chatId).getUserId();

                            // 2. Busca a lista de portfólios do usuário
                            List<PortfolioResponseDto> portfolios = portfolioService.getUserPortfolio(userByChatId.toString());

                            // 3. Valida se a lista está vazia
                            if (portfolios == null || portfolios.isEmpty()) {
                                send(chatId, "📁 Você ainda não possui nenhum portfólio cadastrado.");
                                break;
                            }

                            // 4. Monta a mensagem formatada
                            StringBuilder mensagem = new StringBuilder("📁 *Seus Portfólios:*\n\n");

                            for (PortfolioResponseDto p : portfolios) {
                                mensagem.append("🔹 *Nome:* ").append(p.name()).append("\n");
                                mensagem.append("🆔 *ID:* `").append(p.id()).append("`\n"); // Usar crase deixa o ID copiável no Telegram
                                mensagem.append("----------------------------------\n");
                            }

                            send(chatId, mensagem.toString());

                        } catch (Exception e) {
                            // Trata caso o usuário não esteja vinculado ou ocorra algum erro na API
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
        bot.execute(new SendMessage(chatId, text));
    }

}
