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
    private static final Color COR_CANCELAR = new Color(0.85f, 0.33f, 0.33f, 1f); // vermelho claro
    // Icones de 16px em 3x; botao com folga em volta.
    private static final float TAMANHO_ICONE = 48f;
    private static final float TAMANHO_SLOT = 64f;

    private final Stage stage;
    private final Skin skin;
    private final TextureAtlas atlas; // pode ser null (TesteGame) - ai os botoes viram texto
    private final Table janela;
    private final TextField campoTexto;
    private final Label logLabel;
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
    private String abaAtual = ABA_LOCAL;
    private final String nomeJogadorLocal;
    private final Color corJogadorLocal;

    // Mesmas cores de CLASSES em AuthScreen (Mage/Knight/Ranger/Bard) -
    // duplicado aqui (so' 4 cores) porque AuthScreen.CLASSES e' privado e
    // fica num pacote diferente (telas vs raiz) - nao compensa expor so'
    // por isso.
    private static Color corDaClasse(String classe) {
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

        Table topo = new Table();
        topo.add(campoTexto).height(44).growX().padRight(8);
        topo.add(botaoMais).size(44).padRight(6);
        topo.add(botaoMenos).size(44);

        logLabel = new Label("", skin, "chat-log");
        logLabel.setWrap(true);
        // topLeft (nao bottomLeft) - com poucas mensagens (ScrollPane maior
        // que o conteudo), bottomLeft deixava o log "flutuando" colado no
        // fundo da caixa em vez de comecar do topo (a pedido do usuario,
        // mesma causa ja corrigida na lista de jogadores ao lado).
        logLabel.setAlignment(Align.topLeft);
        scrollLog = new ScrollPane(logLabel, skin);
        scrollLog.setFadeScrollBars(false);
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
        Button cancelar = criarBotaoIcone("ui/Negate", "Cancel", estiloSlot(COR_CANCELAR, false));
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

    /** Icone de cada aba (Local = NotificationIcon, extras = bandeira/Help). */
    private static String iconeDaAba(String nome) {
        if (ABA_LOCAL.equals(nome)) return ICONE_LOCAL;
        TipoAba aba = tipoDaAba(nome);
        return aba != null ? aba.icone : null;
    }

    private static Color corDaAba(String nome) {
        TipoAba aba = tipoDaAba(nome);
        return aba != null ? aba.cor : COR_LOCAL;
    }

    /** Slot na cor da aba. Ativa (aba selecionada embaixo): fundo mais claro
     * e borda branca grossa pra destacar. */
    private TextButton.TextButtonStyle estiloSlot(Color cor, boolean ativa) {
        // Cache: reconstruirAbas() roda a cada troca de aba e retangulo() cria textura nova.
        String chave = cor.toString() + ativa;
        TextButton.TextButtonStyle emCache = cacheEstilos.get(chave);
        if (emCache != null) return emCache;
        TextButton.TextButtonStyle estilo = new TextButton.TextButtonStyle(skin.get("cinza-popup", TextButton.TextButtonStyle.class));
        Color fundo = ativa ? cor.cpy().lerp(Color.WHITE, 0.2f) : cor;
        Color borda = ativa ? Color.WHITE : cor.cpy().lerp(Color.WHITE, 0.35f);
        int espessura = ativa ? 2 : 1;
        estilo.up = UiSkin.retangulo(fundo, borda, espessura);
        estilo.over = UiSkin.retangulo(fundo.cpy().lerp(Color.WHITE, 0.12f), borda, espessura);
        estilo.down = UiSkin.retangulo(fundo.cpy().mul(0.7f, 0.7f, 0.7f, 1f), borda, espessura);
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
        if (!mensagensPorAba.containsKey(nome)) mensagensPorAba.put(nome, new Array<>());
        abaAtual = nome;
        reconstruirAbas();
        reconstruirLog();
    }

    /** Local nunca fecha (a pedido do usuario) - "-" so' tem efeito numa aba
     * extra (English/Portuguese/Spanish/Russian/Help) adicionada via "+". */
    private void fecharAbaAtual() {
        if (abaAtual.equals(ABA_LOCAL)) return;
        mensagensPorAba.remove(abaAtual);
        abaAtual = ABA_LOCAL;
        reconstruirAbas();
        reconstruirLog();
    }

    private void trocarAba(String nome) {
        abaAtual = nome;
        reconstruirAbas();
        reconstruirLog();
    }

    private void reconstruirAbas() {
        linhaAbas.clearChildren();
        for (String nome : mensagensPorAba.keySet()) {
            boolean ativa = nome.equals(abaAtual);
            // Icone em vez do nome, no slot com a cor da aba.
            Button botao = criarBotaoIcone(iconeDaAba(nome), nome, estiloSlot(corDaAba(nome), ativa));
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
        adicionarMensagemDeTeste(nomeJogadorLocal, corJogadorLocal, texto.trim());
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
        java.util.Calendar agora = java.util.Calendar.getInstance();
        String hora = String.format("%02d:%02d", agora.get(java.util.Calendar.HOUR_OF_DAY), agora.get(java.util.Calendar.MINUTE));
        String linha = "[GREEN][" + hora + "][] [#" + corNome.toString() + "]" + nome + "[]: " + texto;
        Array<String> msgs = mensagensPorAba.get(abaAtual);
        msgs.add(linha);
        if (msgs.size > MAX_MENSAGENS) msgs.removeIndex(0);
        reconstruirLog();
    }

    private void reconstruirLog() {
        Array<String> msgs = mensagensPorAba.getOrDefault(abaAtual, new Array<>());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < msgs.size; i++) {
            if (i > 0) sb.append("\n");
            sb.append(msgs.get(i));
        }
        logLabel.setText(sb.toString());
        scrollLog.layout();
        scrollLog.setScrollPercentY(100f);
    }

    /** Lista de jogadores da direita - sempre mostra quem esta por perto
     * agora, independente da aba de chat selecionada (igual o print de
     * referencia: o cabecalho fica fixo em "Local"). Chamado todo frame
     * enquanto o chat esta visivel (ver WorldScreen::render) - lista curta,
     * custo desprezivel reconstruir. */
    public void atualizarJogadores(List<String> nomes) {
        contadorJogadores.setText(nomes.size() + " Player" + (nomes.size() == 1 ? "" : "s"));
        listaJogadoresBox.clearChildren();
        for (String nome : nomes) {
            listaJogadoresBox.add(new Label(nome, skin)).left().padBottom(4).row();
        }
    }

    public void setVisivel(boolean visivel) {
        janela.setVisible(visivel);
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
