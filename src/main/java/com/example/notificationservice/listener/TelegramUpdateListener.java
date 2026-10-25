package com.example.notificationservice.listener;

import com.example.notificationservice.domain.LinkedUser;
import com.example.notificationservice.dto.PortfolioResponseDto;
import com.example.notificationservice.dto.PositionResponseDto;
import com.example.notificationservice.dto.PositionTotalResume;
import com.example.notificationservice.dto.TelegramLinkDto;
import com.example.notificationservice.dto.TelegramRegisterDto;
import com.example.notificationservice.enums.PositionType;
import com.example.notificationservice.enums.RegistrationStatus;
import com.example.notificationservice.service.AlertService;
import com.example.notificationservice.service.IdentityService;
import com.example.notificationservice.service.PortfolioService;
import com.example.notificationservice.service.PositionService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetMyCommands;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class TelegramUpdateListener {
    private final TelegramBot bot;
    private final IdentityService identityService;
    private final PortfolioService portfolioService;
    private final PositionService positionService;
    private final AlertService alertService;

    // O bot e stateless entre updates: cada chat guarda aqui o passo da operacao
    // em andamento (tipo, ativo, quantidade, preco) ate ela terminar.
    private final Map<Long, Conversa> conversas = new ConcurrentHashMap<>();

    private enum Acao {
        ADICIONAR, DIMINUIR, REMOVER, CRIAR_PORTFOLIO, CRIAR_ALERTA, CRIAR_CONTA
    }

    private enum Etapa {
        ESCOLHA_TIPO,
        INFORMAR_ATIVO,
        SELECAO_POSICAO,
        INFORMAR_QUANTIDADE,
        CONFIRMACAO_REMOCAO,
        ESCOLHA_PRECO,
        INFORMAR_PRECO,
        INFORMAR_NOME_PORTFOLIO,
        ALERTA_ESCOLHA_TIPO,
        ALERTA_INFORMAR_ATIVO,
        ALERTA_INFORMAR_PRECO,
        CONTA_INFORMAR_USERNAME,
        CONTA_INFORMAR_EMAIL,
        CONTA_INFORMAR_SENHA
    }

    private static final class Conversa {
        private final String portfolioId;
        private final Acao acao;
        private Etapa etapa;
        private PositionType tipo;
        private String assetId;
        private BigDecimal quantidade;
        private List<PositionResponseDto> posicoes;
        private PositionResponseDto selecionada;
        private String username;
        private String email;

        private Conversa(String portfolioId, Acao acao, Etapa etapa) {
            this.portfolioId = portfolioId;
            this.acao = acao;
            this.etapa = etapa;
        }
    }

    @PostConstruct
    public void start() {
        registrarComandos();
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {

                // 1. TRATA CLIQUE NOS BOTOES (Callback Query)
                if (update.callbackQuery() != null) {
                    tratarCallback(update.callbackQuery());
                    continue; // Pula para o próximo update
                }

                // 2. TRATA MENSAGENS DE TEXTO NORMAIS
                if (update.message() == null || update.message().text() == null) {
                    continue;
                }

                tratarMensagem(update.message().chat().id(), update.message().text().trim());
            }

            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    /**
     * Registra os comandos no Telegram para o autocomplete ao digitar "/".
     * Os nomes vao sem a barra, como a API exige. Falha aqui (rede, token)
     * nao pode impedir o bot de subir e continuar recebendo updates.
     */
    private void registrarComandos() {
        try {
            bot.execute(new SetMyCommands(
                    new BotCommand("start", "Inicia o bot"),
                    new BotCommand("help", "Lista os comandos disponíveis"),
                    new BotCommand("vincular", "Vincula sua conta com o código gerado no site"),
                    new BotCommand("criarconta", "Cria uma conta pelo Telegram"),
                    new BotCommand("portfolio", "Consulta seus portfolios"),
                    new BotCommand("newportfolio", "Cria um novo portfolio"),
                    new BotCommand("alerta", "Cria um alerta de preço"),
                    new BotCommand("cancelar", "Cancela a operação em andamento")
            ));
        } catch (Exception e) {
            System.out.println("Não foi possível registrar os comandos do bot: " + e.getMessage());
        }
    }

    private void tratarCallback(CallbackQuery callback) {
        Long chatId = callback.message().chat().id();
        String callbackData = callback.data();

        // Sem responder, o Telegram deixa o botao girando "carregando".
        bot.execute(new AnswerCallbackQuery(callback.id()));

        try {
            if (callbackData.startsWith("portfolio_id_")) {
                executarAcaoDoPortfolio(chatId, callbackData.replace("portfolio_id_", ""));
            } else if (callbackData.startsWith("refresh_portfolio_")) {
                executarAcaoDoPortfolio(chatId, callbackData.replace("refresh_portfolio_", ""));
            } else if (callbackData.startsWith("add_portfolio_")) {
                iniciarOperacao(chatId, callbackData.replace("add_portfolio_", ""), Acao.ADICIONAR);
            } else if (callbackData.startsWith("decrease_portfolio_")) {
                iniciarOperacao(chatId, callbackData.replace("decrease_portfolio_", ""), Acao.DIMINUIR);
            } else if (callbackData.startsWith("remove_portfolio_")) {
                iniciarOperacao(chatId, callbackData.replace("remove_portfolio_", ""), Acao.REMOVER);
            } else if (callbackData.startsWith("add_type_")) {
                escolherTipo(chatId, callbackData.replace("add_type_", ""));
            } else if (callbackData.startsWith("decrease_sel_")) {
                selecionarPosicao(chatId, callbackData.replace("decrease_sel_", ""), Acao.DIMINUIR);
            } else if (callbackData.startsWith("remove_sel_")) {
                selecionarPosicao(chatId, callbackData.replace("remove_sel_", ""), Acao.REMOVER);
            } else if ("remove_confirm_yes".equals(callbackData)) {
                confirmarRemocao(chatId, true);
            } else if ("remove_confirm_no".equals(callbackData)) {
                confirmarRemocao(chatId, false);
            } else if ("price_market".equals(callbackData)) {
                executarComPrecoDeMercado(chatId);
            } else if ("price_custom".equals(callbackData)) {
                pedirPrecoInformado(chatId);
            } else if ("new_portfolio".equals(callbackData)) {
                iniciarCriacaoPortfolio(chatId);
            } else if (callbackData.startsWith("delete_portfolio_")) {
                pedirConfirmacaoExclusao(chatId, callbackData.replace("delete_portfolio_", ""));
            } else if (callbackData.startsWith("portfolio_delete_yes_")) {
                excluirPortfolio(chatId, callbackData.replace("portfolio_delete_yes_", ""));
            } else if (callbackData.startsWith("portfolio_delete_no_")) {
                executarAcaoDoPortfolio(chatId, callbackData.replace("portfolio_delete_no_", ""));
            } else if (callbackData.startsWith("create_alert_")) {
                iniciarCriacaoAlerta(chatId, callbackData.replace("create_alert_", ""));
            } else if (callbackData.startsWith("alert_type_")) {
                escolherTipoAlerta(chatId, callbackData.replace("alert_type_", ""));
            }
        } catch (Exception e) {
            System.out.println("Erro no callback do Telegram: " + e.getMessage());
            send(chatId, "❌ Não foi possível concluir a operação.");
        }
    }

    private void tratarMensagem(Long chatId, String text) {
        Conversa conversa = conversas.get(chatId);

        if (conversa != null) {
            // Texto livre durante uma operacao e o dado que ela esta esperando.
            if (!text.startsWith("/")) {
                tratarTextoDaOperacao(chatId, conversa, text);
                return;
            }
            // /cancelar precisa ver a conversa para saber que havia o que cancelar.
            if (!"/cancelar".equals(text)) {
                conversas.remove(chatId);
            }
        }

        String[] parts = text.split("\\s+", 2);
        String command = parts[0];

        switch (command) {
            case "/start" ->
                    send(chatId, "Chain Tracker está online! 🟢\n\n"
                            + "Use /criarconta para criar uma conta ou /vincular <codigo> para vincular uma conta existente.");

            case "/help" ->
                    send(chatId, "/criarconta - Criar uma conta pelo Telegram\n"
                            + "/vincular <codigo> - Vincular sua conta\n"
                            + "/portfolio - Consultar portfolios\n"
                            + "/newportfolio - Criar portfolio\n"
                            + "/alerta - Criar alerta de preço\n"
                            + "/cancelar - Cancelar a operação em andamento");

            case "/cancelar" -> {
                if (conversas.remove(chatId) != null) {
                    send(chatId, "Operação cancelada.");
                } else {
                    send(chatId, "Nenhuma operação em andamento.");
                }
            }

            case "/teste" ->
                    send(chatId, "testado as " + Instant.now() + " por " + chatId);

            case "/vincular" -> {
                if (identityService.isChatLinked(chatId)) {
                    // Cada chat so pode apontar para uma conta. Sem esta checagem
                    send(chatId, "❌ Este chat já está vinculado a uma conta. "
                            + "Cada chat do Telegram pode ser vinculado a apenas uma conta.");
                } else if (parts.length > 1 && !parts[1].isBlank()) {
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

            case "/criarconta" -> iniciarCriacaoConta(chatId);

            case "/portfolio", "/portfolios" -> listarPortfolios(chatId);

            case "/newportfolio" -> iniciarCriacaoPortfolio(chatId);

            case "/alerta" -> iniciarCriacaoAlerta(chatId, null);

            default ->
                    send(chatId, "Comando não reconhecido.");
        }
    }

    private void listarPortfolios(Long chatId) {
        try {
            UUID userByChatId = identityService.getUserByChatId(chatId).getUserId();

            List<PortfolioResponseDto> portfolios = portfolioService.getUserPortfolio(userByChatId.toString());

            InlineKeyboardMarkup teclado = new InlineKeyboardMarkup();

            if (portfolios == null || portfolios.isEmpty()) {
                teclado.addRow(new InlineKeyboardButton("➕ Criar portfólio").callbackData("new_portfolio"));
                sendWithKeyboard(chatId, "📁 Você ainda não possui nenhum portfólio cadastrado.", teclado);
                return;
            }

            StringBuilder mensagem = new StringBuilder("📁 *Seus Portfólios:*\n\n");

            for (PortfolioResponseDto p : portfolios) {
                mensagem.append("💼 *").append(p.name().toUpperCase()).append("*\n");
                mensagem.append("└───────────────\n\n");

                InlineKeyboardButton botao = new InlineKeyboardButton("✨ Acessar " + p.name())
                        .callbackData("portfolio_id_" + p.id());

                teclado.addRow(botao);
            }

            teclado.addRow(new InlineKeyboardButton("➕ Criar portfólio").callbackData("new_portfolio"));
            mensagem.append("👇 _Clique em um dos botões abaixo para gerenciar:_");

            sendWithKeyboard(chatId, mensagem.toString(), teclado);

        } catch (Exception e) {
            System.out.println(e.getMessage());
            send(chatId, "❌ Não foi possível carregar seus portfólios. Crie uma conta com `/criarconta` ou vincule uma existente com `/vincular <codigo>`.");
        }
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
                PositionTotalResume portfolioResume = portfolioService.getPortfolioResume(portfolioId, userByChatId.getUserId().toString());
                String emoji = portfolioResume.portfolioPnlValue().signum() >= 0
                        ? "🟢" : "🔴";

                resposta.append("━━━━━━━━━━━━━━━━━━\n")
                        .append("💰 *Patrimônio total:* ")
                        .append("$ ").append(portfolioResume.portfolioValue())
                        .append("\n\n")
                        .append("📈 *Resultado da carteira:*\n")
                        .append(emoji).append(" $ ")
                        .append(portfolioResume.portfolioPnlValue())
                        .append(" (")
                        .append(portfolioResume.portfolioPnlPercentage())
                        .append("%)\n\n")
                        .append("🏆 *Melhor desempenho:* ")
                        .append(portfolioResume.bestPerformer())
                        .append("\n")
                        .append("📉 *Pior desempenho:* ")
                        .append(portfolioResume.worstPerformer())
                        .append("\n")
                        .append("━━━━━━━━━━━━━━━━━━\n\n");

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
                                    " └ %s PnL: $%s%s (%s%s%%)\n\n",
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

            sendWithKeyboard(chatId, resposta.toString(), tecladoDoPortfolio(portfolioId));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            send(chatId, "❌ Erro ao processar a ação do portfólio.");
        }
    }

    private InlineKeyboardMarkup tecladoDoPortfolio(String portfolioId) {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("➕ Adicionar").callbackData("add_portfolio_" + portfolioId),
                        new InlineKeyboardButton("➖ Diminuir").callbackData("decrease_portfolio_" + portfolioId)
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("🗑️ Remover").callbackData("remove_portfolio_" + portfolioId),
                        new InlineKeyboardButton("🔄 Atualizar").callbackData("refresh_portfolio_" + portfolioId)
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("🔔 Criar alerta").callbackData("create_alert_" + portfolioId)
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("🗑️ Excluir portfólio").callbackData("delete_portfolio_" + portfolioId)
                });
    }

    // ------------------------------------------------------------------
    // Fluxo de criar / excluir portfolio
    // ------------------------------------------------------------------
    public void mandarAlerta(String conteudo,Long chatId){
        send(chatId,conteudo);
    }
    private void iniciarCriacaoPortfolio(Long chatId) {
        conversas.put(chatId, new Conversa(null, Acao.CRIAR_PORTFOLIO, Etapa.INFORMAR_NOME_PORTFOLIO));
        send(chatId, "📁 *Novo portfólio*\n\nEnvie o nome do portfólio.\nPara desistir, use /cancelar.");
    }

    private void criarPortfolio(Long chatId, String nome) {
        Conversa conversa = conversas.get(chatId);

        if (conversa == null || conversa.etapa != Etapa.INFORMAR_NOME_PORTFOLIO) {
            send(chatId, "⚠️ Sessão expirada. Use /newportfolio novamente.");
            return;
        }

        String nomePortfolio = nome.trim();

        try {
            LinkedUser usuario = identityService.getUserByChatId(chatId);
            portfolioService.createPortfolio(usuario.getUserId().toString(), nomePortfolio);
            conversas.remove(chatId);
            send(chatId, "✅ Portfólio *" + nomePortfolio + "* criado.");
            listarPortfolios(chatId);
        } catch (Exception e) {
            System.out.println("Erro ao criar portfolio pelo bot: " + e.getMessage());
            conversas.remove(chatId);
            send(chatId, "❌ Não foi possível criar o portfólio. Tente novamente.");
        }
    }

    private void pedirConfirmacaoExclusao(Long chatId, String portfolioId) {
        InlineKeyboardMarkup teclado = new InlineKeyboardMarkup().addRow(
                new InlineKeyboardButton("✅ Excluir").callbackData("portfolio_delete_yes_" + portfolioId),
                new InlineKeyboardButton("↩️ Cancelar").callbackData("portfolio_delete_no_" + portfolioId));

        sendWithKeyboard(chatId, "⚠️ *Excluir este portfólio?*\n\nTodas as posições dele serão apagadas. Essa ação não pode ser desfeita.", teclado);
    }

    private void excluirPortfolio(Long chatId, String portfolioId) {
        try {
            LinkedUser usuario = identityService.getUserByChatId(chatId);
            portfolioService.deletePortfolio(portfolioId, usuario.getUserId().toString());
            conversas.remove(chatId);
            send(chatId, "🗑️ Portfólio excluído.");
            listarPortfolios(chatId);
        } catch (Exception e) {
            System.out.println("Erro ao excluir portfolio pelo bot: " + e.getMessage());
            send(chatId, "❌ Não foi possível excluir o portfólio. Ele pode já ter sido removido.");
        }
    }

    // ------------------------------------------------------------------
    // Fluxo de criar alerta
    // ------------------------------------------------------------------

    /**
     * Ponto de entrada do comando /alerta e do botao "Criar alerta" do
     * portfolio. O portfolioId so vem pelo botao e serve para reabrir a tela
     * do portfolio depois que o alerta for criado.
     */
    private void iniciarCriacaoAlerta(Long chatId, String portfolioId) {
        if (!identityService.isChatLinked(chatId)) {
            send(chatId, "❌ Crie uma conta com `/criarconta` ou vincule uma existente com `/vincular <codigo>`.");
            return;
        }

        conversas.put(chatId, new Conversa(portfolioId, Acao.CRIAR_ALERTA, Etapa.ALERTA_ESCOLHA_TIPO));

        InlineKeyboardMarkup teclado = new InlineKeyboardMarkup().addRow(
                new InlineKeyboardButton("🪙 Cripto").callbackData("alert_type_CRIPTO"),
                new InlineKeyboardButton("📈 Ação").callbackData("alert_type_STOCK"));

        sendWithKeyboard(chatId, "🔔 *Criar alerta*\n\nQual é o tipo do ativo?", teclado);
    }

    private void escolherTipoAlerta(Long chatId, String tipo) {
        Conversa conversa = conversas.get(chatId);

        if (conversa == null || conversa.acao != Acao.CRIAR_ALERTA
                || conversa.etapa != Etapa.ALERTA_ESCOLHA_TIPO) {
            send(chatId, "⚠️ Sessão expirada. Use /alerta novamente.");
            return;
        }

        conversa.tipo = PositionType.valueOf(tipo);
        conversa.etapa = Etapa.ALERTA_INFORMAR_ATIVO;

        String exemplo = conversa.tipo == PositionType.CRIPTO ? "`bitcoin`" : "`AAPL`";
        send(chatId, "Qual é o ativo do alerta?\nExemplo: " + exemplo);
    }

    private void tratarAtivoAlerta(Long chatId, Conversa conversa, String text) {
        if (text.isBlank()) {
            send(chatId, "⚠️ Envie o nome ou código do ativo.");
            return;
        }

        conversa.assetId = text.trim();
        conversa.etapa = Etapa.ALERTA_INFORMAR_PRECO;
        send(chatId, "Agora envie o preço-alvo do alerta.\nExemplo: `123.45` ou `123,45`.");
    }

    private void tratarPrecoAlerta(Long chatId, Conversa conversa, String text) {
        BigDecimal preco = parseDecimal(text);

        if (preco == null || preco.compareTo(BigDecimal.ZERO) <= 0) {
            send(chatId, "⚠️ Preço inválido. Envie um número maior que zero.");
            return;
        }

        try {
            LinkedUser usuario = identityService.getUserByChatId(chatId);
            alertService.createAlert(usuario.getUserId().toString(), conversa.tipo, conversa.assetId, preco);

            conversas.remove(chatId);
            send(chatId, "✅ Alerta criado para *" + conversa.assetId + "* em `$"
                    + preco.stripTrailingZeros().toPlainString() + "`.\n\n"
                    + "Você será avisado aqui quando o preço for atingido. 🔔");

            // Veio do botao dentro do portfolio: volta para ele.
            if (conversa.portfolioId != null) {
                executarAcaoDoPortfolio(chatId, conversa.portfolioId);
            }
        } catch (Exception e) {
            System.out.println("Erro ao criar alerta pelo bot: " + e.getMessage());
            conversas.remove(chatId);
            send(chatId, "❌ Não foi possível criar o alerta. Verifique o ativo e tente novamente.");
        }
    }

    // ------------------------------------------------------------------
    // Fluxo de criar conta pelo Telegram
    // ------------------------------------------------------------------

    private void iniciarCriacaoConta(Long chatId) {
        if (identityService.isChatLinked(chatId)) {
            send(chatId, "❌ Este chat já está vinculado a uma conta.");
            return;
        }

        conversas.put(chatId, new Conversa(null, Acao.CRIAR_CONTA, Etapa.CONTA_INFORMAR_USERNAME));
        send(chatId, "🆕 *Criar conta pelo Telegram*\n\n"
                + "Envie um nome de usuário com 3 a 30 caracteres.\n"
                + "Pode usar letras, números, ponto, hífen ou sublinhado.\n\n"
                + "Para desistir, use /cancelar.");
    }

    private void tratarUsernameConta(Long chatId, Conversa conversa, String text) {
        if (conversa.acao != Acao.CRIAR_CONTA) {
            return;
        }

        String username = text.trim();

        if (!username.matches("[A-Za-z0-9._-]{3,30}")) {
            send(chatId, "⚠️ Nome de usuário inválido. Use de 3 a 30 caracteres: letras, números, ponto, hífen ou sublinhado.");
            return;
        }

        conversa.username = username;
        conversa.etapa = Etapa.CONTA_INFORMAR_EMAIL;
        send(chatId, "Agora envie seu e-mail.");
    }

    private void tratarEmailConta(Long chatId, Conversa conversa, String text) {
        if (conversa.acao != Acao.CRIAR_CONTA) {
            return;
        }

        String email = text.trim();

        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            send(chatId, "⚠️ E-mail inválido. Envie novamente.");
            return;
        }

        conversa.email = email;
        conversa.etapa = Etapa.CONTA_INFORMAR_SENHA;
        send(chatId, "🔐 Agora envie uma senha com pelo menos 8 caracteres.\n"
                + "A senha não pode começar com /.\n\n"
                + "⚠️ Ela ficará no histórico desta conversa; apague a mensagem depois.\n"
                + "Para desistir, use /cancelar.");
    }

    private void tratarSenhaConta(Long chatId, Conversa conversa, String text) {
        if (conversa.acao != Acao.CRIAR_CONTA) {
            return;
        }

        if (text.length() < 8 || text.length() > 128) {
            send(chatId, "⚠️ A senha precisa ter entre 8 e 128 caracteres. Envie novamente.");
            return;
        }

        RegistrationStatus status = identityService.registerUser(
                new TelegramRegisterDto(conversa.username, conversa.email, text, chatId));

        switch (status) {
            case SUCCESS -> {
                conversas.remove(chatId);
                send(chatId, "✅ *Conta criada e vinculada!*\n\n"
                        + "Use o usuário e a senha que você definiu para entrar no site.\n\n"
                        + "Agora você pode usar /portfolio, /newportfolio e /alerta.");
            }
            case CONFLICT -> {
                conversa.username = null;
                conversa.email = null;
                conversa.etapa = Etapa.CONTA_INFORMAR_USERNAME;
                send(chatId, "⚠️ Este nome de usuário, e-mail ou chat já está em uso.\n"
                        + "Envie outro nome de usuário.");
            }
            case INVALID -> {
                conversa.username = null;
                conversa.email = null;
                conversa.etapa = Etapa.CONTA_INFORMAR_USERNAME;
                send(chatId, "⚠️ Os dados foram recusados. Confira as informações e envie o nome de usuário novamente.");
            }
            case ERROR -> {
                conversas.remove(chatId);
                send(chatId, "❌ Não foi possível criar a conta agora. Tente novamente em instantes.");
            }
        }
    }

    // ------------------------------------------------------------------
    // Fluxo de adicionar / diminuir / remover posicao
    // ------------------------------------------------------------------

    private void iniciarOperacao(Long chatId, String portfolioId, Acao acao) {
        if (acao == Acao.ADICIONAR) {
            conversas.put(chatId, new Conversa(portfolioId, acao, Etapa.ESCOLHA_TIPO));

            InlineKeyboardMarkup teclado = new InlineKeyboardMarkup().addRow(
                    new InlineKeyboardButton("🪙 Cripto").callbackData("add_type_CRIPTO"),
                    new InlineKeyboardButton("📈 Ação").callbackData("add_type_STOCK"));

            sendWithKeyboard(chatId, "➕ *Adicionar posição*\n\nQual é o tipo do ativo?", teclado);
            return;
        }

        LinkedUser usuario = identityService.getUserByChatId(chatId);
        List<PositionResponseDto> posicoes = positionService.getUserPositions(portfolioId, usuario.getUserId().toString());

        if (posicoes.isEmpty()) {
            send(chatId, "📭 Não há posições para alterar neste portfólio.");
            return;
        }

        Conversa conversa = new Conversa(portfolioId, acao, Etapa.SELECAO_POSICAO);
        conversa.posicoes = posicoes;
        conversas.put(chatId, conversa);

        String titulo = acao == Acao.DIMINUIR
                ? "➖ *Diminuir posição*\n\nEscolha a posição:"
                : "🗑️ *Remover posição*\n\nEscolha a posição:";
        String prefixo = acao == Acao.DIMINUIR ? "decrease_sel_" : "remove_sel_";

        sendListaPosicoes(chatId, titulo, posicoes, prefixo);
    }

    private void sendListaPosicoes(Long chatId, String texto, List<PositionResponseDto> posicoes, String callbackPrefix) {
        InlineKeyboardMarkup teclado = new InlineKeyboardMarkup();

        for (int i = 0; i < posicoes.size(); i++) {
            PositionResponseDto posicao = posicoes.get(i);
            teclado.addRow(new InlineKeyboardButton(posicao.symbol() + " • " + posicao.quantity())
                    .callbackData(callbackPrefix + i));
        }

        sendWithKeyboard(chatId, texto, teclado);
    }

    private void escolherTipo(Long chatId, String tipo) {
        Conversa conversa = conversas.get(chatId);

        if (conversa == null || conversa.etapa != Etapa.ESCOLHA_TIPO) {
            send(chatId, "⚠️ Sessão expirada. Abra o portfólio novamente.");
            return;
        }

        conversa.tipo = PositionType.valueOf(tipo);
        conversa.etapa = Etapa.INFORMAR_ATIVO;

        String exemplo = conversa.tipo == PositionType.CRIPTO ? "`bitcoin`" : "`AAPL`";
        send(chatId, "Qual é o ativo?\nExemplo: " + exemplo);
    }

    private void selecionarPosicao(Long chatId, String indice, Acao acao) {
        Conversa conversa = conversas.get(chatId);

        if (conversa == null || conversa.etapa != Etapa.SELECAO_POSICAO || conversa.acao != acao) {
            send(chatId, "⚠️ Sessão expirada. Abra o portfólio novamente.");
            return;
        }

        int posicaoNaLista;
        try {
            posicaoNaLista = Integer.parseInt(indice);
        } catch (NumberFormatException e) {
            return;
        }

        if (posicaoNaLista < 0 || posicaoNaLista >= conversa.posicoes.size()) {
            return;
        }

        conversa.selecionada = conversa.posicoes.get(posicaoNaLista);
        PositionResponseDto posicao = conversa.selecionada;

        if (acao == Acao.DIMINUIR) {
            conversa.etapa = Etapa.INFORMAR_QUANTIDADE;
            send(chatId, "📦 *" + posicao.symbol() + "* tem " + posicao.quantity()
                    + " unidades.\n\nEnvie a quantidade a vender:");
            return;
        }

        // Remover = vender a quantidade inteira da posicao.
        conversa.quantidade = posicao.quantity();
        conversa.etapa = Etapa.CONFIRMACAO_REMOCAO;

        InlineKeyboardMarkup teclado = new InlineKeyboardMarkup().addRow(
                new InlineKeyboardButton("✅ Remover").callbackData("remove_confirm_yes"),
                new InlineKeyboardButton("❌ Cancelar").callbackData("remove_confirm_no"));

        sendWithKeyboard(chatId, "🗑️ Remover toda a posição de *" + posicao.symbol()
                + "* (" + posicao.quantity() + ")?", teclado);
    }

    private void confirmarRemocao(Long chatId, boolean confirmado) {
        Conversa conversa = conversas.get(chatId);

        if (conversa == null || conversa.etapa != Etapa.CONFIRMACAO_REMOCAO) {
            send(chatId, "⚠️ Sessão expirada. Abra o portfólio novamente.");
            return;
        }

        if (!confirmado) {
            conversas.remove(chatId);
            send(chatId, "Operação cancelada.");
            executarAcaoDoPortfolio(chatId, conversa.portfolioId);
            return;
        }

        pedirPreco(chatId, conversa);
    }

    private void tratarTextoDaOperacao(Long chatId, Conversa conversa, String text) {
        switch (conversa.etapa) {
            case INFORMAR_ATIVO -> tratarAtivo(chatId, conversa, text);
            case INFORMAR_QUANTIDADE -> {
                if (conversa.acao == Acao.ADICIONAR) {
                    tratarQuantidadeAdicao(chatId, conversa, text);
                } else {
                    tratarQuantidadeVenda(chatId, conversa, text);
                }
            }
            case INFORMAR_PRECO -> tratarPreco(chatId, conversa, text);
            case INFORMAR_NOME_PORTFOLIO -> criarPortfolio(chatId, text);
            case ALERTA_INFORMAR_ATIVO -> tratarAtivoAlerta(chatId, conversa, text);
            case ALERTA_INFORMAR_PRECO -> tratarPrecoAlerta(chatId, conversa, text);
            case CONTA_INFORMAR_USERNAME -> tratarUsernameConta(chatId, conversa, text);
            case CONTA_INFORMAR_EMAIL -> tratarEmailConta(chatId, conversa, text);
            case CONTA_INFORMAR_SENHA -> tratarSenhaConta(chatId, conversa, text);
            default -> send(chatId, "Use os botões acima para continuar ou /cancelar para sair.");
        }
    }

    private void tratarAtivo(Long chatId, Conversa conversa, String text) {
        if (text.isBlank()) {
            send(chatId, "⚠️ Envie o nome ou código do ativo.");
            return;
        }

        conversa.assetId = text.trim();
        conversa.etapa = Etapa.INFORMAR_QUANTIDADE;

        String exemplo = conversa.tipo == PositionType.CRIPTO ? "0.5" : "10";
        send(chatId, "Agora envie a quantidade.\nExemplo: " + exemplo);
    }

    private void tratarQuantidadeAdicao(Long chatId, Conversa conversa, String text) {
        BigDecimal quantidade = parseDecimal(text);

        if (quantidade == null || quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            send(chatId, "⚠️ Quantidade inválida. Envie um número maior que zero.");
            return;
        }

        conversa.quantidade = quantidade;

        pedirPreco(chatId, conversa);
    }

    private void tratarQuantidadeVenda(Long chatId, Conversa conversa, String text) {
        BigDecimal quantidade = parseDecimal(text);

        if (quantidade == null || quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            send(chatId, "⚠️ Quantidade inválida. Envie um número maior que zero.");
            return;
        }

        if (quantidade.compareTo(conversa.selecionada.quantity()) > 0) {
            send(chatId, "⚠️ Você possui apenas " + conversa.selecionada.quantity() + " unidades dessa posição.");
            return;
        }

        conversa.quantidade = quantidade;

        pedirPreco(chatId, conversa);
    }

    private void tratarPreco(Long chatId, Conversa conversa, String text) {
        BigDecimal preco = parseDecimal(text);

        if (preco == null || preco.compareTo(BigDecimal.ZERO) <= 0) {
            send(chatId, "⚠️ Preço inválido. Envie um número maior que zero.");
            return;
        }

        executarOperacao(chatId, conversa, preco);
    }

    private void pedirPreco(Long chatId, Conversa conversa) {
        conversa.etapa = Etapa.ESCOLHA_PRECO;

        InlineKeyboardMarkup teclado = new InlineKeyboardMarkup().addRow(
                new InlineKeyboardButton("💰 Preço de mercado").callbackData("price_market"),
                new InlineKeyboardButton("✏️ Informar preço").callbackData("price_custom"));

        String pergunta = conversa.acao == Acao.ADICIONAR
                ? "Usar o preço de mercado atual ou informar o preço da compra?"
                : "Usar o preço de mercado atual ou informar o preço da venda?";

        sendWithKeyboard(chatId, "💵 " + pergunta, teclado);
    }

    private void executarComPrecoDeMercado(Long chatId) {
        Conversa conversa = conversas.get(chatId);

        if (conversa == null || conversa.etapa != Etapa.ESCOLHA_PRECO) {
            send(chatId, "⚠️ Sessão expirada. Abra o portfólio novamente.");
            return;
        }

        executarOperacao(chatId, conversa, null);
    }

    private void pedirPrecoInformado(Long chatId) {
        Conversa conversa = conversas.get(chatId);

        if (conversa == null || conversa.etapa != Etapa.ESCOLHA_PRECO) {
            send(chatId, "⚠️ Sessão expirada. Abra o portfólio novamente.");
            return;
        }

        conversa.etapa = Etapa.INFORMAR_PRECO;
        send(chatId, "Envie o preço unitário. Exemplo: `123.45` ou `123,45`.");
    }

    private void executarOperacao(Long chatId, Conversa conversa, BigDecimal preco) {
        try {
            LinkedUser usuario = identityService.getUserByChatId(chatId);
            String userId = usuario.getUserId().toString();

            if (conversa.acao == Acao.ADICIONAR) {
                positionService.addPosition(conversa.portfolioId, userId, conversa.tipo,
                        conversa.assetId, conversa.quantidade, preco);

                send(chatId, "✅ Posição de *" + conversa.assetId + "* adicionada.");
            } else {
                PositionResponseDto posicao = conversa.selecionada;

                positionService.sellPosition(conversa.portfolioId, userId, inferirTipo(posicao),
                        posicao.assetId(), conversa.quantidade, preco);

                String verbo = conversa.acao == Acao.REMOVER ? "removida" : "atualizada";
                send(chatId, "✅ Posição de *" + posicao.symbol() + "* " + verbo + ".");
            }

            conversas.remove(chatId);
            executarAcaoDoPortfolio(chatId, conversa.portfolioId);

        } catch (Exception e) {
            System.out.println("Erro ao executar operação do bot: " + e.getMessage());
            send(chatId, "❌ Não foi possível concluir a operação. Tente novamente.");
        }
    }

    /**
     * O PositionResponseDto nao carrega o tipo. Para stock o assetId e o proprio
     * symbol canonico (GOOG/GOOG); para cripto o assetId e o id do provedor
     * (bitcoin) e o symbol e o ticker (btc). Se um dia o type entrar no DTO,
     * esta inferencia pode sair.
     */
    private PositionType inferirTipo(PositionResponseDto posicao) {
        return posicao.assetId() != null && posicao.assetId().equalsIgnoreCase(posicao.symbol())
                ? PositionType.STOCK
                : PositionType.CRIPTO;
    }

    private BigDecimal parseDecimal(String valor) {
        try {
            return new BigDecimal(valor.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Envio padrão de texto (com suporte a Markdown)
     */
    private void send(Long chatId, String text) {
        bot.execute(new SendMessage(chatId, text).parseMode(ParseMode.Markdown));
    }

    private void sendWithKeyboard(Long chatId, String text, InlineKeyboardMarkup teclado) {
        bot.execute(new SendMessage(chatId, text)
                .parseMode(ParseMode.Markdown)
                .replyMarkup(teclado));
    }
}
