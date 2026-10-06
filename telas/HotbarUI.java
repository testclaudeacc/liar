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
 * PC: uma linha no centro de baixo. Celular (lado direito; a esquerda ja'
 * tem o joystick): 1-4 fixos e redondos num arco em volta do canto de baixo
 * (tocar usa); 5-8 num botao acima que, segurado, abre um "+" (arrasta o
 * dedo ate' um e solta pra usar).
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
    /** Celular: 2 botoes de roda (posicao calculada a cada frame). */
    private final com.badlogic.gdx.scenes.scene2d.Group grupoMobile = new com.badlogic.gdx.scenes.scene2d.Group() {
        @Override public void act(float delta) {
            super.act(delta);
            posicionarMobile();
        }
    };
    private final Roda[] rodas = new Roda[2];
    private Button.ButtonStyle estiloSlot, estiloSlotEscolhido;
    private final Button[] slots = new Button[BookMenuUI.SLOTS_ATALHO];
    private final Stack[] conteudos = new Stack[BookMenuUI.SLOTS_ATALHO];

    public HotbarUI(Stage stage, Skin skin, BookMenuUI livro, AoUsar aoUsar) {
        this.skin = skin;
        this.livro = livro;
        this.aoUsar = aoUsar;
        // Mesmo cinza escuro dos slots da bag (e das outras janelas). No
        // celular os botoes sao redondos.
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estiloSlot = estilo;
        if (MOBILE) {
            estilo.up = circulo(new Color(0.15f, 0.15f, 0.15f, 0.92f), new Color(0.32f, 0.32f, 0.32f, 1f));
            estiloSlotEscolhido = new Button.ButtonStyle();
            estiloSlotEscolhido.up = circulo(new Color(0.22f, 0.22f, 0.22f, 0.96f), new Color(0.95f, 0.65f, 0.24f, 1f));
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
            // 1-4: fixos em arco no canto de baixo (tocar usa). 5-8: opcoes da
            // roda, so' aparecem com o dedo segurando o botao "5-8".
            for (int i = 0; i < slots.length; i++) {
                if (i >= 4) {
                    slots[i].setVisible(false);
                    slots[i].setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
                }
                grupoMobile.addActor(slots[i]);
            }
            rodas[1] = new Roda(4, "5-8");
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

    // ---- Celular ----
    // Slots 1-4: fixos, num arco em volta do canto de baixo a direita (tocar
    // usa). Slots 5-8: segurar o botao "5-8" (acima do arco) abre um "+" com
    // eles; arrastar o dedo na direcao de um destaca e soltar usa. Soltar no
    // meio (sem arrastar) cancela.
    private static final float TAM_FIXO = 74f;
    /** Centro do arco (distancia da borda direita, altura a partir de baixo). */
    private static final float ARCO_CX = 16f, ARCO_CY = 24f;
    private static final float RAIO_ARCO = 190f;
    private static final float[] ANGULOS_ARCO = {100f, 125f, 150f, 175f};
    private static final float TAM_BOTAO_RODA = 84f;
    private static final float RAIO_MAIS = 84f;
    private static final float[] ANGULOS_MAIS = {90f, 0f, -90f, 180f};
    /** Arrastou menos que isso do centro: nenhuma opcao (soltar cancela). */
    private static final float ZONA_MORTA = 30f;
    /** Centro do botao "5-8": (distancia da borda direita, altura a partir de baixo). */
    private static final float[] POSICAO_RODA = {172f, 322f};

    private float tamanhoSlot(int i) { return MOBILE && i < 4 ? TAM_FIXO : TAM_SLOT; }

    private final class Roda {
        final int primeiro;
        final Button botao;
        boolean aberta = false;
        int escolhida = -1;
        float centroX, centroY; // centro da roda aberta (pode ser empurrado pra caber na tela)

        Roda(int primeiro, String rotulo) {
            this.primeiro = primeiro;
            Button.ButtonStyle estilo = new Button.ButtonStyle();
            estilo.up = circulo(new Color(0.13f, 0.13f, 0.13f, 0.92f), new Color(0.42f, 0.42f, 0.42f, 1f));
            botao = new Button(estilo);
            Label texto = new Label(rotulo, skin, "hud");
            texto.setFontScale(0.9f);
            texto.setColor(1f, 1f, 1f, 0.75f);
            botao.add(texto);
            botao.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, int b) {
                    abrir();
                    escolher(e.getStageX(), e.getStageY());
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

        void abrir() {
            Stage stage = botao.getStage();
            if (stage == null) return;
            aberta = true;
            escolhida = -1;
            // Centro = centro do botao, empurrado pra dentro da tela se alguma
            // ponta do "+" fosse sair.
            float margem = RAIO_MAIS + TAM_SLOT / 2f + 6f;
            float bx = botao.getX() + botao.getWidth() / 2f, by = botao.getY() + botao.getHeight() / 2f;
            centroX = Math.max(margem, Math.min(bx, stage.getWidth() - margem));
            centroY = Math.max(margem, Math.min(by, stage.getHeight() - margem));
            for (int i = 0; i < 4; i++) {
                Button opcao = slots[primeiro + i];
                float ang = ANGULOS_MAIS[i] * com.badlogic.gdx.math.MathUtils.degreesToRadians;
                opcao.setStyle(estiloSlot);
                opcao.setBounds(centroX + RAIO_MAIS * com.badlogic.gdx.math.MathUtils.cos(ang) - TAM_SLOT / 2f,
                    centroY + RAIO_MAIS * com.badlogic.gdx.math.MathUtils.sin(ang) - TAM_SLOT / 2f, TAM_SLOT, TAM_SLOT);
                opcao.setVisible(true);
                opcao.getColor().a = 0f;
                opcao.addAction(Actions.fadeIn(0.08f));
                opcao.toFront();
            }
            botao.toFront();
        }

        /** Opcao na direcao do dedo (a partir do centro), ou nenhuma. */
        void escolher(float dedoX, float dedoY) {
            if (!aberta) return;
            float dx = dedoX - centroX, dy = dedoY - centroY;
            int nova = -1;
            if (dx * dx + dy * dy >= ZONA_MORTA * ZONA_MORTA) {
                float ang = com.badlogic.gdx.math.MathUtils.atan2(dy, dx) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
                float melhor = Float.MAX_VALUE;
                for (int i = 0; i < ANGULOS_MAIS.length; i++) {
                    float d = Math.abs(((ang - ANGULOS_MAIS[i]) % 360f + 540f) % 360f - 180f);
                    if (d < melhor) { melhor = d; nova = i; }
                }
            }
            if (nova == escolhida) return;
            escolhida = nova;
            for (int i = 0; i < 4; i++) slots[primeiro + i].setStyle(i == escolhida ? estiloSlotEscolhido : estiloSlot);
        }

        void fechar() {
            aberta = false;
            escolhida = -1;
            for (int i = 0; i < 4; i++) {
                Button opcao = slots[primeiro + i];
                opcao.setStyle(estiloSlot);
                opcao.setVisible(false);
            }
        }
    }

    private void posicionarMobile() {
        Stage stage = grupoMobile.getStage();
        if (stage == null) return;
        float w = stage.getWidth();
        float cx = w - ARCO_CX;
        for (int i = 0; i < 4; i++) {
            float ang = ANGULOS_ARCO[i] * com.badlogic.gdx.math.MathUtils.degreesToRadians;
            slots[i].setBounds(cx + RAIO_ARCO * com.badlogic.gdx.math.MathUtils.cos(ang) - TAM_FIXO / 2f,
                ARCO_CY + RAIO_ARCO * com.badlogic.gdx.math.MathUtils.sin(ang) - TAM_FIXO / 2f, TAM_FIXO, TAM_FIXO);
        }
        Roda r = rodas[1];
        if (r != null && !r.aberta) {
            r.botao.setBounds(w - POSICAO_RODA[0] - TAM_BOTAO_RODA / 2f,
                POSICAO_RODA[1] - TAM_BOTAO_RODA / 2f, TAM_BOTAO_RODA, TAM_BOTAO_RODA);
        }
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

    /** Usa o slot (clique ou tecla): escurece rapidinho e manda pro servidor. */
    private void usar(int indice) {
        Button slot = slots[indice];
        slot.clearActions();
        slot.setColor(COR_APERTADO);
        slot.addAction(Actions.color(Color.WHITE, 0.18f));
        escurecerIcones(conteudos[indice]);
        // Celular, 5-8: o slot some junto com a roda, entao o "apertado" vai no botao da roda.
        if (MOBILE && indice >= 4) {
            Roda r = rodas[1];
            if (r != null) {
                r.botao.clearActions();
                r.botao.setColor(COR_APERTADO);
                r.botao.addAction(Actions.color(Color.WHITE, 0.18f));
            }
        }
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
        if (!visivel && grupoMobile.isVisible()) for (Roda r : rodas) if (r != null) r.fechar();
        grupoMobile.setVisible(visivel);
    }
}
