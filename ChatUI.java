package com.teste.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat cheio (cobre a tela toda, PC e mobile), refeito igual aos prints de
 * referencia que o usuario mandou (ver ~/Area de trabalho/prints): campo de
 * mensagem + "+"/"-" no topo, log + lista de jogadores lado a lado no meio,
 * abas (Local/Global/extras) + Close embaixo. So' Local e Global existem de
 * fabrica (sem Trade, a pedido do usuario) - "+" abre uma lista pra adicionar
 * uma aba extra (English/Portuguese/Spanish/Help), "-" fecha a aba extra
 * ATUAL (Local/Global nunca fecham). Ainda nao fala com o servidor de
 * verdade (nenhuma das abas tem um canal de rede próprio ainda) - cada aba
 * so' guarda seu proprio log local, igual o ChatUI antigo (mesma ideia do
 * chatlogic.gd otimizado: 1 Label que cresce, nao 1 Label por mensagem).
 */
public class ChatUI {

    private static final int MAX_MENSAGENS = 60;
    private static final String ABA_LOCAL = "Local";
    private static final String ABA_GLOBAL = "Global";
    private static final String[] ABAS_ADICIONAVEIS = {"English", "Portuguese", "Spanish", "Help"};

    private final Stage stage;
    private final Skin skin;
    private final Table janela;
    private final TextField campoTexto;
    private final Label logLabel;
    private final ScrollPane scrollLog;
    private final Table listaJogadoresBox;
    private final Label cabecalhoJogadores;
    private final Label contadorJogadores;
    private final Table linhaAbas;
    private final Table popupAdicionar;

    /** Ordem de insercao importa (Local/Global sempre primeiro, extras depois
     * na ordem que foram adicionadas) - LinkedHashMap preserva isso. */
    private final Map<String, Array<String>> mensagensPorAba = new LinkedHashMap<>();
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
        this.stage = stage;
        this.skin = skin;
        this.nomeJogadorLocal = nomeJogadorLocal;
        this.corJogadorLocal = corDaClasse(classeJogadorLocal);
        mensagensPorAba.put(ABA_LOCAL, new Array<>());
        mensagensPorAba.put(ABA_GLOBAL, new Array<>());

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

    private Table criarPopupAdicionar() {
        Table popup = new Table();
        popup.setBackground(skin.getDrawable("popup-painel"));
        popup.pad(10);
        // Largura fixa de volta (nao mais esticando) - a pedido do usuario,
        // que preferiu manter o tamanho do botao e so' encolher o TEXTO que
        // nao cabia. Fonte trocada pra "botao-pequeno-font" (martel 20px, ja
        // existe no skin) - a "cinza-popup" padrao usa fonteBotao (32px),
        // grande demais pra "Portuguese" caber em 200px sem vazar.
        popup.defaults().width(200).height(56).padBottom(8);
        for (String nome : ABAS_ADICIONAVEIS) {
            TextButton botao = new TextButton(nome, skin, "cinza-popup");
            botao.getLabel().setStyle(new Label.LabelStyle(skin.getFont("botao-pequeno-font"), botao.getLabel().getStyle().fontColor));
            botao.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    adicionarAba(nome);
                    popupAdicionar.setVisible(false);
                }
            });
            popup.add(botao).row();
        }
        TextButton cancelar = new TextButton("Cancel", skin, "vermelho-popup");
        cancelar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { popupAdicionar.setVisible(false); }
        });
        popup.add(cancelar).padBottom(0);

        // Centralizada na tela (a pedido do usuario) - antes ficava ancorada
        // no canto superior direito, embaixo do botao "+".
        Table ancora = new Table();
        ancora.setFillParent(true);
        ancora.center();
        ancora.add(popup);
        return ancora;
    }

    private void adicionarAba(String nome) {
        if (!mensagensPorAba.containsKey(nome)) mensagensPorAba.put(nome, new Array<>());
        abaAtual = nome;
        reconstruirAbas();
        reconstruirLog();
        atualizarCampoTexto();
    }

    /** Local/Global nunca fecham (a pedido do usuario) - "-" so' tem efeito
     * numa aba extra (English/Portuguese/Spanish/Help) adicionada via "+". */
    private void fecharAbaAtual() {
        if (abaAtual.equals(ABA_LOCAL) || abaAtual.equals(ABA_GLOBAL)) return;
        mensagensPorAba.remove(abaAtual);
        abaAtual = ABA_LOCAL;
        reconstruirAbas();
        reconstruirLog();
        atualizarCampoTexto();
    }

    private void trocarAba(String nome) {
        abaAtual = nome;
        reconstruirAbas();
        reconstruirLog();
        atualizarCampoTexto();
    }

    /** Global e' so' leitura (a pedido do usuario) - desabilita o campo de
     * mensagem enquanto essa aba estiver selecionada, soltando o foco de
     * teclado se estava digitando nela quando a troca aconteceu. */
    private void atualizarCampoTexto() {
        boolean global = abaAtual.equals(ABA_GLOBAL);
        campoTexto.setDisabled(global);
        if (global && estaDigitando()) stage.setKeyboardFocus(null);
    }

    private void reconstruirAbas() {
        linhaAbas.clearChildren();
        for (String nome : mensagensPorAba.keySet()) {
            boolean ativa = nome.equals(abaAtual);
            TextButton botao = new TextButton(nome, skin, ativa ? "verde-popup" : "cinza-popup");
            botao.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { trocarAba(nome); }
            });
            // minWidth (nao width fixo) - abas adicionadas com nome longo
            // (ex: "Portuguese") vazavam do botao de 110px fixo (bug
            // reportado pelo usuario); pad lateral de verdade na propria
            // celula do label faz o botao crescer o suficiente pra caber,
            // sem encolher as abas curtas (Local/Global/Help) que ja cabiam.
            botao.getLabelCell().padLeft(10).padRight(10);
            linhaAbas.add(botao).minWidth(110).height(48).padRight(6);
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
