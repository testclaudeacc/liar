package com.teste.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.teste.game.telas.UiSkin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat cheio (cobre a tela toda, PC e mobile), refeito igual aos prints de
 * referencia que o usuario mandou (ver ~/Area de trabalho/prints): campo de
 * mensagem + "+"/"-" no topo, log + lista de jogadores lado a lado no meio,
 * abas (Local/extras) + Close embaixo. So' Local existe de fabrica (sem
 * Trade nem Global, a pedido do usuario) - "+" abre uma barra de icones pra
 * adicionar uma aba extra (English/Portuguese/Spanish/Russian/Help), "-" fecha a aba
 * extra ATUAL (Local nunca fecha). Ainda nao fala com o servidor de
 * verdade (nenhuma das abas tem um canal de rede próprio ainda) - cada aba
 * so' guarda seu proprio log local, igual o ChatUI antigo (mesma ideia do
 * chatlogic.gd otimizado: 1 Label que cresce, nao 1 Label por mensagem).
 */
public class ChatUI {

    private static final int MAX_MENSAGENS = 60;
    /** Igual servidor.py::CHAT_MAX_CARACTERES - passou disso o campo para de digitar. */
    public static final int MAX_CARACTERES = 200;
    /** Cor das mensagens de boas-vindas (Local e canais).*/
    public static final Color COR_BOAS_VINDAS = new Color(0.35f, 1f, 0.35f, 1f); // verde
    private static final java.util.Map<String, String> BOAS_VINDAS = new java.util.HashMap<>();
    static {
        BOAS_VINDAS.put("Portuguese", "Seja bem-vindo! Respeite as regras para evitar puni\u00e7\u00f5es e trate os outros com dignidade! Bom jogo :)");
        BOAS_VINDAS.put("Spanish", "\u00a1Bienvenido! Respeta las reglas para evitar sanciones y trata a los dem\u00e1s con dignidad. \u00a1Buen juego! :)");
        BOAS_VINDAS.put("English", "Welcome! Follow the rules to avoid punishments and treat others with dignity! Have a good game :)");
        BOAS_VINDAS.put("Russian", "\u0414\u043e\u0431\u0440\u043e \u043f\u043e\u0436\u0430\u043b\u043e\u0432\u0430\u0442\u044c! \u0421\u043e\u0431\u043b\u044e\u0434\u0430\u0439\u0442\u0435 \u043f\u0440\u0430\u0432\u0438\u043b\u0430, \u0447\u0442\u043e\u0431\u044b \u0438\u0437\u0431\u0435\u0436\u0430\u0442\u044c \u043d\u0430\u043a\u0430\u0437\u0430\u043d\u0438\u0439, \u0438 \u043e\u0442\u043d\u043e\u0441\u0438\u0442\u0435\u0441\u044c \u043a \u0434\u0440\u0443\u0433\u0438\u043c \u0441 \u0443\u0432\u0430\u0436\u0435\u043d\u0438\u0435\u043c! \u041f\u0440\u0438\u044f\u0442\u043d\u043e\u0439 \u0438\u0433\u0440\u044b :)");
        BOAS_VINDAS.put("Help", "Welcome to Help! Ask your questions here and be patient with each other. Treat everyone with dignity :)");
    }
    private static final Color COR_SISTEMA = new Color(0.68f, 0.68f, 0.68f, 1f); // cinza
    /** Punicoes (mute de spam/toxicidade): vermelho vivo. */
    public static final Color COR_PUNICAO = new Color(1f, 0f, 0f, 1f);
    private static final String ABA_LOCAL = "Local";
    /** Aba extra: icone no atlas + cor de fundo do slot (no "+" e embaixo). */
    private static final class TipoAba {
        final String nome, icone;
        final Color cor;
        TipoAba(String nome, String icone, Color cor) { this.nome = nome; this.icone = icone; this.cor = cor; }
    }
    // Help usa a mesma bandeira do mob voltando pra casa.
    private static final TipoAba[] ABAS_ADICIONAVEIS = {
        new TipoAba("Portuguese", "ui/Brazil", new Color(0.05f, 0.33f, 0.1f, 1f)),   // verde escuro
        new TipoAba("Spanish", "ui/Spain", new Color(0.5f, 0.4f, 0.04f, 1f)),        // amarelo escuro
        new TipoAba("English", "ui/America", new Color(0.07f, 0.15f, 0.45f, 1f)),    // azul escuro
        new TipoAba("Russian", "ui/Russian", new Color(0.45f, 0.06f, 0.06f, 1f)),    // vermelho escuro
        new TipoAba("Help", "ui/items/Flag", new Color(0.36f, 0.14f, 0.52f, 1f)),    // roxo
    };
    private static final int ICONES_POR_LINHA = 3;
    private static final String ICONE_LOCAL = "ui/NotificationIcon";
    private static final Color COR_LOCAL = new Color(0.16f, 0.16f, 0.16f, 1f);
    // Icones de 16px em 3x; botao com folga em volta.
    private static final float TAMANHO_ICONE = 48f;
    private static final float TAMANHO_SLOT = 64f;

    private final Stage stage;
    private final Skin skin;
    private final TextureAtlas atlas; // pode ser null (TesteGame) - ai os botoes viram texto
    private final Table janela;
    private final TextField campoTexto;
    // Log: uma linha (Label) por mensagem - as que tem coordenada ganham o
    // icone do mapa na frente e abrem o mapa ao clicar (ver linkDeCoordenada).
    private final Table logTabela = new Table();
    private final ScrollPane scrollLog;
    private final Table listaJogadoresBox;
    private final Label cabecalhoJogadores;
    private final Label contadorJogadores;
    private final Table linhaAbas;
    private final Table popupAdicionar;

    /** Ordem de insercao importa (Local sempre primeiro, extras depois
     * na ordem que foram adicionadas) - LinkedHashMap preserva isso. */
    private final Map<String, Array<String>> mensagensPorAba = new LinkedHashMap<>();
    private final Map<String, TextButton.TextButtonStyle> cacheEstilos = new java.util.HashMap<>();
    /** Quem esta em cada chat (lista da direita), igual membros de um grupo:
     * Local = jogadores por perto (WorldScreen, todo frame); extras = membros
     * do canal que o servidor manda (chat_members). */
    private final Map<String, List<String>> membrosPorAba = new java.util.HashMap<>();

    /** Avisa quem fala com o servidor (WorldScreen) quando o jogador entra/sai
     * de um chat extra. Sem ouvinte (TesteGame) a lista fica so' com ele mesmo. */
    public interface OuvinteCanais {
        void entrou(String canal);
        void saiu(String canal);
        /** Mandou mensagem na aba Local. True = foi pro servidor, que devolve
         * ja censurada pra todos (inclusive quem mandou) - ai nao adiciona
         * aqui direto pra nao duplicar. */
        boolean enviouLocal(String texto);
        /** Idem pra um chat de idioma: o servidor repassa pra todo mundo do canal. */
        boolean enviouCanal(String canal, String texto);
        /** Mensagem privada pra esse player (nome real). */
        boolean enviouPrivado(String destino, String texto);
        /** Mensagem na aba Party (so' existe com party). */
        boolean enviouParty(String texto);
    }
    private OuvinteCanais ouvinteCanais;
    private String abaAtual = ABA_LOCAL;
    private final String nomeJogadorLocal;
    private final Color corJogadorLocal;

    // Mesmas cores de CLASSES em AuthScreen (Mage/Knight/Ranger/Bard) -
    // duplicado aqui (so' 4 cores) porque AuthScreen.CLASSES e' privado e
    // fica num pacote diferente (telas vs raiz) - nao compensa expor so'
    // por isso.
    public static Color corDaClasse(String classe) {
        if (classe == null) return Color.WHITE;
        switch (classe) {
            case "Mage": return new Color(0.68f, 0.28f, 1.0f, 1f);
            case "Ranger": return new Color(0.2f, 0.8f, 0.2f, 1f);
            case "Bard": return new Color(1.0f, 0.84f, 0.0f, 1f);
            default: return new Color(0.75f, 0.75f, 0.75f, 1f); // Knight
        }
    }

    public ChatUI(Stage stage, Skin skin, float larguraTela, float alturaTela, String nomeJogadorLocal, String classeJogadorLocal) {
        this(stage, skin, null, larguraTela, alturaTela, nomeJogadorLocal, classeJogadorLocal);
    }

    public ChatUI(Stage stage, Skin skin, TextureAtlas atlas, float larguraTela, float alturaTela, String nomeJogadorLocal, String classeJogadorLocal) {
        this.stage = stage;
        this.skin = skin;
        this.atlas = atlas;
        this.nomeJogadorLocal = nomeJogadorLocal;
        this.corJogadorLocal = corDaClasse(classeJogadorLocal);
        mensagensPorAba.put(ABA_LOCAL, new Array<>());

        campoTexto = new TextField("", skin);
        campoTexto.setMessageText("");
        campoTexto.setMaxLength(MAX_CARACTERES);

        // Fonte trocada pra "simbolo-font" (dejavu-sans.condensed, gerada
        // direto em 34px - ver UiSkin) - o martel usado por "verde-popup"/
        // "vermelho-popup" nao cobre numero/simbolo direito (mesmo problema
        // ja documentado no deleteBtn de AuthScreen, "100% sure?" sumia o
        // "100%"): "+"/"-" saiam completamente em branco, botao virava um
        // quadrado solido sem nada escrito (achado testando ao vivo, print em
        // mao). Usava "default-font" (20px) + setFontScale(1.6f) antes - isso
        // estica a textura ja rasterizada em 20px pra caber em 32px na tela,
        // borrando o simbolo (achado pelo usuario testando); gerar a fonte
        // direto nesse tamanho final evita o blur e tambem centraliza certo
        // (o layout de TextButton usa a metrica REAL da fonte, nao a
        // escalada, pra centralizar o label).
        TextButton.TextButtonStyle estiloMais = new TextButton.TextButtonStyle(skin.get("verde-popup", TextButton.TextButtonStyle.class));
        estiloMais.font = skin.getFont("simbolo-font");
        TextButton botaoMais = new TextButton("+", estiloMais);
        TextButton.TextButtonStyle estiloMenos = new TextButton.TextButtonStyle(skin.get("vermelho-popup", TextButton.TextButtonStyle.class));
        estiloMenos.font = skin.getFont("simbolo-font");
        TextButton botaoMenos = new TextButton("-", estiloMenos);
        botaoMais.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                popupAdicionar.setVisible(!popupAdicionar.isVisible());
                // Escolher o chat novo nao deve comecar a digitar.
                if (estaDigitando()) stage.setKeyboardFocus(null);
            }
        });
        botaoMenos.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { fecharAbaAtual(); }
        });

        // Botao de coordenada: cola "x.., y.." da posicao do player no campo.
        Button botaoCoord = new Button(skin.get("cinza-popup", TextButton.TextButtonStyle.class));
        TextureRegion iconeMapa = atlas != null ? atlas.findRegion("ui/buttons/MapBtn") : null;
        if (iconeMapa != null) {
            Image img = new Image(iconeMapa);
            img.setScaling(com.badlogic.gdx.utils.Scaling.fit);
            botaoCoord.add(img).size(30);
        }
        botaoCoord.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (fornecedorCoordenadas == null || campoTexto.isDisabled()) return;
                String coord = fornecedorCoordenadas.get();
                if (coord == null) return;
                String atual = campoTexto.getText();
                String novo = atual.isEmpty() || atual.endsWith(" ") ? atual + coord : atual + " " + coord;
                campoTexto.setText(novo);
                campoTexto.setCursorPosition(novo.length());
                stage.setKeyboardFocus(campoTexto);
            }
        });

        Table topo = new Table();
        topo.add(campoTexto).height(44).growX().padRight(8);
        topo.add(botaoCoord).size(44).padRight(6);
        topo.add(botaoMais).size(44).padRight(6);
        topo.add(botaoMenos).size(44);

        this.skinLog = skin;
        // top (nao bottom) - com poucas mensagens o log comeca do topo da caixa.
        logTabela.top().left();
        scrollLog = new ScrollPane(logTabela, skin);
        scrollLog.setFadeScrollBars(false);
        scrollLog.setScrollingDisabled(true, false);
        Table logBox = new Table();
        logBox.setBackground(criarFundo(new Color(0f, 0f, 0f, 0.35f)));
        logBox.add(scrollLog).grow().pad(8);

        cabecalhoJogadores = new Label(ABA_LOCAL, skin, "subtitulo");
        // Label separado (fonte "default", dejavu-sans) so' pra contagem -
        // "subtitulo" (martel) nao cobre numero direito, mesmo problema do
        // "+"/"-" acima (ver comentario la).
        contadorJogadores = new Label("", skin);
        contadorJogadores.setColor(new Color(0.4f, 0.75f, 1f, 1f));
        listaJogadoresBox = new Table();
        // Sem isso, com poucos jogadores (lista bem menor que a altura do
        // ScrollPane), o Table se auto-alinha centralizado DENTRO do espaco
        // do scroll - so' aparecia 1 nome jogado no meio em vez da lista
        // comecando do topo (bug reportado pelo usuario).
        listaJogadoresBox.top();
        Table painelJogadores = new Table();
        painelJogadores.setBackground(skin.getDrawable("painel"));
        painelJogadores.top();
        painelJogadores.add(cabecalhoJogadores).padTop(10).row();
        painelJogadores.add(contadorJogadores).padBottom(10).row();
        ScrollPane scrollJogadores = new ScrollPane(listaJogadoresBox, skin);
        scrollJogadores.setFadeScrollBars(false);
        painelJogadores.add(scrollJogadores).grow().pad(4, 10, 10, 10);

        Table meio = new Table();
        meio.add(logBox).grow().padRight(8);
        meio.add(painelJogadores).width(230).growY();

        linhaAbas = new Table();
        linhaAbas.left();
        reconstruirAbas();

        TextButton botaoFechar = new TextButton("Close", skin, "vermelho-popup");
        botaoFechar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { setVisivel(false); }
        });

        Table rodape = new Table();
        rodape.add(linhaAbas).left().expandX();
        rodape.add(botaoFechar).width(140).height(56).right();

        Table conteudo = new Table();
        conteudo.setBackground(criarFundo(new Color(0.03f, 0.03f, 0.03f, 0.88f)));
        conteudo.pad(16);
        conteudo.add(topo).growX().padBottom(10).row();
        conteudo.add(meio).grow().padBottom(10).row();
        conteudo.add(rodape).growX();

        janela = new Table();
        janela.setFillParent(true);
        janela.add(conteudo).grow();
        stage.addActor(janela);

        popupAdicionar = criarPopupAdicionar();
        stage.addActor(popupAdicionar);
        popupAdicionar.setVisible(false);

        campoTexto.setTextFieldListener((textField, c) -> {
            if (c == '\n' || c == '\r') {
                enviarMensagem(textField.getText());
                textField.setText("");
                stage.setKeyboardFocus(null);
            }
        });
        conteudo.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                // Clique num botao (+, -, abas, Close) nao foca o campo - senao
                // abrir o "+" pra adicionar um chat ja comecava a digitar.
                if (vemDeBotao(event.getTarget())) return;
                // Clique nas mensagens (nome/link/texto) tambem nao comeca a digitar.
                if (event.getTarget() != null && event.getTarget().isDescendantOf(scrollLog)) return;
                // janela.isVisible(): o clique no botao "Close" tambem borbulha
                // pra ca (conteudo e' ancestral dele) - o listener do proprio
                // botao (que fecha a janela E solta o foco, ver setVisivel)
                // roda PRIMEIRO (alvo do toque, mais profundo na arvore), e so'
                // depois esse clicked() aqui (ancestral); sem essa checagem,
                // ele refocava campoTexto IMEDIATAMENTE depois do Close ter
                // acabado de soltar o foco, deixando estaDigitando() == true
                // pra sempre com a janela ja invisivel - travava o movimento
                // (WASD) de vez, mesmo com o chat fechado (bug reportado pelo
                // usuario: "escrever algo e fechar" prendia o jogador no lugar).
                if (!campoTexto.isDisabled() && janela.isVisible()) stage.setKeyboardFocus(campoTexto);
            }
        });

        reconstruirLog();
        janela.setVisible(false);
    }

    /** Painel com 1 botao de icone por aba extra (bandeiras + Help) e o
     * Cancel (ui/Negate) por ultimo, 3 por linha, centralizado na tela.
     * Clicar fora dele tambem fecha. */
    private Table criarPopupAdicionar() {
        Table popup = new Table();
        popup.setBackground(skin.getDrawable("popup-painel"));
        popup.pad(10);
        // enabled (Table vem childrenOnly): clique no fundo do painel entre os
        // icones nao pode "vazar" pra ancora e fechar a barra.
        popup.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        int n = 0;
        for (TipoAba aba : ABAS_ADICIONAVEIS) {
            final String nome = aba.nome;
            Button botao = criarBotaoIcone(aba.icone, nome, estiloSlot(aba.cor, false));
            botao.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    adicionarAba(nome);
                    popupAdicionar.setVisible(false);
                }
            });
            adicionarNaGrade(popup, botao, n++);
        }
        // Sem fundo/borda: so' o icone do Negate aparece.
        Button cancelar = criarBotaoIcone("ui/Negate", "Cancel", estiloSemFundo());
        cancelar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { popupAdicionar.setVisible(false); }
        });
        adicionarNaGrade(popup, cancelar, n);

        Table ancora = new Table();
        ancora.setFillParent(true);
        ancora.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        ancora.center();
        ancora.add(popup);
        ancora.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                if (event.getTarget() == ancora) ancora.setVisible(false);
            }
        });
        return ancora;
    }

    private static boolean vemDeBotao(com.badlogic.gdx.scenes.scene2d.Actor alvo) {
        for (com.badlogic.gdx.scenes.scene2d.Actor a = alvo; a != null; a = a.getParent()) {
            if (a instanceof Button) return true;
        }
        return false;
    }

    private static TipoAba tipoDaAba(String nome) {
        for (TipoAba aba : ABAS_ADICIONAVEIS) if (aba.nome.equals(nome)) return aba;
        return null;
    }

    /** Icone de cada aba (Local = NotificationIcon, extras = bandeira/Help,
     * conversa privada = icone da classe do outro player). */
    private String iconeDaAba(String nome) {
        if (ABA_LOCAL.equals(nome)) return ICONE_LOCAL;
        if (ABA_PARTY.equals(nome)) return ICONE_PARTY;
        if (ehPrivada(nome)) return iconePrivada.get(nome);
        TipoAba aba = tipoDaAba(nome);
        return aba != null ? aba.icone : null;
    }

    private Color corDaAba(String nome) {
        if (ehPrivada(nome)) return COR_PRIVADA;
        if (ABA_PARTY.equals(nome)) return COR_PARTY;
        TipoAba aba = tipoDaAba(nome);
        return aba != null ? aba.cor : COR_LOCAL;
    }

    // ---- Chat da party (servidor.py::handle_pc) ----
    // Aparece sozinho quando entra numa party e some quando sai; "-" nao fecha.
    public static final String ABA_PARTY = "Party";
    private static final String ICONE_PARTY = "ui/buttons/PartyBtn";
    private static final Color COR_PARTY = new Color(0.42f, 0.30f, 0.17f, 1f); // marrom (cor da aba Party do livro)
    /** Cor das mensagens da party (log e balao de fala). */
    public static final Color COR_MSG_PARTY = Color.valueOf("ffe11a");

    /** membros null/vazio = sem party (a aba some). */
    public void definirParty(List<String> membros) {
        boolean tem = membros != null && !membros.isEmpty();
        if (tem) {
            if (!mensagensPorAba.containsKey(ABA_PARTY)) {
                // Logo depois do Local.
                Map<String, Array<String>> novo = new LinkedHashMap<>();
                for (Map.Entry<String, Array<String>> e : mensagensPorAba.entrySet()) {
                    novo.put(e.getKey(), e.getValue());
                    if (e.getKey().equals(ABA_LOCAL)) novo.put(ABA_PARTY, new Array<>());
                }
                mensagensPorAba.clear();
                mensagensPorAba.putAll(novo);
            }
            membrosPorAba.put(ABA_PARTY, new java.util.ArrayList<>(membros));
            reconstruirAbas();
            if (abaAtual.equals(ABA_PARTY)) reconstruirPainelJogadores();
        } else if (mensagensPorAba.remove(ABA_PARTY) != null) {
            membrosPorAba.remove(ABA_PARTY);
            naoLidas.remove(ABA_PARTY);
            if (abaAtual.equals(ABA_PARTY)) {
                abaAtual = ABA_LOCAL;
                reconstruirLog();
                reconstruirPainelJogadores();
            }
            reconstruirAbas();
        }
    }

    /** Mensagem da party (vinda do servidor): igual o Local (nome na cor da
     * classe, texto branco) - so' o balao de fala no mundo e' amarelo. */
    public void adicionarMensagemParty(String nome, Color corNome, String texto) {
        adicionarNaAba(ABA_PARTY, linhaDeJogador(nome, nome, corNome, texto));
    }

    // ---- Conversa privada (botao de chat da janela do jogador) ----
    // Aba "@<nome real>"; so' voce e ele (servidor.py::handle_pm).
    private static final String PREFIXO_PRIVADA = "@";
    private static final Color COR_PRIVADA = new Color(0.12f, 0.24f, 0.30f, 1f);
    private final Map<String, String> iconePrivada = new java.util.HashMap<>();
    private final Map<String, String> nomeExibidoPrivada = new java.util.HashMap<>();

    private static boolean ehPrivada(String aba) { return aba.startsWith(PREFIXO_PRIVADA); }

    private void garantirAbaPrivada(String nomeReal, String nomeExibido, String icone) {
        String aba = PREFIXO_PRIVADA + nomeReal;
        if (icone != null) iconePrivada.put(aba, icone);
        nomeExibidoPrivada.put(aba, nomeExibido);
        if (mensagensPorAba.containsKey(aba)) return;
        mensagensPorAba.put(aba, new Array<>());
        List<String> membros = new java.util.ArrayList<>();
        membros.add(nomeJogadorLocal);
        membros.add(nomeExibido);
        membrosPorAba.put(aba, membros);
        reconstruirAbas();
    }

    /** Abre (ou volta pra) a conversa privada com esse player e mostra o chat. */
    public void abrirConversaPrivada(String nomeReal, String nomeExibido, String iconeClasse) {
        garantirAbaPrivada(nomeReal, nomeExibido, iconeClasse);
        trocarAba(PREFIXO_PRIVADA + nomeReal);
        setVisivel(true);
    }

    /** Mensagem privada recebida/enviada (o servidor devolve a propria tambem).
     * outro* = o outro lado da conversa (a aba); remetente* = quem escreveu. */
    public void adicionarMensagemPrivada(String outroReal, String outroExibido, String outroIcone,
                                         String remetenteExibido, Color corRemetente, String texto) {
        adicionarMensagemPrivada(outroReal, outroExibido, outroIcone, null, remetenteExibido, corRemetente, texto);
    }

    public void adicionarMensagemPrivada(String outroReal, String outroExibido, String outroIcone, String remetenteReal,
                                         String remetenteExibido, Color corRemetente, String texto) {
        garantirAbaPrivada(outroReal, outroExibido, outroIcone);
        adicionarNaAba(PREFIXO_PRIVADA + outroReal, linhaDeJogador(remetenteReal, remetenteExibido, corRemetente, texto));
    }

    /** Estilo vazio (sem up/over/down) - so' o conteudo do botao aparece. */
    private TextButton.TextButtonStyle estiloSemFundo() {
        TextButton.TextButtonStyle estilo = new TextButton.TextButtonStyle();
        estilo.font = skin.getFont("botao-pequeno-font");
        estilo.fontColor = Color.WHITE;
        return estilo;
    }

    private static Color bordaEscura(Color fundo) {
        return fundo.cpy().mul(0.6f, 0.6f, 0.6f, 1f);
    }

    /** Slot na cor da aba, com borda um pouco mais escura que o fundo (pra
     * dar contraste). Ativa (aba selecionada embaixo): fundo mais claro e
     * borda mais grossa. */
    private TextButton.TextButtonStyle estiloSlot(Color cor, boolean ativa) {
        // Cache: reconstruirAbas() roda a cada troca de aba e retangulo() cria textura nova.
        String chave = cor.toString() + ativa;
        TextButton.TextButtonStyle emCache = cacheEstilos.get(chave);
        if (emCache != null) return emCache;
        TextButton.TextButtonStyle estilo = new TextButton.TextButtonStyle(skin.get("cinza-popup", TextButton.TextButtonStyle.class));
        Color fundo = ativa ? cor.cpy().lerp(Color.WHITE, 0.2f) : cor;
        int espessura = ativa ? 2 : 1;
        Color over = fundo.cpy().lerp(Color.WHITE, 0.12f);
        Color down = fundo.cpy().mul(0.7f, 0.7f, 0.7f, 1f);
        estilo.up = UiSkin.retangulo(fundo, bordaEscura(fundo), espessura);
        estilo.over = UiSkin.retangulo(over, bordaEscura(over), espessura);
        estilo.down = UiSkin.retangulo(down, bordaEscura(down), espessura);
        cacheEstilos.put(chave, estilo);
        return estilo;
    }

    /** Botao com o icone do atlas; sem atlas/icone (ex: TesteGame) vira texto. */
    private Button criarBotaoIcone(String caminhoIcone, String textoReserva, TextButton.TextButtonStyle estilo) {
        TextureRegion icone = atlas != null && caminhoIcone != null ? atlas.findRegion(caminhoIcone) : null;
        if (icone != null) {
            Button botao = new Button(estilo);
            botao.add(new Image(icone)).size(TAMANHO_ICONE);
            return botao;
        }
        TextButton tb = new TextButton(textoReserva, estilo);
        tb.getLabel().setStyle(new Label.LabelStyle(skin.getFont("botao-pequeno-font"), tb.getLabel().getStyle().fontColor));
        return tb;
    }

    private void adicionarNaGrade(Table popup, Button botao, int indice) {
        com.badlogic.gdx.scenes.scene2d.ui.Cell<Button> celula = popup.add(botao).minWidth(TAMANHO_SLOT).height(TAMANHO_SLOT).pad(4);
        if (indice % ICONES_POR_LINHA == ICONES_POR_LINHA - 1) celula.row();
    }

    private void adicionarAba(String nome) {
        if (!mensagensPorAba.containsKey(nome)) {
            mensagensPorAba.put(nome, new Array<>());
            // Ate o servidor responder, o grupo tem so' o proprio jogador.
            List<String> soEu = new java.util.ArrayList<>();
            soEu.add(nomeJogadorLocal);
            membrosPorAba.put(nome, soEu);
            if (ouvinteCanais != null) ouvinteCanais.entrou(nome);
            // Boas-vindas no idioma do canal (so' quem entrou ve).
            String boasVindas = BOAS_VINDAS.get(nome);
            if (boasVindas != null) adicionarNaAba(nome, hora() + " [#" + COR_BOAS_VINDAS.toString() + "]" + escaparMarkup(boasVindas) + "[]");
        }
        abaAtual = nome;
        reconstruirAbas();
        reconstruirLog();
        reconstruirPainelJogadores();
    }

    /** Local nunca fecha (a pedido do usuario) - "-" so' tem efeito numa aba
     * extra (English/Portuguese/Spanish/Russian/Help) adicionada via "+". */
    private void fecharAbaAtual() {
        if (abaAtual.equals(ABA_LOCAL) || abaAtual.equals(ABA_PARTY)) return;
        mensagensPorAba.remove(abaAtual);
        membrosPorAba.remove(abaAtual);
        naoLidas.remove(abaAtual);
        if (ehPrivada(abaAtual)) {
            iconePrivada.remove(abaAtual);
            nomeExibidoPrivada.remove(abaAtual);
        } else if (ouvinteCanais != null) {
            ouvinteCanais.saiu(abaAtual);
        }
        abaAtual = ABA_LOCAL;
        reconstruirAbas();
        reconstruirLog();
        reconstruirPainelJogadores();
    }

    private void trocarAba(String nome) {
        abaAtual = nome;
        naoLidas.remove(nome);
        reconstruirAbas();
        reconstruirLog();
        reconstruirPainelJogadores();
    }

    // ---- Retrato do player nas abas privadas + mensagens nao lidas ----
    /** Quem sabe desenhar o player (WorldScreen): o sprite animado dele, ou
     * o icone de reserva (classe) se ele nao estiver por perto/online. */
    public interface FornecedorRetrato {
        com.badlogic.gdx.scenes.scene2d.Actor retrato(String nomeReal, String iconeReserva);
    }
    private FornecedorRetrato fornecedorRetrato;
    private final Map<String, Integer> naoLidas = new java.util.HashMap<>();

    public void setFornecedorRetrato(FornecedorRetrato f) {
        fornecedorRetrato = f;
        reconstruirAbas();
    }

    /** Botao da aba: icone (ou retrato do player no PV) + numero de mensagens
     * nao lidas no canto inferior direito. */
    private Button criarBotaoAba(String nome, boolean ativa) {
        TextButton.TextButtonStyle estilo = estiloSlot(corDaAba(nome), ativa);
        com.badlogic.gdx.scenes.scene2d.Actor figura = null;
        if (ehPrivada(nome) && fornecedorRetrato != null) {
            figura = fornecedorRetrato.retrato(nome.substring(PREFIXO_PRIVADA.length()), iconePrivada.get(nome));
        } else {
            TextureRegion icone = atlas != null && iconeDaAba(nome) != null ? atlas.findRegion(iconeDaAba(nome)) : null;
            if (icone != null) figura = new Image(icone);
        }
        if (figura == null) return criarBotaoIcone(null, nome, estilo); // sem atlas (TesteGame): texto
        Table centro = new Table();
        // Retrato do PV ocupa o slot quase todo (o boneco tem muita sobra
        // transparente em volta, com 48 ficava pequeno).
        if (ehPrivada(nome)) centro.add(figura).size(TAMANHO_SLOT - 2f, TAMANHO_SLOT - 2f);
        else centro.add(figura).size(TAMANHO_ICONE);
        com.badlogic.gdx.scenes.scene2d.ui.Stack pilha = new com.badlogic.gdx.scenes.scene2d.ui.Stack(centro);
        int qtd = naoLidas.getOrDefault(nome, 0);
        if (qtd > 0) {
            Label numero = new Label(qtd > 99 ? "99+" : String.valueOf(qtd), skin, "hud");
            numero.setFontScale(0.6f);
            Table canto = new Table();
            canto.bottom().right();
            canto.add(numero).pad(0, 0, 1, 3);
            pilha.add(canto);
        }
        Button botao = new Button(estilo);
        botao.add(pilha).grow();
        return botao;
    }

    private void reconstruirAbas() {
        linhaAbas.clearChildren();
        for (String nome : mensagensPorAba.keySet()) {
            boolean ativa = nome.equals(abaAtual);
            // Icone em vez do nome, no slot com a cor da aba.
            Button botao = criarBotaoAba(nome, ativa);
            botao.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { trocarAba(nome); }
            });
            if (botao instanceof TextButton) ((TextButton) botao).getLabelCell().padLeft(10).padRight(10);
            linhaAbas.add(botao).minWidth(TAMANHO_SLOT).height(TAMANHO_SLOT).padRight(6);
        }
    }

    private NinePatchDrawable criarFundo(Color cor) {
        Pixmap pm = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pm.setColor(cor);
        pm.fill();
        Texture tex = new Texture(pm);
        pm.dispose();
        return new NinePatchDrawable(new NinePatch(tex, 1, 1, 1, 1));
    }

    private void enviarMensagem(String texto) {
        if (texto == null || texto.trim().isEmpty()) return;
        texto = texto.trim();
        if (texto.length() > MAX_CARACTERES) texto = texto.substring(0, MAX_CARACTERES);
        if (ouvinteCanais != null) {
            boolean foiProServidor = abaAtual.equals(ABA_LOCAL)
                ? ouvinteCanais.enviouLocal(texto)
                : abaAtual.equals(ABA_PARTY) ? ouvinteCanais.enviouParty(texto)
                : ehPrivada(abaAtual)
                    ? ouvinteCanais.enviouPrivado(abaAtual.substring(PREFIXO_PRIVADA.length()), texto)
                    : ouvinteCanais.enviouCanal(abaAtual, texto);
            if (foiProServidor) return;
        }
        adicionarNaAba(abaAtual, linhaDeJogador(nomeJogadorLocal, corJogadorLocal, texto));
    }

    /** Formato pedido pelo usuario: "[HH:MM] Nome: mensagem", com o horario
     * sempre verde, o nome na cor da classe do remetente e a mensagem em
     * branco (cor padrao do Label, ver UiSkin::"chat-log") - 3 cores numa
     * MESMA linha via markup do BitmapFont (fonteChatLog.markupEnabled=true,
     * so' nela, ver UiSkin) em vez de 3 Labels separados por mensagem (1
     * Label soh que cresce continua sendo mais barato, mesma ideia de
     * antes). Color::toString() devolve hex RRGGBBAA, formato que a tag
     * markup [#...] entende direto. */
    public void adicionarMensagemDeTeste(String nome, Color corNome, String texto) {
        adicionarNaAba(abaAtual, linhaDeJogador(nome, corNome, texto));
    }

    /** Mensagem de um jogador no chat Local (vinda do servidor), qualquer que
     * seja a aba selecionada agora. */
    public void adicionarMensagemLocal(String nome, Color corNome, String texto) {
        adicionarNaAba(ABA_LOCAL, linhaDeJogador(nome, corNome, texto));
    }

    /** Idem, com o nome real (clicar no nome abre a janela dele). */
    public void adicionarMensagemLocal(String nomeReal, String nomeExibido, Color corNome, String texto) {
        adicionarNaAba(ABA_LOCAL, linhaDeJogador(nomeReal, nomeExibido, corNome, texto));
    }

    /** Mensagem de um jogador num chat de idioma (vinda do servidor). Ignora
     * se o jogador ja fechou essa aba. */
    public void adicionarMensagemCanal(String canal, String nome, Color corNome, String texto) {
        adicionarNaAba(canal, linhaDeJogador(nome, corNome, texto));
    }

    public void adicionarMensagemCanal(String canal, String nomeReal, String nomeExibido, Color corNome, String texto) {
        adicionarNaAba(canal, linhaDeJogador(nomeReal, nomeExibido, corNome, texto));
    }

    /** Aviso do sistema no chat Local (sem nome): level/skill up, anti-spam... */
    public void adicionarMensagemSistema(String texto) {
        adicionarMensagemSistema(texto, COR_SISTEMA);
    }

    /** Idem, com cor propria (ex: vermelho pro mute de 1 hora). */
    public void adicionarMensagemSistema(String texto, Color cor) {
        adicionarNaAba(ABA_LOCAL, hora() + " [#" + cor.toString() + "]" + escaparMarkup(texto) + "[]");
    }

    private static String hora() {
        java.util.Calendar agora = java.util.Calendar.getInstance();
        return "[GREEN][" + String.format("%02d:%02d", agora.get(java.util.Calendar.HOUR_OF_DAY), agora.get(java.util.Calendar.MINUTE)) + "][]";
    }

    private static String linhaDeJogador(String nome, Color corNome, String texto) {
        return linhaDeJogador(null, nome, corNome, texto);
    }

    /** nomeReal (pode ser null): clicar no nome abre a janela desse player.
     * Formato guardado: [MARCA_JOG nomeReal MARCA_JOG] "[hora] Nome: " MARCA_TEXTO mensagem. */
    private static String linhaDeJogador(String nomeReal, String nome, Color corNome, String texto) {
        String prefixo = nomeReal != null ? MARCA_JOG + nomeReal + MARCA_JOG : "";
        return prefixo + hora() + " [#" + corNome.toString() + "]" + escaparMarkup(nome) + "[]: " + MARCA_TEXTO + escaparMarkup(texto);
    }

    /** "[" digitado pelo jogador viraria tag de cor no markup - "[[" e' o escape. */
    private static String escaparMarkup(String texto) {
        return texto.replace("[", "[[");
    }

    private void adicionarNaAba(String aba, String linha) {
        Array<String> msgs = mensagensPorAba.get(aba);
        if (msgs == null) return;
        msgs.add(linkDeCoordenada(linha));
        if (msgs.size > MAX_MENSAGENS) msgs.removeIndex(0);
        if (aba.equals(abaAtual)) reconstruirLog();
        // Nao lida: aba que nao esta aberta, ou o chat inteiro fechado.
        if (!aba.equals(abaAtual) || !janela.isVisible()) {
            naoLidas.merge(aba, 1, Integer::sum);
            reconstruirAbas();
        }
    }

    private void reconstruirLog() {
        Array<String> msgs = mensagensPorAba.getOrDefault(abaAtual, new Array<>());
        logTabela.clearChildren();
        for (int i = 0; i < msgs.size; i++) {
            String linha = msgs.get(i);
            int[] coord = null;
            String nomeReal = null;
            // Ordem das marcas: [link x,y] [nome real] prefixo MARCA_TEXTO texto.
            if (linha.startsWith(MARCA_LINK)) {
                int fim = linha.indexOf(MARCA_LINK, 1);
                String[] xy = linha.substring(1, fim).split(",");
                coord = new int[]{Integer.parseInt(xy[0]), Integer.parseInt(xy[1])};
                linha = linha.substring(fim + 1);
            }
            if (linha.startsWith(MARCA_JOG)) {
                int fim = linha.indexOf(MARCA_JOG, 1);
                nomeReal = linha.substring(1, fim);
                linha = linha.substring(fim + 1);
            }
            Table l = new Table();
            l.left().top();
            int sep = linha.indexOf(MARCA_TEXTO);
            if (sep < 0) {
                // Sistema (sem nome): uma linha so'.
                Label texto = new Label(linha, skinLog, "chat-log");
                texto.setWrap(true);
                texto.setAlignment(Align.topLeft);
                l.add(texto).growX().top();
            } else {
                // "[hora] Nome: " | (icone do mapa) | mensagem
                Label prefixo = new Label(linha.substring(0, sep), skinLog, "chat-log");
                prefixo.setAlignment(Align.topLeft);
                if (nomeReal != null) {
                    final String quem = nomeReal;
                    prefixo.addListener(new ClickListener() {
                        @Override public void clicked(InputEvent event, float x, float y) {
                            if (ouvinteNome != null) ouvinteNome.abrir(quem);
                        }
                    });
                }
                l.add(prefixo).top();
                Table resto = new Table();
                resto.left().top();
                if (coord != null) {
                    TextureRegion icone = atlas != null ? atlas.findRegion("ui/buttons/MapBtn") : null;
                    if (icone != null) {
                        Image img = new Image(icone);
                        img.setScaling(com.badlogic.gdx.utils.Scaling.fit);
                        float tam = prefixo.getStyle().font.getLineHeight();
                        resto.add(img).size(tam).top().padRight(4);
                    }
                }
                Label texto = new Label(linha.substring(sep + 1), skinLog, "chat-log");
                texto.setWrap(true);
                texto.setAlignment(Align.topLeft);
                resto.add(texto).growX().top();
                if (coord != null) {
                    final int cx = coord[0], cy = coord[1];
                    resto.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
                    resto.addListener(new ClickListener() {
                        @Override public void clicked(InputEvent event, float x, float y) {
                            if (ouvinteCoordenada != null) ouvinteCoordenada.abrir(cx, cy);
                        }
                    });
                }
                l.add(resto).growX().top();
            }
            logTabela.add(l).growX().left().row();
        }
        scrollLog.layout();
        scrollLog.setScrollPercentY(100f);
    }

    // ---- Link de coordenada no chat ("x300, y400") ----
    private Skin skinLog;
    private static final String MARCA_LINK = "\u0001";
    private static final java.util.regex.Pattern RE_COORDENADA = java.util.regex.Pattern.compile(
        "(?i)\\bx\\s*[:=]?\\s*(\\d{1,5})\\s*[,;]?\\s*y\\s*[:=]?\\s*(\\d{1,5})\\b");
    private static final String COR_LINK = "[#55ff55]";

    /** Texto "x.., y.." da posicao atual do player (WorldScreen). */
    private java.util.function.Supplier<String> fornecedorCoordenadas;
    public void setFornecedorCoordenadas(java.util.function.Supplier<String> f) { this.fornecedorCoordenadas = f; }

    // Tambem aceita invertido: "y400, x300".
    private static final java.util.regex.Pattern RE_COORDENADA_YX = java.util.regex.Pattern.compile(
        "(?i)\\by\\s*[:=]?\\s*(\\d{1,5})\\s*[,;]?\\s*x\\s*[:=]?\\s*(\\d{1,5})\\b");

    /** Clicar numa coordenada do chat (WorldScreen abre o mapa la'). */
    public interface OuvinteCoordenada { void abrir(int x, int y); }
    private OuvinteCoordenada ouvinteCoordenada;
    public void setOuvinteCoordenada(OuvinteCoordenada o) { this.ouvinteCoordenada = o; }

    /** Se a mensagem tem coordenada: pinta de verde e poe a marca do link
     * na frente (a linha vira clicavel, com o icone do mapa). */
    private static String linkDeCoordenada(String linha) {
        int inicioTexto = linha.indexOf(MARCA_TEXTO);
        if (inicioTexto < 0) return linha; // so' mensagem de player tem link
        String texto = linha.substring(inicioTexto + 1);
        java.util.regex.Matcher m = RE_COORDENADA.matcher(texto);
        String x, y;
        if (m.find()) {
            x = m.group(1); y = m.group(2);
        } else {
            m = RE_COORDENADA_YX.matcher(texto);
            if (!m.find()) return linha;
            y = m.group(1); x = m.group(2); // y veio primeiro
        }
        String marcada = texto.substring(0, m.start()) + COR_LINK + m.group() + "[]" + texto.substring(m.end());
        return MARCA_LINK + x + "," + y + MARCA_LINK + linha.substring(0, inicioTexto + 1) + marcada;
    }

    private static final String MARCA_JOG = "\u0002";
    private static final String MARCA_TEXTO = "\u0003";

    /** Clicar no nome de quem falou (WorldScreen abre a janela dele). */
    public interface OuvinteNome { void abrir(String nomeReal); }
    private OuvinteNome ouvinteNome;
    public void setOuvinteNome(OuvinteNome o) { this.ouvinteNome = o; }

    /** Jogadores por perto (aba Local). Chamado todo frame enquanto o chat
     * esta visivel (ver WorldScreen::render) - lista curta, custo desprezivel;
     * so' redesenha o painel se a aba Local estiver selecionada. */
    public void atualizarJogadores(List<String> nomes) {
        membrosPorAba.put(ABA_LOCAL, nomes);
        if (abaAtual.equals(ABA_LOCAL)) reconstruirPainelJogadores();
    }

    /** Membros de um chat extra, vindos do servidor (chat_members). Ignora
     * canal que o jogador nao tem mais aberto. */
    public void setMembrosDoCanal(String canal, List<String> nomes) {
        if (!membrosPorAba.containsKey(canal)) return;
        membrosPorAba.put(canal, nomes);
        if (abaAtual.equals(canal)) reconstruirPainelJogadores();
    }

    /** Chats extras abertos agora (pra reentrar nos canais apos reconectar). */
    public List<String> canaisAbertos() {
        List<String> canais = new java.util.ArrayList<>();
        for (String nome : mensagensPorAba.keySet()) {
            if (!nome.equals(ABA_LOCAL) && !nome.equals(ABA_PARTY) && !ehPrivada(nome)) canais.add(nome);
        }
        return canais;
    }

    public void setOuvinteCanais(OuvinteCanais ouvinte) {
        this.ouvinteCanais = ouvinte;
    }

    /** Painel da direita: nome do chat selecionado + quantos/quem esta nele. */
    private void reconstruirPainelJogadores() {
        List<String> nomes = membrosPorAba.getOrDefault(abaAtual, java.util.Collections.emptyList());
        cabecalhoJogadores.setText(ehPrivada(abaAtual) ? nomeExibidoPrivada.getOrDefault(abaAtual, "Private") : abaAtual);
        contadorJogadores.setText(nomes.size() + " Player" + (nomes.size() == 1 ? "" : "s"));
        listaJogadoresBox.clearChildren();
        for (String nome : nomes) {
            listaJogadoresBox.add(new Label(nome, skin)).center().expandX().padBottom(4).row();
        }
    }

    public void setVisivel(boolean visivel) {
        janela.setVisible(visivel);
        if (visivel && naoLidas.remove(abaAtual) != null) reconstruirAbas();
        if (!visivel) {
            popupAdicionar.setVisible(false);
            if (estaDigitando()) stage.setKeyboardFocus(null);
        }
    }

    public boolean isVisivel() {
        return janela.isVisible();
    }

    /** Foca o campo de texto (chamado pelo ENTER do WorldScreen quando o chat
     * ja esta aberto mas ninguem esta digitando ainda). */
    public void focarCampoTexto() {
        if (!campoTexto.isDisabled()) stage.setKeyboardFocus(campoTexto);
    }

    /** True enquanto o campo de texto do chat tem o foco do teclado - usado
     * pelo WorldScreen tanto pra travar o movimento (WASD) quanto pra decidir
     * se a barra de espaco deve digitar um espaco normal em vez de
     * abrir/fechar o chat. */
    public boolean estaDigitando() {
        return stage.getKeyboardFocus() == campoTexto;
    }
}
