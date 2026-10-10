package com.teste.game.telas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

/**
 * Janela de outro jogador (abre clicando no botao de alvo do topo com um
 * player marcado) - igual o mock "tela desejada": icone de amizade no topo
 * esquerdo (so' se ele for seu amigo), nome no meio, X vermelho no topo
 * direito, foto do player (o sprite dele de agora, com as skins) e 5 botoes
 * embaixo: Friends, Ignore, Party, Trade e Chat. O IconBtn abre uma barrinha
 * em cima da janela com PkIcon/GuildIcon/SellerIcon - cada um liga/desliga
 * (fica verde) o motivo da amizade.
 *
 * So' cuida do visual; o que cada botao faz fica com quem abriu (Ouvinte).
 */
public class PainelJogadorUI {

    public interface Ouvinte {
        void alternarAmigo(String nome);
        void alternarIgnorar(String nome);
        void convidarParty(String nome);
        void convidarTrade(String nome);
        void abrirChat(String nome);
        /** icone: "pk", "guild" ou "seller" (servidor.py::ICONES_AMIZADE_VALIDOS). */
        void alternarIconeAmigo(String nome, String icone);
    }

    // Foto na esquerda, botoes (icone + o que faz) empilhados na direita.
    private static final float LARGURA = 330f;
    private static final float TAM_FOTO = 100f;
    private static final float ALTURA_STATUS = 42f;
    private static final float LARGURA_BOTAO = 170f;
    private static final float ALTURA_BOTAO = 38f;
    private static final float TAM_ICONE_BOTAO = 26f;
    private static final float TAM_MINI = 44f;
    /** No PC a janela fica menor (no celular continua do tamanho de antes). */
    private static final float ESCALA_PC = 0.8f;
    private static final float ESCALA_CELULAR = 1.15f;

    // Cores dos botoes: as mesmas das notificacoes (HudVitais.COR_NOTIF_*)
    // - amigo rosa, trade amarelo, party azul - e o resto no mesmo tom.
    private static final Color COR_CINZA = Color.valueOf("3a3a3a");
    private static final Color COR_ROSA = new Color(0.45f, 0.12f, 0.3f, 1f);
    private static final Color COR_VERDE = Color.valueOf("1e6b2a");
    private static final Color COR_VERMELHO = Color.valueOf("7a1a1a");
    private static final Color COR_AMARELO = new Color(0.42f, 0.33f, 0.05f, 1f);
    private static final Color COR_PARTY = new Color(0.16f, 0.32f, 0.45f, 1f);
    private static final Color COR_MENSAGEM = Color.valueOf("2a5a52");
    private static final Color COR_TEXTO_ESCURO = Color.WHITE;
    private static final Color COR_TOPBAR = Color.valueOf("2f2f2f");

    private static final Color COR_FUNDO = new Color(0.10f, 0.10f, 0.10f, 0.97f);
    private static final Color COR_BORDA = new Color(0.36f, 0.36f, 0.36f, 1f);
    private static final Color COR_SLOT = new Color(0.20f, 0.20f, 0.20f, 1f);
    private static final Color COR_SLOT_BORDA = new Color(0.09f, 0.09f, 0.09f, 1f);
    private static final Color COR_ATIVO = new Color(0.10f, 0.55f, 0.12f, 1f);
    private static final Color COR_ATIVO_BORDA = new Color(0.05f, 0.30f, 0.06f, 1f);

    private final Skin skin;
    private final TextureAtlas atlas;
    private final Ouvinte ouvinte;
    // Janela arrastavel pela topbar: o deslocamento entra depois do layout
    // (senao qualquer relayout voltava ela pro meio).
    private float deslocX = 0f, deslocY = 0f;
    private Table conteudoArrastavel;
    private final Table raiz = new Table() {
        @Override public void layout() {
            super.layout();
            if (conteudoArrastavel != null) conteudoArrastavel.moveBy(deslocX, deslocY);
        }
    };
    private final Table statusFoto = new Table();
    private final Image iconeStatus = new Image();
    private Label textoStatus, textoVisto;
    private final Table janela = new Table();
    private final Table barraIcones = new Table();
    private final Label nomeLabel;
    private final Table areaFoto = new Table();
    private final TextButton botaoIconBtn;
    private final TextButton botaoAmigo, botaoIgnorar, botaoParty, botaoTrade, botaoChat;
    private final TextButton.TextButtonStyle estiloAddFriend, estiloAdded, estiloBlock, estiloBlocked;
    private final Table botoes = new Table();
    private boolean mostrarParty = true, mostrarTrade = true;
    private final Button miniPk, miniGuild, miniSeller;
    private final Button.ButtonStyle estiloSlot, estiloSlotAtivo;

    private String nomeAtual = null;

    public PainelJogadorUI(Stage stage, Skin skin, TextureAtlas atlas, Ouvinte ouvinte) {
        this.skin = skin;
        this.atlas = atlas;
        this.ouvinte = ouvinte;
        estiloSlot = estilo(COR_SLOT, COR_SLOT_BORDA);
        estiloSlotAtivo = estilo(COR_ATIVO, COR_ATIVO_BORDA);

        estiloAddFriend = estiloTexto(COR_ROSA, COR_TEXTO_ESCURO);
        estiloAdded = estiloTexto(COR_VERDE, Color.WHITE);
        estiloBlock = estiloTexto(COR_CINZA, Color.WHITE);
        estiloBlocked = estiloTexto(COR_VERMELHO, Color.WHITE);

        // ---- Topbar so' com o nome e o X ----
        botaoIconBtn = botaoTexto("ui/buttons/IconBtn", "", estiloTexto(COR_CINZA, Color.WHITE), 22f);
        botaoIconBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                barraIcones.setVisible(!barraIcones.isVisible());
            }
        });
        // Nome sem contorno e um pouco menor que o titulo.
        nomeLabel = new Label("", new Label.LabelStyle(UiSkin.fonteHudEm(skin, 1.35f), Color.WHITE));
        nomeLabel.setAlignment(Align.center);
        Button fechar = botaoIcone("ui/buttons/CloseBtn", null, 30f);
        fechar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { fechar(); }
        });
        Table topo = new Table();
        topo.setBackground(UiSkin.retanguloBordaUnica(COR_TOPBAR, COR_BORDA, false));
        topo.pad(4f, 10f, 4f, 6f);
        topo.add(botaoIconBtn).size(32f).left(); // icones de amizade (so' amigo)
        topo.add(nomeLabel).expandX().center();
        topo.add(fechar).size(32f).right();

        // ---- Botoes de baixo (ordem do mock, esquerda -> direita) ----
        botaoAmigo = botaoTexto("ui/buttons/FriendsBtn", "Add Friend", estiloAddFriend, TAM_ICONE_BOTAO);
        botaoIgnorar = botaoTexto("ui/buttons/IgnoreBtn", "Block", estiloBlock, TAM_ICONE_BOTAO);
        botaoParty = botaoTexto("ui/buttons/PartyBtn", "Party", estiloTexto(COR_PARTY, Color.WHITE), TAM_ICONE_BOTAO);
        botaoTrade = botaoTexto("ui/currency/Gold", "Trade", estiloTexto(COR_AMARELO, COR_TEXTO_ESCURO), TAM_ICONE_BOTAO);
        botaoChat = botaoTexto("ui/ChatButton", "Message", estiloTexto(COR_MENSAGEM, Color.WHITE), TAM_ICONE_BOTAO);
        acao(botaoAmigo, () -> ouvinte.alternarAmigo(nomeAtual));
        acao(botaoIgnorar, () -> ouvinte.alternarIgnorar(nomeAtual));
        acao(botaoParty, () -> ouvinte.convidarParty(nomeAtual));
        acao(botaoTrade, () -> ouvinte.convidarTrade(nomeAtual));
        acao(botaoChat, () -> ouvinte.abrirChat(nomeAtual));
        montarBotoes();

        janela.setBackground(UiSkin.retangulo(COR_FUNDO, COR_BORDA, 1));
        janela.setTouchable(Touchable.enabled);
        janela.pad(1f);
        janela.add(topo).growX().row();
        // Esquerda: botao dos icones de amizade (so' amigo) em cima da foto.
        Table esquerda = new Table();
        esquerda.top();
        esquerda.add(areaFoto).width(TAM_FOTO + 20f).height(TAM_FOTO + ALTURA_STATUS + 14f);
        Table corpo = new Table();
        corpo.pad(8f, 10f, 6f, 10f);
        corpo.add(esquerda).expandX().top();
        corpo.add(botoes).top();
        janela.add(corpo).grow();

        // ---- Barrinha dos motivos da amizade (em cima, a esquerda) ----
        miniPk = botaoIcone("ui/PkIcon", estiloSlot, 30f);
        miniGuild = botaoIcone("ui/GuildIcon", estiloSlot, 30f);
        miniSeller = botaoIcone("ui/SellerIcon", estiloSlot, 30f);
        acao(miniPk, () -> ouvinte.alternarIconeAmigo(nomeAtual, "pk"));
        acao(miniGuild, () -> ouvinte.alternarIconeAmigo(nomeAtual, "guild"));
        acao(miniSeller, () -> ouvinte.alternarIconeAmigo(nomeAtual, "seller"));
        barraIcones.setBackground(UiSkin.retangulo(COR_FUNDO, COR_BORDA, 1));
        barraIcones.setTouchable(Touchable.enabled);
        barraIcones.pad(3f);
        barraIcones.add(miniPk).size(TAM_MINI).padRight(3f);
        barraIcones.add(miniGuild).size(TAM_MINI).padRight(3f);
        barraIcones.add(miniSeller).size(TAM_MINI);
        barraIcones.setVisible(false);

        Table conteudo = new Table() {
            @Override public void layout() {
                super.layout();
                setOrigin(getWidth() / 2f, getHeight() / 2f);
            }
        };
        conteudoArrastavel = conteudo;
        conteudo.add(barraIcones).left().padLeft(4f).row();
        conteudo.add(janela).width(LARGURA);
        boolean mobile = com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
            || com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
        // PC menor; celular um pouco maior (dedo).
        conteudo.setTransform(true);
        conteudo.setScale(mobile ? ESCALA_CELULAR : ESCALA_PC);

        // Clique em cima da janela nao pode "vazar" pro mundo atras dela
        // (senao mirava/andava no SQM embaixo).
        InputListener engolir = new InputListener() {
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) { return true; }
        };
        janela.addListener(engolir);
        // Arrastar pela topbar move a janela (da' pra mexer no resto do jogo).
        topo.setTouchable(Touchable.enabled);
        topo.addListener(new InputListener() {
            float ultimoX, ultimoY;
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                ultimoX = event.getStageX();
                ultimoY = event.getStageY();
                return true;
            }
            @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                float dx = event.getStageX() - ultimoX, dy = event.getStageY() - ultimoY;
                ultimoX = event.getStageX();
                ultimoY = event.getStageY();
                Stage st = raiz.getStage();
                float limX = st != null ? st.getWidth() * 0.5f : 2000f, limY = st != null ? st.getHeight() * 0.5f : 2000f;
                float nx = Math.max(-limX, Math.min(limX, deslocX + dx)), ny = Math.max(-limY, Math.min(limY, deslocY + dy));
                conteudoArrastavel.moveBy(nx - deslocX, ny - deslocY);
                deslocX = nx;
                deslocY = ny;
            }
        });

        // Status no canto de cima da foto: online/offline e "Last seen".
        Label.LabelStyle estiloStatus = new Label.LabelStyle(UiSkin.fonteHudEm(skin, 0.95f), Color.WHITE);
        textoStatus = new Label("", estiloStatus);
        textoVisto = new Label("", new Label.LabelStyle(UiSkin.fonteHudEm(skin, 0.75f), Color.WHITE));
        textoVisto.setColor(new Color(0.65f, 0.65f, 0.65f, 1f));
        iconeStatus.setScaling(Scaling.fit);
        Table linhaStatus = new Table();
        linhaStatus.add(iconeStatus).size(20f).padRight(5f);
        linhaStatus.add(textoStatus).left();
        statusFoto.top().left();
        statusFoto.add(linhaStatus).left().row();
        statusFoto.add(textoVisto).left();
        statusFoto.setVisible(false);
        barraIcones.addListener(engolir);

        raiz.setFillParent(true);
        raiz.center();
        raiz.add(conteudo);
        raiz.setVisible(false);
        stage.addActor(raiz);
    }

    /** foto: ator que desenha o player (o mesmo sprite animado do jogo). */
    public void abrir(String nomeReal, String nomeExibido, Actor foto) {
        abrir(nomeReal, nomeExibido, null, foto);
    }

    /** classe: pinta o nome na cor dela (null = branco). */
    public void abrir(String nomeReal, String nomeExibido, String classe, Actor foto) {
        if (!raiz.isVisible()) {
            // Reabrindo: sempre no centro, e por baixo das outras janelas.
            deslocX = 0f;
            deslocY = 0f;
            raiz.invalidate();
            raiz.toBack();
        }
        nomeAtual = nomeReal;
        // A Martel (fonte do titulo) nao tem "?" - o nome escondido ("???",
        // ver WorldScreen.nomeVisivel) saia em branco. Nesse caso usa a
        // DejaVu (mesmo tamanho), que tem o simbolo.
        Label.LabelStyle estiloNome = nomeExibido.indexOf('?') >= 0
            ? new Label.LabelStyle(skin.getFont("simbolo-font"), Color.WHITE)
            : skin.get("subtitulo", Label.LabelStyle.class);
        estiloNome = new Label.LabelStyle(UiSkin.fonteHudEm(skin, 1.35f), Color.WHITE); // tem "?" e sem contorno
        nomeLabel.setStyle(estiloNome);
        nomeLabel.setText(nomeExibido);
        // Nome escondido ("???"): sem cor, senao entregava a classe.
        boolean escondido = nomeExibido.indexOf('?') >= 0;
        nomeLabel.setColor(classe != null && !escondido ? com.teste.game.ChatUI.corDaClasse(classe) : Color.WHITE);
        areaFoto.clearChildren();
        statusFoto.setVisible(false);
        // Status em cima (Online/Last seen) e a foto embaixo, sem sobrepor.
        areaFoto.top();
        areaFoto.add(statusFoto).left().height(ALTURA_STATUS).top().row();
        if (foto != null) areaFoto.add(foto).size(TAM_FOTO).padTop(14f);
        else areaFoto.add().size(TAM_FOTO);
        barraIcones.setVisible(false);
        raiz.setVisible(true);
    }

    /** player_info do servidor: online, ou ha' quantos segundos foi visto (-1 = nunca). */
    public void definirStatus(String nome, boolean online, long vistoHaSeg) {
        if (nome == null || !nome.equals(nomeAtual)) return;
        TextureRegion r = atlas.findRegion(online ? "ui/OnlineIcon" : "ui/OfflineIcon");
        iconeStatus.setDrawable(r != null ? new TextureRegionDrawable(r) : null);
        textoStatus.setText(online ? "Online" : "Offline");
        textoStatus.setColor(online ? Color.valueOf("5ee36a") : new Color(0.75f, 0.75f, 0.75f, 1f));
        textoVisto.setText(online || vistoHaSeg < 0 ? "" : "Last seen " + tempoAtras(vistoHaSeg));
        statusFoto.setVisible(true);
    }

    private static String tempoAtras(long seg) {
        if (seg < 60) return "just now";
        if (seg < 3600) return (seg / 60) + "m ago";
        if (seg < 86400) return (seg / 3600) + "h ago";
        return (seg / 86400) + "d ago";
    }

    /** Estado dos botoes (amigo/ignorado ficam verdes; IconBtn so' pra amigo). */
    public void definirEstado(boolean amigo, boolean ignorado, boolean pk, boolean guild, boolean seller) {
        botaoAmigo.setStyle(amigo ? estiloAdded : estiloAddFriend);
        botaoAmigo.setText(amigo ? "Added" : "Add Friend");
        botaoIgnorar.setStyle(ignorado ? estiloBlocked : estiloBlock);
        botaoIgnorar.setText(ignorado ? "Blocked" : "Block");
        botaoIconBtn.setVisible(amigo);
        if (!amigo) barraIcones.setVisible(false);
        miniPk.setStyle(pk ? estiloSlotAtivo : estiloSlot);
        miniGuild.setStyle(guild ? estiloSlotAtivo : estiloSlot);
        miniSeller.setStyle(seller ? estiloSlotAtivo : estiloSlot);
    }

    private void montarBotoes() {
        botoes.clearChildren();
        for (Button b : new Button[]{botaoAmigo, botaoIgnorar, botaoParty, botaoTrade, botaoChat}) {
            if (b == botaoParty && !mostrarParty) continue;
            if (b == botaoTrade && !mostrarTrade) continue;
            botoes.add(b).width(LARGURA_BOTAO).height(ALTURA_BOTAO).padBottom(4f).row();
        }
    }

    /** Party: so' sem party ou sendo o lider. Trade: so' perto (5 SQMs).
     * Chamado todo frame com a janela aberta; so' remonta quando muda. */
    public void definirBotoesVisiveis(boolean party, boolean trade) {
        if (party == mostrarParty && trade == mostrarTrade) return;
        mostrarParty = party;
        mostrarTrade = trade;
        montarBotoes();
    }

    public void fechar() {
        raiz.setVisible(false);
        barraIcones.setVisible(false);
        nomeAtual = null;
    }

    public boolean isVisivel() { return raiz.isVisible(); }

    /** Nome (real) do player que esta aberto, ou null. */
    public String nomeAberto() { return raiz.isVisible() ? nomeAtual : null; }

    // ------------------------------------------------------------------

    private Button.ButtonStyle estilo(Color fundo, Color borda) {
        Button.ButtonStyle e = new Button.ButtonStyle();
        Drawable normal = UiSkin.retangulo(fundo, borda, 2);
        e.up = normal;
        e.over = UiSkin.retangulo(fundo.cpy().lerp(Color.WHITE, 0.1f), borda, 2);
        e.down = UiSkin.retangulo(fundo.cpy().mul(0.75f, 0.75f, 0.75f, 1f), borda, 2);
        return e;
    }

    /** Botao chapado (sem contorno no texto), cor de fundo e do texto. */
    private TextButton.TextButtonStyle estiloTexto(Color fundo, Color texto) {
        TextButton.TextButtonStyle e = new TextButton.TextButtonStyle();
        e.font = skin.get("chat-log", Label.LabelStyle.class).font;
        e.fontColor = texto;
        Color borda = fundo.cpy().lerp(Color.BLACK, 0.35f);
        e.up = UiSkin.retangulo(fundo, borda, 1);
        e.over = UiSkin.retangulo(fundo.cpy().lerp(Color.WHITE, 0.12f), borda, 1);
        e.down = UiSkin.retangulo(fundo.cpy().lerp(Color.BLACK, 0.15f), borda, 1);
        return e;
    }

    /** Icone na esquerda + o que o botao faz do lado. */
    private TextButton botaoTexto(String regiao, String texto, TextButton.TextButtonStyle estilo, float tamIcone) {
        TextButton b = new TextButton(texto, estilo);
        b.clearChildren();
        TextureRegion r = atlas.findRegion(regiao);
        if (r != null) {
            Image img = new Image(new TextureRegionDrawable(r));
            img.setScaling(Scaling.fit);
            if (texto.isEmpty()) b.add(img).size(tamIcone);
            else b.add(img).size(tamIcone).padLeft(8f).padRight(8f);
        }
        if (!texto.isEmpty()) {
            b.add(b.getLabel()).left().expandX();
            b.getLabel().setAlignment(Align.left);
        }
        return b;
    }

    /** Botao com um icone do atlas no meio. estilo null = so' o icone (sem fundo). */
    private Button botaoIcone(String regiao, Button.ButtonStyle estilo, float tamIcone) {
        Button b = new Button(estilo != null ? estilo : new Button.ButtonStyle());
        TextureRegion r = atlas.findRegion(regiao);
        if (r != null) {
            Image img = new Image(new TextureRegionDrawable(r));
            img.setScaling(Scaling.fit);
            b.add(img).size(tamIcone);
        }
        return b;
    }

    private static void acao(Button b, Runnable r) {
        b.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { r.run(); }
        });
    }
}
