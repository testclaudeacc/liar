package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;

/**
 * Barra de atalhos da gameplay: 8 slots livres (teclas 1-8). O que vai em
 * cada slot e' escolhido na aba Spells do livro (BookMenuUI) e fica salvo no
 * servidor (servidor.py::set_hotbar). Clicar/tocar num slot ou apertar a
 * tecla usa o atalho (servidor.py::use_hotbar).
 *
 * PC: uma linha no centro de baixo. Celular: botoes redondos, 1-4 num 2x2
 * no canto de baixo a direita (a esquerda ja' tem o joystick) e 5-8 num 2x2
 * na direita de cima, abaixo dos botoes do topo.
 */
public final class HotbarUI {

    public interface AoUsar { void usar(int indice); }

    private static final boolean MOBILE = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
        || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
    private static final float TAM_SLOT = MOBILE ? 64f : 54f;
    private static final float ESPACO = MOBILE ? 6f : 5f;
    /** Tom do "apertado" (clique ou tecla): escurece e volta. */
    private static final Color COR_APERTADO = new Color(0.55f, 0.55f, 0.55f, 1f);

    private final Skin skin;
    private final BookMenuUI livro;
    private final AoUsar aoUsar;
    private final Table raiz = new Table();
    private final Button[] slots = new Button[BookMenuUI.SLOTS_ATALHO];
    private final Stack[] conteudos = new Stack[BookMenuUI.SLOTS_ATALHO];

    public HotbarUI(Stage stage, Skin skin, BookMenuUI livro, AoUsar aoUsar) {
        this.skin = skin;
        this.livro = livro;
        this.aoUsar = aoUsar;
        // Mesmo cinza escuro dos slots da bag (e das outras janelas). No
        // celular os botoes sao redondos.
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        if (MOBILE) {
            estilo.up = circulo(new Color(0.15f, 0.15f, 0.15f, 0.92f), new Color(0.32f, 0.32f, 0.32f, 1f));
        } else {
            estilo.up = skin.getDrawable("bag-slot");
            estilo.over = skin.getDrawable("bag-slot-hover");
        }
        for (int i = 0; i < slots.length; i++) {
            final int indice = i;
            Button slot = new Button(estilo);
            conteudos[i] = new Stack();
            slot.add(conteudos[i]).grow();
            slot.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) { usar(indice); }
            });
            slots[i] = slot;
        }
        raiz.setFillParent(true);
        if (MOBILE) {
            // Celular: 1-4 num 2x2 no canto de baixo a direita; 5-8 num 2x2 na
            // direita de cima, logo abaixo dos botoes do topo (chat/menu/config).
            Table cima = grade2x2(4), baixo = grade2x2(0);
            raiz.top().right();
            raiz.add(cima).right().padTop(TOPO_MOBILE).padRight(18).row();
            raiz.add().expandY().row();
            raiz.add(baixo).right().padBottom(18).padRight(18);
        } else {
            raiz.bottom().pad(0, 0, 10, 0);
            for (Button slot : slots) raiz.add(slot).size(TAM_SLOT).pad(ESPACO / 2f);
        }
        // Por baixo das outras telas (livro, chat, settings), igual a HUD.
        stage.getRoot().addActorAt(0, raiz);
        atualizar();
    }

    /** Altura ocupada no topo pelos botoes de chat/menu/config (108 no
     * celular) e a linha de fps embaixo deles. */
    private static final float TOPO_MOBILE = 150f;

    private Table grade2x2(int primeiro) {
        Table t = new Table();
        for (int i = 0; i < 4; i++) {
            t.add(slots[primeiro + i]).size(TAM_SLOT).pad(ESPACO / 2f);
            if (i == 1) t.row();
        }
        return t;
    }

    /** Botao redondo (celular): circulo com borda, gerado em alta resolucao
     * e suavizado (Linear) pra nao ficar serrilhado quando reduz. */
    private static com.badlogic.gdx.scenes.scene2d.utils.Drawable circulo(Color fundo, Color borda) {
        int tam = 128, raio = tam / 2;
        com.badlogic.gdx.graphics.Pixmap pm = new com.badlogic.gdx.graphics.Pixmap(tam, tam, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        pm.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        pm.setColor(borda);
        pm.fillCircle(raio, raio, raio - 1);
        pm.setColor(fundo);
        pm.fillCircle(raio, raio, raio - 6);
        com.badlogic.gdx.graphics.Texture tex = new com.badlogic.gdx.graphics.Texture(pm, true);
        tex.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.MipMapLinearLinear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        pm.dispose();
        return new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(new TextureRegion(tex));
    }

    /** Atualiza o conteudo dos slots (mudou a barra ou a bag). Os botoes
     * continuam os mesmos, entao o efeito de "apertado" nao e' cortado. */
    public void atualizar() {
        String[] atalhos = livro.atalhos();
        for (int i = 0; i < slots.length; i++) {
            Stack pilha = conteudos[i];
            pilha.clearChildren();
            String caminho = i < atalhos.length ? atalhos[i] : "";
            if (caminho != null && !caminho.isEmpty()) {
                int qtd = livro.quantidadeNaBag(caminho);
                TextureRegion icone = livro.iconeDoItem(caminho);
                Table centro = new Table();
                if (icone != null) {
                    Image img = new Image(icone);
                    img.setScaling(Scaling.fit);
                    if (qtd <= 0) img.setColor(1f, 1f, 1f, 0.3f); // acabou: so' transparente, sem numero
                    centro.add(img).size(TAM_SLOT * (MOBILE ? 0.58f : 0.68f));
                }
                pilha.add(centro);
                if (qtd > 0) {
                    Label numero = new Label(String.valueOf(qtd), skin, "hud");
                    numero.setFontScale(MOBILE ? 0.75f : 0.65f);
                    Table canto = new Table();
                    // Redondo: o numero vai embaixo no meio (o canto e' vazio).
                    if (MOBILE) { canto.bottom(); canto.add(numero).padBottom(4); }
                    else { canto.bottom().right(); canto.add(numero).pad(0, 0, 2, 4); }
                    pilha.add(canto);
                }
            }
            if (!MOBILE) {
                // Tecla do slot no canto de cima a esquerda.
                Label tecla = new Label(String.valueOf(i + 1), skin, "hud");
                tecla.setFontScale(0.55f);
                tecla.setColor(1f, 1f, 1f, 0.5f);
                Table cantoTecla = new Table();
                cantoTecla.top().left();
                cantoTecla.add(tecla).pad(2, 4, 0, 0);
                pilha.add(cantoTecla);
            }
        }
    }

    /** Usa o slot (clique ou tecla): escurece rapidinho e manda pro servidor. */
    private void usar(int indice) {
        Button slot = slots[indice];
        slot.clearActions();
        slot.setColor(COR_APERTADO);
        slot.addAction(Actions.color(Color.WHITE, 0.18f));
        escurecerIcones(conteudos[indice]);
        String caminho = livro.atalhos()[indice];
        if (caminho != null && !caminho.isEmpty()) aoUsar.usar(indice);
    }

    /** O tom do botao nao passa pros filhos no scene2d: escurece o icone junto. */
    private void escurecerIcones(com.badlogic.gdx.scenes.scene2d.Group grupo) {
        for (com.badlogic.gdx.scenes.scene2d.Actor filho : grupo.getChildren()) {
            if (filho instanceof Image) {
                Color normal = new Color(1f, 1f, 1f, filho.getColor().a);
                filho.clearActions();
                filho.setColor(COR_APERTADO.r, COR_APERTADO.g, COR_APERTADO.b, normal.a);
                filho.addAction(Actions.color(normal, 0.18f));
            } else if (filho instanceof com.badlogic.gdx.scenes.scene2d.Group) {
                escurecerIcones((com.badlogic.gdx.scenes.scene2d.Group) filho);
            }
        }
    }

    /** Tecla 1-8 (PC). */
    public boolean usarTecla(int numero) {
        int i = numero - 1;
        if (i < 0 || i >= slots.length) return false;
        usar(i);
        return true;
    }

    public void setVisivel(boolean visivel) { raiz.setVisible(visivel); }
}
