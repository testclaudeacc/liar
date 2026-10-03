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

    // Medidas do mock (em px da imagem) * ~1.4, mesma escala do BookMenu.
    private static final float LARGURA = 430f;
    private static final float ALTURA = 262f;
    private static final float TAM_BOTAO = 76f;
    private static final float TAM_ICONE_BOTAO = 46f;
    private static final float TAM_FOTO = 96f;
    private static final float TAM_MINI = 44f;

    private static final Color COR_FUNDO = new Color(0.10f, 0.10f, 0.10f, 0.97f);
    private static final Color COR_BORDA = new Color(0.36f, 0.36f, 0.36f, 1f);
    private static final Color COR_SLOT = new Color(0.20f, 0.20f, 0.20f, 1f);
    private static final Color COR_SLOT_BORDA = new Color(0.09f, 0.09f, 0.09f, 1f);
    private static final Color COR_ATIVO = new Color(0.10f, 0.55f, 0.12f, 1f);
    private static final Color COR_ATIVO_BORDA = new Color(0.05f, 0.30f, 0.06f, 1f);

    private final Skin skin;
    private final TextureAtlas atlas;
    private final Ouvinte ouvinte;
    private final Table raiz = new Table();
    private final Table janela = new Table();
    private final Table barraIcones = new Table();
    private final Label nomeLabel;
    private final Table areaFoto = new Table();
    private final Button botaoIconBtn;
    private final Button botaoAmigo, botaoIgnorar;
    private final Button miniPk, miniGuild, miniSeller;
    private final Button.ButtonStyle estiloSlot, estiloSlotAtivo;

    private String nomeAtual = null;

    public PainelJogadorUI(Stage stage, Skin skin, TextureAtlas atlas, Ouvinte ouvinte) {
        this.skin = skin;
        this.atlas = atlas;
        this.ouvinte = ouvinte;
        estiloSlot = estilo(COR_SLOT, COR_SLOT_BORDA);
        estiloSlotAtivo = estilo(COR_ATIVO, COR_ATIVO_BORDA);

        // ---- Topo: IconBtn | nome | X ----
        botaoIconBtn = botaoIcone("ui/buttons/IconBtn", null, 30f);
        botaoIconBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                barraIcones.setVisible(!barraIcones.isVisible());
            }
        });
        nomeLabel = new Label("", skin, "subtitulo");
        nomeLabel.setAlignment(Align.center);
        Button fechar = botaoIcone("ui/buttons/CloseBtn", null, 30f);
        fechar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { fechar(); }
        });
        Table topo = new Table();
        topo.add(botaoIconBtn).size(36f).left();
        topo.add(nomeLabel).expandX().center();
        topo.add(fechar).size(36f).right();

        // ---- Botoes de baixo (ordem do mock, esquerda -> direita) ----
        botaoAmigo = botaoIcone("ui/buttons/FriendsBtn", estiloSlot, TAM_ICONE_BOTAO);
        botaoIgnorar = botaoIcone("ui/buttons/IgnoreBtn", estiloSlot, TAM_ICONE_BOTAO);
        Button botaoParty = botaoIcone("ui/buttons/PartyBtn", estiloSlot, TAM_ICONE_BOTAO);
        Button botaoTrade = botaoIcone("ui/currency/Gold", estiloSlot, TAM_ICONE_BOTAO);
        Button botaoChat = botaoIcone("ui/ChatButton", estiloSlot, TAM_ICONE_BOTAO);
        acao(botaoAmigo, () -> ouvinte.alternarAmigo(nomeAtual));
        acao(botaoIgnorar, () -> ouvinte.alternarIgnorar(nomeAtual));
        acao(botaoParty, () -> ouvinte.convidarParty(nomeAtual));
        acao(botaoTrade, () -> ouvinte.convidarTrade(nomeAtual));
        acao(botaoChat, () -> ouvinte.abrirChat(nomeAtual));
        Table botoes = new Table();
        for (Button b : new Button[]{botaoAmigo, botaoIgnorar, botaoParty, botaoTrade, botaoChat}) {
            botoes.add(b).size(TAM_BOTAO).pad(0, 2, 0, 2);
        }

        janela.setBackground(UiSkin.retangulo(COR_FUNDO, COR_BORDA, 1));
        janela.setTouchable(Touchable.enabled);
        janela.pad(8f, 10f, 8f, 10f);
        janela.add(topo).growX().row();
        janela.add(areaFoto).size(TAM_FOTO).expandY().row();
        janela.add(botoes).padTop(4f);

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

        Table conteudo = new Table();
        conteudo.add(barraIcones).left().padLeft(4f).row();
        conteudo.add(janela).size(LARGURA, ALTURA);

        // Clique em cima da janela nao pode "vazar" pro mundo atras dela
        // (senao mirava/andava no SQM embaixo).
        InputListener engolir = new InputListener() {
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) { return true; }
        };
        janela.addListener(engolir);
        barraIcones.addListener(engolir);

        raiz.setFillParent(true);
        raiz.center();
        raiz.add(conteudo);
        raiz.setVisible(false);
        stage.addActor(raiz);
    }

    /** foto: ator que desenha o player (o mesmo sprite animado do jogo). */
    public void abrir(String nomeReal, String nomeExibido, Actor foto) {
        nomeAtual = nomeReal;
        nomeLabel.setText(nomeExibido);
        areaFoto.clearChildren();
        if (foto != null) areaFoto.add(foto).grow();
        barraIcones.setVisible(false);
        raiz.setVisible(true);
    }

    /** Estado dos botoes (amigo/ignorado ficam verdes; IconBtn so' pra amigo). */
    public void definirEstado(boolean amigo, boolean ignorado, boolean pk, boolean guild, boolean seller) {
        botaoAmigo.setStyle(amigo ? estiloSlotAtivo : estiloSlot);
        botaoIgnorar.setStyle(ignorado ? estiloSlotAtivo : estiloSlot);
        botaoIconBtn.setVisible(amigo);
        if (!amigo) barraIcones.setVisible(false);
        miniPk.setStyle(pk ? estiloSlotAtivo : estiloSlot);
        miniGuild.setStyle(guild ? estiloSlotAtivo : estiloSlot);
        miniSeller.setStyle(seller ? estiloSlotAtivo : estiloSlot);
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
