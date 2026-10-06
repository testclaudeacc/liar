package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
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
 * Barra de atalhos da gameplay: 9 slots livres (BookMenuUI.SLOTS_ATALHO). O
 * que vai em cada slot e' escolhido na aba Spells do livro e fica salvo no
 * servidor (servidor.py::set_hotbar). Usar manda servidor.py::use_hotbar.
 *
 * PC: uma linha no centro de baixo, teclas 1-9.
 *
 * Celular (lado direito; a esquerda ja' tem o joystick):
 *  - 3 "dragkeys" grandes num arco em volta do canto de baixo. Cada um guarda
 *    2 atalhos: segurar e deslizar pra CIMA usa o 1o, pra ESQUERDA o 2o
 *    (dragkey 1 = slots 1-2, 2 = 3-4, 3 = 5-6). Soltar sem deslizar cancela.
 *  - 3 slots fixos (comida/pocao, slots 7-9) no canto, dentro do arco: tocar usa.
 */
public final class HotbarUI {

    public interface AoUsar { void usar(int indice); }

    private static final boolean MOBILE = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
        || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
    private static final float TAM_SLOT = 54f;     // PC
    private static final float ESPACO = 5f;        // PC
    /** Tom do "apertado" (clique ou tecla): escurece e volta. */
    private static final Color COR_APERTADO = new Color(0.55f, 0.55f, 0.55f, 1f);

    // ---- Celular ----
    /** Canto do arco: (distancia da borda direita, altura a partir de baixo). */
    private static final float CANTO_X = 22f, CANTO_Y = 30f;
    private static final float TAM_DRAGKEY = 88f;
    private static final float RAIO_DRAGKEYS = 246f;
    private static final float[] ANGULOS_DRAGKEYS = {100f, 136f, 172f};
    /** Opcoes que aparecem ao segurar um dragkey: tamanho e distancia dele. */
    private static final float TAM_OPCAO = 66f;
    private static final float DIST_OPCAO = 92f;
    /** Direcao de cada opcao do dragkey: 1a pra cima, 2a pra esquerda. */
    private static final float[] ANGULOS_OPCAO = {90f, 180f};
    /** Arrastou menos que isso: nenhuma opcao (soltar cancela). */
    private static final float ZONA_MORTA = 30f;
    /** Slots fixos (comida/pocao) num arco menor, dentro do dos dragkeys. */
    private static final float TAM_FIXO = 78f;
    private static final float RAIO_FIXOS = 136f;
    private static final float[] ANGULOS_FIXOS = {100f, 136f, 172f};
    private static final int PRIMEIRO_FIXO = 6; // slots 7-9

    private final Skin skin;
    private final BookMenuUI livro;
    private final AoUsar aoUsar;
    private final Table raiz = new Table();
    /** Celular: tudo solto (posicao calculada a cada frame), nao numa tabela. */
    private final com.badlogic.gdx.scenes.scene2d.Group grupoMobile = new com.badlogic.gdx.scenes.scene2d.Group() {
        @Override public void act(float delta) {
            super.act(delta);
            posicionarMobile();
        }
    };
    private final Button[] slots = new Button[BookMenuUI.SLOTS_ATALHO];
    private final Stack[] conteudos = new Stack[BookMenuUI.SLOTS_ATALHO];
    private final Dragkey[] dragkeys = new Dragkey[3];
    private Button.ButtonStyle estiloSlot, estiloSlotEscolhido;

    public HotbarUI(Stage stage, Skin skin, BookMenuUI livro, AoUsar aoUsar) {
        this.skin = skin;
        this.livro = livro;
        this.aoUsar = aoUsar;
        // Mesmo cinza escuro dos slots da bag (e das outras janelas). No
        // celular os botoes sao redondos.
        estiloSlot = new Button.ButtonStyle();
        if (MOBILE) {
            estiloSlot.up = circulo(new Color(0.15f, 0.15f, 0.15f, 0.92f), new Color(0.32f, 0.32f, 0.32f, 1f));
            estiloSlotEscolhido = new Button.ButtonStyle();
            estiloSlotEscolhido.up = circulo(new Color(0.22f, 0.22f, 0.22f, 0.96f), new Color(0.95f, 0.65f, 0.24f, 1f));
        } else {
            estiloSlot.up = skin.getDrawable("bag-slot");
            estiloSlot.over = skin.getDrawable("bag-slot-hover");
        }
        for (int i = 0; i < slots.length; i++) {
            final int indice = i;
            Button slot = new Button(estiloSlot);
            conteudos[i] = new Stack();
            slot.add(conteudos[i]).grow();
            slot.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) { usar(indice); }
            });
            slots[i] = slot;
        }
        if (MOBILE) {
            for (int i = 0; i < slots.length; i++) {
                // Opcoes dos dragkeys: so' aparecem com o dedo segurando.
                if (i < PRIMEIRO_FIXO) {
                    slots[i].setVisible(false);
                    slots[i].setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
                }
                grupoMobile.addActor(slots[i]);
            }
            for (int k = 0; k < dragkeys.length; k++) dragkeys[k] = new Dragkey(k * 2);
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

    /** Botao grande que guarda 2 atalhos: segura e desliza pra cima (1o) ou
     * pra esquerda (2o); soltar usa o destacado. */
    private final class Dragkey {
        final int primeiro;
        final Button botao;
        boolean aberto = false;
        int escolhida = -1;

        Dragkey(int primeiro) {
            this.primeiro = primeiro;
            Button.ButtonStyle estilo = new Button.ButtonStyle();
            estilo.up = circulo(new Color(0.13f, 0.13f, 0.13f, 0.92f), new Color(0.42f, 0.42f, 0.42f, 1f));
            botao = new Button(estilo);
            Label texto = new Label((primeiro + 1) + "/" + (primeiro + 2), skin, "hud");
            texto.setFontScale(0.9f);
            texto.setColor(1f, 1f, 1f, 0.75f);
            botao.add(texto);
            botao.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, int b) {
                    abrir();
                    return true;
                }
                @Override public void touchDragged(InputEvent e, float x, float y, int pointer) {
                    escolher(e.getStageX(), e.getStageY());
                }
                @Override public void touchUp(InputEvent e, float x, float y, int pointer, int b) {
                    int i = escolhida;
                    fechar();
                    if (i >= 0) usar(primeiro + i);
                }
            });
            grupoMobile.addActor(botao);
        }

        float centroX() { return botao.getX() + botao.getWidth() / 2f; }
        float centroY() { return botao.getY() + botao.getHeight() / 2f; }

        void abrir() {
            aberto = true;
            escolhida = -1;
            for (int i = 0; i < ANGULOS_OPCAO.length; i++) {
                Button opcao = slots[primeiro + i];
                float ang = ANGULOS_OPCAO[i] * MathUtils.degreesToRadians;
                opcao.setStyle(estiloSlot);
                opcao.setBounds(centroX() + DIST_OPCAO * MathUtils.cos(ang) - TAM_OPCAO / 2f,
                    centroY() + DIST_OPCAO * MathUtils.sin(ang) - TAM_OPCAO / 2f, TAM_OPCAO, TAM_OPCAO);
                opcao.setVisible(true);
                opcao.getColor().a = 0f;
                opcao.addAction(Actions.fadeIn(0.08f));
                opcao.toFront();
            }
            botao.toFront();
        }

        /** Opcao na direcao do dedo (cima ou esquerda), ou nenhuma. */
        void escolher(float dedoX, float dedoY) {
            if (!aberto) return;
            float dx = dedoX - centroX(), dy = dedoY - centroY();
            int nova = -1;
            if (dx * dx + dy * dy >= ZONA_MORTA * ZONA_MORTA) {
                float ang = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
                float melhor = Float.MAX_VALUE;
                for (int i = 0; i < ANGULOS_OPCAO.length; i++) {
                    float d = Math.abs(((ang - ANGULOS_OPCAO[i]) % 360f + 540f) % 360f - 180f);
                    if (d < melhor) { melhor = d; nova = i; }
                }
                // Deslizou pra baixo/direita (longe das 2 opcoes): nenhuma.
                if (melhor > 60f) nova = -1;
            }
            if (nova == escolhida) return;
            escolhida = nova;
            for (int i = 0; i < ANGULOS_OPCAO.length; i++) {
                slots[primeiro + i].setStyle(i == escolhida ? estiloSlotEscolhido : estiloSlot);
            }
        }

        void fechar() {
            aberto = false;
            escolhida = -1;
            for (int i = 0; i < ANGULOS_OPCAO.length; i++) {
                slots[primeiro + i].setStyle(estiloSlot);
                slots[primeiro + i].setVisible(false);
            }
        }
    }

    private void posicionarMobile() {
        Stage stage = grupoMobile.getStage();
        if (stage == null) return;
        float cx = stage.getWidth() - CANTO_X, cy = CANTO_Y;
        for (int k = 0; k < dragkeys.length; k++) {
            if (dragkeys[k] == null || dragkeys[k].aberto) continue; // nao mexe com o dedo em cima
            float ang = ANGULOS_DRAGKEYS[k] * MathUtils.degreesToRadians;
            dragkeys[k].botao.setBounds(cx + RAIO_DRAGKEYS * MathUtils.cos(ang) - TAM_DRAGKEY / 2f,
                cy + RAIO_DRAGKEYS * MathUtils.sin(ang) - TAM_DRAGKEY / 2f, TAM_DRAGKEY, TAM_DRAGKEY);
        }
        for (int i = 0; i < ANGULOS_FIXOS.length; i++) {
            float ang = ANGULOS_FIXOS[i] * MathUtils.degreesToRadians;
            slots[PRIMEIRO_FIXO + i].setBounds(cx + RAIO_FIXOS * MathUtils.cos(ang) - TAM_FIXO / 2f,
                cy + RAIO_FIXOS * MathUtils.sin(ang) - TAM_FIXO / 2f, TAM_FIXO, TAM_FIXO);
        }
    }

    private float tamanhoSlot(int i) {
        if (!MOBILE) return TAM_SLOT;
        return i >= PRIMEIRO_FIXO ? TAM_FIXO : TAM_OPCAO;
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
                    centro.add(img).size(tamanhoSlot(i) * (MOBILE ? 0.58f : 0.68f));
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

    /** Usa o slot (clique, toque ou tecla): escurece rapidinho e manda pro servidor. */
    private void usar(int indice) {
        Button slot = slots[indice];
        apertar(slot);
        escurecerIcones(conteudos[indice]);
        // Celular, opcao de dragkey: ela some junto, entao o "apertado" vai no dragkey.
        if (MOBILE && indice < PRIMEIRO_FIXO && dragkeys[indice / 2] != null) apertar(dragkeys[indice / 2].botao);
        String caminho = livro.atalhos()[indice];
        if (caminho != null && !caminho.isEmpty()) aoUsar.usar(indice);
    }

    private static void apertar(Button b) {
        b.clearActions();
        b.setColor(COR_APERTADO);
        b.addAction(Actions.color(Color.WHITE, 0.18f));
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

    /** Tecla 1-9 (PC). */
    public boolean usarTecla(int numero) {
        int i = numero - 1;
        if (i < 0 || i >= slots.length) return false;
        usar(i);
        return true;
    }

    public void setVisivel(boolean visivel) {
        raiz.setVisible(visivel);
        if (!visivel && grupoMobile.isVisible()) for (Dragkey d : dragkeys) if (d != null) d.fechar();
        grupoMobile.setVisible(visivel);
    }
}
