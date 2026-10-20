package com.example.notificationservice.listener;

import com.example.notificationservice.domain.LinkedUser;
import com.example.notificationservice.dto.PortfolioResponseDto;
import com.example.notificationservice.dto.PositionResponseDto;
import com.example.notificationservice.dto.TelegramLinkDto;
import com.example.notificationservice.service.IdentityService;
import com.example.notificationservice.service.PortfolioService;
import com.example.notificationservice.service.PositionService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TelegramUpdateListener {
    private final TelegramBot bot;
    private final IdentityService identityService;
    private final PortfolioService portfolioService;
    private final PositionService positionService;

    @PostConstruct
    public void start() {
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {

                // 1. TRATA CLIQUE NOS BOTÕES (Callback Query)
                if (update.callbackQuery() != null) {
                    Long chatId = update.callbackQuery().message().chat().id();
                    String callbackData = update.callbackQuery().data();

                    if (callbackData.startsWith("portfolio_id_")) {
                        String portfolioId = callbackData.replace("portfolio_id_", "");
                        executarAcaoDoPortfolio(chatId, portfolioId);
                    }
                    continue; // Pula para o próximo update
                }

                // 2. TRATA MENSAGENS DE TEXTO NORMAS
                if (update.message() == null || update.message().text() == null) {
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

                            boolean sucesso = identityService.linkUser(new TelegramLinkDto(codigo, chatId));

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

                            // Criação do teclado inline usando a API do Pengrad
                            InlineKeyboardMarkup teclado = new InlineKeyboardMarkup();

                            for (PortfolioResponseDto p : portfolios) {
                                mensagem.append("💼 *").append(p.name().toUpperCase()).append("*\n");
                                mensagem.append("└───────────────────\n\n");

                                // Cria o botão e o adiciona como uma nova linha no teclado
                                InlineKeyboardButton botao = new InlineKeyboardButton("✨ Acessar " + p.name())
                                        .callbackData("portfolio_id_" + p.id());

                                teclado.addRow(botao);
                            }

                            mensagem.append("👇 _Clique em um dos botões abaixo para gerenciar:_");

                            // Envia a mensagem de texto com os botões anexados
                            sendWithKeyboard(chatId, mensagem.toString(), teclado);

                        } catch (Exception e) {
                            System.out.println(e.getMessage());
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

    /**
     * Ação executada imediatamente após o usuário clicar no botão do Portfólio
     */
    private void executarAcaoDoPortfolio(Long chatId, String portfolioId) {
        try {
            StringBuilder resposta = new StringBuilder();
            LinkedUser userByChatId = identityService.getUserByChatId(chatId);
            List<PositionResponseDto> positions = positionService.getUserPositions(portfolioId, userByChatId.getUserId().toString());

            if (positions.isEmpty()) {
                resposta.append("📭 *Nenhuma posição encontrada para este portfólio\\.*");
            } else {
                resposta.append("📊 *Suas Posições Atuais* 📊\n\n");

                for (PositionResponseDto userPosition : positions) {
                    // Define o emoji com base no PnL (Lucro/Prejuízo)
                    boolean isProfit = userPosition.pnlAmount() != null && userPosition.pnlAmount().compareTo(BigDecimal.ZERO) >= 0;
                    String pnlEmoji = isProfit ? "🟢" : "🔴";
                    String pnlSign = isProfit ? "+" : ""; //

                    resposta.append(String.format(
                            "🪙 %s - %s\n" +
                                    " ├ 📦 Qtd: %s\n" +
                                    " ├ 💵 Preço Médio: $ %s\n" +
                                    " ├ 💰 Valor Atual: $ %s\n" +
                                    " └ %s PnL: %s%s (%s%s%%)\n\n",
                            userPosition.symbol(),
                            userPosition.name(),
                            userPosition.quantity(),
                            userPosition.averagePrice(),
                            userPosition.positionValue(),
                            pnlEmoji,
                            pnlSign, userPosition.pnlAmount(),
                            pnlSign, userPosition.pnlPercentage()
                    ));
                }
            }

            send(chatId, resposta.toString());
        } catch (Exception e) {
            send(chatId, "❌ Erro ao processar a ação do portfólio.");
        }
    }

    /**
     * Envio padrão de texto (com suporte a Markdown)
     */
    private void send(Long chatId, String text) {
        bot.execute(new SendMessage(chatId, text).parseMode(ParseMode.Markdown));
    }

    /**
     * Envio especializado contendo os botões Inline abaixo da mensagem
     */
    private void sendWithKeyboard(Long chatId, String text, InlineKeyboardMarkup teclado) {
        bot.execute(new SendMessage(chatId, text)
                .parseMode(ParseMode.Markdown)
                .replyMarkup(teclado));
    }
}
