package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;

/**
 * Barra de atalhos da gameplay: 4 slots de magia + 4 de item (comida/pocao).
 * O que vai em cada slot e' escolhido na aba Spells do livro (BookMenuUI) e
 * fica salvo no servidor (servidor.py::set_hotbar). Clicar/tocar num slot (ou
 * as teclas 1-4 magias, 5-8 itens) usa o atalho (servidor.py::use_hotbar).
 *
 * PC: uma linha no centro de baixo. Celular: duas linhas no canto de baixo a
 * direita (a esquerda ja' tem o joystick).
 */
public final class HotbarUI {

    public interface AoUsar { void usar(String tipo, int indice); }

    private static final boolean MOBILE = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
        || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
    private static final float TAM_SLOT = MOBILE ? 58f : 42f;
    private static final float ESPACO = MOBILE ? 6f : 4f;
    private static final Color BORDA_MAGIA = new Color(0.45f, 0.3f, 0.65f, 1f);
    private static final Color BORDA_ITEM = new Color(0.55f, 0.38f, 0.2f, 1f);

    private final Skin skin;
    private final BookMenuUI livro;
    private final AoUsar aoUsar;
    private final Table raiz = new Table();
    private final com.badlogic.gdx.scenes.scene2d.utils.Drawable fundoMagia, fundoItem;

    public HotbarUI(Stage stage, Skin skin, BookMenuUI livro, AoUsar aoUsar) {
        this.skin = skin;
        this.livro = livro;
        this.aoUsar = aoUsar;
        fundoMagia = UiSkin.retangulo(new Color(0.1f, 0.08f, 0.13f, 0.88f), BORDA_MAGIA, 2);
        fundoItem = UiSkin.retangulo(new Color(0.12f, 0.1f, 0.08f, 0.88f), BORDA_ITEM, 2);
        raiz.setFillParent(true);
        if (MOBILE) raiz.bottom().right().pad(0, 0, 18, 18);
        else raiz.bottom().pad(0, 0, 10, 0);
        // Por baixo das outras telas (livro, chat, settings), igual a HUD.
        stage.getRoot().addActorAt(0, raiz);
        atualizar();
    }

    /** Redesenha os slots (mudou a hotbar ou a bag). */
    public void atualizar() {
        raiz.clearChildren();
        String[] magias = livro.hotbarMagias(), itens = livro.hotbarItens();
        Table linhaMagias = new Table(), linhaItens = new Table();
        for (int i = 0; i < magias.length; i++) linhaMagias.add(criarSlot("spells", i, magias[i], i + 1)).size(TAM_SLOT).pad(ESPACO / 2f);
        for (int i = 0; i < itens.length; i++) linhaItens.add(criarSlot("items", i, itens[i], magias.length + i + 1)).size(TAM_SLOT).pad(ESPACO / 2f);
        if (MOBILE) {
            raiz.add(linhaMagias).right().row();
            raiz.add(linhaItens).right();
        } else {
            raiz.add(linhaMagias).padRight(12);
            raiz.add(linhaItens);
        }
    }

    private Button criarSlot(String tipo, int indice, String caminho, int tecla) {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = "spells".equals(tipo) ? fundoMagia : fundoItem;
        Button slot = new Button(estilo);
        Stack pilha = new Stack();
        boolean vazio = caminho == null || caminho.isEmpty();
        if (!vazio) {
            TextureRegion icone = livro.iconeDoItem(caminho);
            Table centro = new Table();
            if (icone != null) {
                Image img = new Image(icone);
                img.setScaling(Scaling.fit);
                int qtd = livro.quantidadeNaBag(caminho);
                if (qtd <= 0) img.setColor(1f, 1f, 1f, 0.3f); // acabou: fica apagado
                centro.add(img).size(TAM_SLOT * 0.7f);
            }
            pilha.add(centro);
            Label qtd = new Label(String.valueOf(livro.quantidadeNaBag(caminho)), skin, "hud");
            qtd.setFontScale(MOBILE ? 0.75f : 0.6f);
            Table canto = new Table();
            canto.bottom().right();
            canto.add(qtd).pad(0, 0, 1, 4);
            pilha.add(canto);
        }
        if (!MOBILE) {
            // Tecla do atalho no canto de cima a esquerda.
            Label numero = new Label(String.valueOf(tecla), skin, "hud");
            numero.setFontScale(0.5f);
            numero.setColor(1f, 1f, 1f, 0.55f);
            Table cantoTecla = new Table();
            cantoTecla.top().left();
            cantoTecla.add(numero).pad(1, 4, 0, 0);
            pilha.add(cantoTecla);
        }
        slot.add(pilha).grow();
        if (!vazio) {
            slot.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) { aoUsar.usar(tipo, indice); }
            });
        }
        return slot;
    }

    /** Tecla 1-8 (PC): 1-4 magias, 5-8 itens. */
    public boolean usarTecla(int numero) {
        String[] magias = livro.hotbarMagias(), itens = livro.hotbarItens();
        int i = numero - 1;
        if (i >= 0 && i < magias.length) {
            if (magias[i].isEmpty()) return false;
            aoUsar.usar("spells", i);
            return true;
        }
        i -= magias.length;
        if (i >= 0 && i < itens.length) {
            if (itens[i].isEmpty()) return false;
            aoUsar.usar("items", i);
            return true;
        }
        return false;
    }

    public void setVisivel(boolean visivel) { raiz.setVisible(visivel); }
}
