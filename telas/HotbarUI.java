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
 * PC: uma linha no centro de baixo. Celular: botoes redondos no lado
 * direito (a esquerda ja' tem o joystick): 1-4 num arco em volta do canto
 * de baixo e 5-8 numa coluna na borda, acima do arco.
 */
public final class HotbarUI {

    public interface AoUsar { void usar(int indice); }

    private static final boolean MOBILE = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
        || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
    private static final float TAM_SLOT = MOBILE ? 50f : 54f;
    private static final float ESPACO = MOBILE ? 6f : 5f;
    /** Tom do "apertado" (clique ou tecla): escurece e volta. */
    private static final Color COR_APERTADO = new Color(0.55f, 0.55f, 0.55f, 1f);

    private final Skin skin;
    private final BookMenuUI livro;
    private final AoUsar aoUsar;
    private final Table raiz = new Table();
    /** Celular: os botoes ficam soltos (posicao calculada), nao numa tabela. */
    private final com.badlogic.gdx.scenes.scene2d.Group grupoMobile = new com.badlogic.gdx.scenes.scene2d.Group() {
        @Override public void act(float delta) {
            super.act(delta);
            posicionarMobile();
        }
    };
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
        if (MOBILE) {
            for (Button slot : slots) grupoMobile.addActor(slot);
            grupoMobile.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly);
            // Por baixo das outras telas (livro, chat, settings), igual a HUD.
            stage.getRoot().addActorAt(0, grupoMobile);
        } else {
            raiz.setFillParent(true);
            raiz.bottom().pad(0, 0, 10, 0);
            for (Button slot : slots) raiz.add(slot).size(TAM_SLOT).pad(ESPACO / 2f);
            stage.getRoot().addActorAt(0, raiz);
        }
        atualizar();
    }

    // Posicoes no celular, tiradas do desenho de referencia (tela 960x540):
    // centro de cada botao = (distancia da borda DIREITA, altura a partir de
    // BAIXO). 1-4 descem em diagonal a partir da borda direita ate' o chao;
    // 5-8 sobem numa coluna na borda, acima do 1 (abaixo dos botoes do topo).
    private static final float[][] POSICOES_MOBILE = {
        {48f, 140f}, {140f, 130f}, {208f, 85f}, {252f, 28f},
        {48f, 197f}, {48f, 254f}, {48f, 311f}, {48f, 368f},
    };

    private void posicionarMobile() {
        com.badlogic.gdx.scenes.scene2d.Stage stage = grupoMobile.getStage();
        if (stage == null) return;
        float w = stage.getWidth();
        for (int i = 0; i < slots.length && i < POSICOES_MOBILE.length; i++) {
            centrar(slots[i], w - POSICOES_MOBILE[i][0], POSICOES_MOBILE[i][1]);
        }
    }

    private static void centrar(Button b, float x, float y) {
        b.setBounds(x - TAM_SLOT / 2f, y - TAM_SLOT / 2f, TAM_SLOT, TAM_SLOT);
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

    public void setVisivel(boolean visivel) {
        raiz.setVisible(visivel);
        grupoMobile.setVisible(visivel);
    }
}
