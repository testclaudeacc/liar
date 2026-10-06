package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;

import java.util.ArrayList;
import java.util.List;

/**
 * Barra de atalhos da gameplay: 9 slots (BookMenuUI.SLOTS_ATALHO). O que vai
 * em cada slot e' escolhido na aba Spells do livro e fica salvo no servidor
 * (servidor.py::set_hotbar). Usar manda servidor.py::use_hotbar.
 *
 * PC: uma linha no centro de baixo; teclas configuraveis (Controles).
 *
 * Celular: botoes no lado direito, montados a partir do layout salvo no
 * aparelho (Controles.layoutMobile), editavel em Settings > Controls > Edit
 * buttons. Cada botao e':
 *  - FIXO: 1 atalho (so' comida/pocao), tocar usa;
 *  - de ARRASTAR: 1 a 4 atalhos, um por direcao ligada (cima, direita,
 *    baixo, esquerda) - segura, desliza pra direcao e solta (soltar sem
 *    deslizar cancela).
 * Os 9 slots vao sendo distribuidos em ordem pelos botoes (no maximo 9
 * atalhos somados).
 */
public final class HotbarUI {

    public interface AoUsar { void usar(int indice); }

    private static final boolean MOBILE = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
        || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
    private static final float TAM_SLOT = 54f;     // PC
    private static final float ESPACO = 5f;        // PC
    /** Tom do "apertado" (clique ou tecla): escurece e volta. */
    private static final Color COR_APERTADO = new Color(0.55f, 0.55f, 0.55f, 1f);
    private static final Color COR_SELECAO = new Color(0.95f, 0.65f, 0.24f, 1f);

    // ---- Celular ----
    /** Opcoes que aparecem ao segurar um botao de arrastar. */
    private static final float TAM_OPCAO = 66f;
    /** Angulo de cada direcao: cima, direita, baixo, esquerda. */
    private static final float[] ANGULOS = {90f, 0f, -90f, 180f};
    private static final String[] NOMES_DIRECAO = {"Up", "Right", "Down", "Left"};
    /** Arrastou menos que isso: nenhuma opcao (soltar cancela). */
    private static final float ZONA_MORTA = 30f;

    private final Stage stage;
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
    private Button.ButtonStyle estiloSlot, estiloSlotEscolhido;
    private com.badlogic.gdx.scenes.scene2d.utils.Drawable circuloBotao, circuloBotaoSelecionado;

    /** Botoes do celular montados a partir do layout. */
    private final List<BotaoNaTela> botoes = new ArrayList<>();
    /** Dono de cada slot no layout atual (null = slot fora do layout). */
    private final BotaoNaTela[] donoDoSlot = new BotaoNaTela[BookMenuUI.SLOTS_ATALHO];
    private List<Controles.BotaoMobile> layout;

    public HotbarUI(Stage stage, Skin skin, BookMenuUI livro, AoUsar aoUsar) {
        this.stage = stage;
        this.skin = skin;
        this.livro = livro;
        this.aoUsar = aoUsar;
        estiloSlot = new Button.ButtonStyle();
        if (MOBILE) {
            estiloSlot.up = circulo(new Color(0.15f, 0.15f, 0.15f, 0.92f), new Color(0.32f, 0.32f, 0.32f, 1f));
            estiloSlotEscolhido = new Button.ButtonStyle();
            estiloSlotEscolhido.up = circulo(new Color(0.22f, 0.22f, 0.22f, 0.96f), COR_SELECAO);
            circuloBotao = circulo(new Color(0.13f, 0.13f, 0.13f, 0.92f), new Color(0.42f, 0.42f, 0.42f, 1f));
            circuloBotaoSelecionado = circulo(new Color(0.13f, 0.13f, 0.13f, 0.92f), COR_SELECAO);
        } else {
            // Um pouco mais escuro que os slots da bag (a pedido do usuario).
            estiloSlot.up = UiSkin.retangulo(new Color(0.08f, 0.08f, 0.08f, 0.92f), new Color(0.17f, 0.17f, 0.17f, 1f), 2);
            estiloSlot.over = UiSkin.retangulo(new Color(0.12f, 0.12f, 0.12f, 0.95f), new Color(0.27f, 0.27f, 0.27f, 1f), 2);
        }
        for (int i = 0; i < slots.length; i++) {
            final int indice = i;
            Button slot = new Button(estiloSlot);
            conteudos[i] = new Stack();
            slot.add(conteudos[i]).grow();
            slot.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    if (!editando) usar(indice);
                }
            });
            if (MOBILE) slot.addListener(new ListenerEdicao(() -> donoDoSlot[indice]));
            slots[i] = slot;
        }
        if (MOBILE) {
            grupoMobile.setTouchable(Touchable.childrenOnly);
            // Por baixo das outras telas (livro, chat, settings), igual a HUD.
            stage.getRoot().addActorAt(0, grupoMobile);
            layout = Controles.layoutMobile();
            montarMobile();
        } else {
            raiz.setFillParent(true);
            raiz.bottom().pad(0, 0, 10, 0);
            for (Button slot : slots) raiz.add(slot).size(TAM_SLOT).pad(ESPACO / 2f);
            stage.getRoot().addActorAt(0, raiz);
        }
        atualizar();
    }

    // =====================================================================
    // Celular: botoes a partir do layout
    // =====================================================================

    /** Um botao do layout ja' na tela. Fixo: o proprio slot e' o botao. De
     * arrastar: botao principal proprio + um slot por direcao ligada. */
    private final class BotaoNaTela {
        final Controles.BotaoMobile cfg;
        final Button principal;
        final int[] slotDaDirecao = {-1, -1, -1, -1};
        boolean aberto = false;
        int escolhida = -1; // direcao escolhida (0-3) ou -1

        BotaoNaTela(Controles.BotaoMobile cfg, int primeiroSlot) {
            this.cfg = cfg;
            if (cfg.fixo) {
                principal = slots[primeiroSlot];
                principal.setStyle(estiloSlot);
                principal.setVisible(true);
                principal.setTouchable(Touchable.enabled);
                donoDoSlot[primeiroSlot] = this;
            } else {
                Button.ButtonStyle estilo = new Button.ButtonStyle();
                estilo.up = circuloBotao;
                principal = new Button(estilo);
                int s = primeiroSlot;
                for (int d = 0; d < 4; d++) {
                    if (!cfg.direcoes[d]) continue;
                    slotDaDirecao[d] = s;
                    donoDoSlot[s] = this;
                    slots[s].setVisible(false);
                    slots[s].setTouchable(Touchable.disabled);
                    s++;
                }
                Label texto = new Label(rotuloDrag(), skin, "hud");
                texto.setFontScale(0.9f);
                texto.setColor(1f, 1f, 1f, 0.75f);
                principal.add(texto);
                principal.addListener(new InputListener() {
                    @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, int b) {
                        if (editando) return false; // quem trata e' o ListenerEdicao
                        abrir();
                        return true;
                    }
                    @Override public void touchDragged(InputEvent e, float x, float y, int pointer) {
                        if (!editando) escolher(e.getStageX(), e.getStageY());
                    }
                    @Override public void touchUp(InputEvent e, float x, float y, int pointer, int b) {
                        if (editando) return;
                        int d = escolhida;
                        fechar();
                        if (d >= 0 && slotDaDirecao[d] >= 0) usar(slotDaDirecao[d]);
                    }
                });
                principal.addListener(new ListenerEdicao(() -> this));
            }
            grupoMobile.addActor(principal);
        }

        /** Ex: "1/2", "3/4/5/6" - os slots desse botao. */
        String rotuloDrag() {
            StringBuilder sb = new StringBuilder();
            for (int d = 0; d < 4; d++) {
                if (slotDaDirecao[d] < 0) continue;
                if (sb.length() > 0) sb.append('/');
                sb.append(slotDaDirecao[d] + 1);
            }
            return sb.toString();
        }

        float cx() { return principal.getX() + principal.getWidth() / 2f; }
        float cy() { return principal.getY() + principal.getHeight() / 2f; }

        /** Mostra as opcoes em volta (no modo edicao ficam apagadas, so' pra ver). */
        void mostrarOpcoes(float alfa) {
            float dist = cfg.tamanho / 2f + TAM_OPCAO / 2f + 6f;
            for (int d = 0; d < 4; d++) {
                int s = slotDaDirecao[d];
                if (s < 0) continue;
                float ang = ANGULOS[d] * MathUtils.degreesToRadians;
                slots[s].setStyle(estiloSlot);
                slots[s].setBounds(cx() + dist * MathUtils.cos(ang) - TAM_OPCAO / 2f,
                    cy() + dist * MathUtils.sin(ang) - TAM_OPCAO / 2f, TAM_OPCAO, TAM_OPCAO);
                slots[s].setVisible(true);
                slots[s].clearActions();
                slots[s].getColor().a = alfa;
                slots[s].toFront();
            }
            principal.toFront();
        }

        void esconderOpcoes() {
            for (int d = 0; d < 4; d++) {
                int s = slotDaDirecao[d];
                if (s < 0) continue;
                slots[s].setStyle(estiloSlot);
                slots[s].setVisible(false);
            }
        }

        void abrir() {
            aberto = true;
            escolhida = -1;
            mostrarOpcoes(0f);
            for (int d = 0; d < 4; d++) if (slotDaDirecao[d] >= 0) slots[slotDaDirecao[d]].addAction(Actions.fadeIn(0.08f));
        }

        /** Direcao ligada mais perto da direcao do dedo, ou nenhuma. */
        void escolher(float dedoX, float dedoY) {
            if (!aberto) return;
            float dx = dedoX - cx(), dy = dedoY - cy();
            int nova = -1;
            if (dx * dx + dy * dy >= ZONA_MORTA * ZONA_MORTA) {
                float ang = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
                float melhor = Float.MAX_VALUE;
                for (int d = 0; d < 4; d++) {
                    if (slotDaDirecao[d] < 0) continue;
                    float dif = Math.abs(((ang - ANGULOS[d]) % 360f + 540f) % 360f - 180f);
                    if (dif < melhor) { melhor = dif; nova = d; }
                }
                if (melhor > 60f) nova = -1; // dedo pra um lado sem opcao
            }
            if (nova == escolhida) return;
            escolhida = nova;
            for (int d = 0; d < 4; d++) {
                if (slotDaDirecao[d] >= 0) slots[slotDaDirecao[d]].setStyle(d == escolhida ? estiloSlotEscolhido : estiloSlot);
            }
        }

        void fechar() {
            aberto = false;
            escolhida = -1;
            esconderOpcoes();
        }
    }

    /** Recria os botoes do celular a partir do layout atual. */
    private void montarMobile() {
        for (BotaoNaTela b : botoes) b.fechar();
        botoes.clear();
        grupoMobile.clearChildren();
        java.util.Arrays.fill(donoDoSlot, null);
        for (Button s : slots) {
            s.setVisible(false);
            s.setTouchable(Touchable.disabled);
            grupoMobile.addActor(s);
        }
        int proximo = 0;
        for (Controles.BotaoMobile cfg : layout) {
            int n = cfg.slots();
            if (n <= 0 || proximo + n > slots.length) continue;
            botoes.add(new BotaoNaTela(cfg, proximo));
            proximo += n;
        }
        atualizar();
    }

    private void posicionarMobile() {
        Stage st = grupoMobile.getStage();
        if (st == null) return;
        float w = st.getWidth();
        for (BotaoNaTela b : botoes) {
            if (b.aberto) continue; // nao mexe com o dedo em cima
            float t = b.cfg.tamanho;
            b.principal.setBounds(w - b.cfg.x - t / 2f, b.cfg.y - t / 2f, t, t);
            if (!b.cfg.fixo) {
                b.principal.getStyle().up = editando && b == selecionado ? circuloBotaoSelecionado : circuloBotao;
                if (editando) b.mostrarOpcoes(0.45f);
            } else {
                b.principal.setStyle(editando && b == selecionado ? estiloSlotEscolhido : estiloSlot);
            }
        }
    }

    // =====================================================================
    // Celular: editor de layout (Settings > Controls > Edit buttons)
    // =====================================================================

    private boolean editando = false;
    private BotaoNaTela selecionado = null;
    private Table painelEdicao;
    private Runnable aoSairEdicao;

    /** Arrastar move o botao; tocar seleciona (painel mostra as opcoes dele). */
    private final class ListenerEdicao extends InputListener {
        final java.util.function.Supplier<BotaoNaTela> dono;
        float iniX, iniY, cfgIniX, cfgIniY;

        ListenerEdicao(java.util.function.Supplier<BotaoNaTela> dono) { this.dono = dono; }

        @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, int b) {
            if (!editando) return false;
            BotaoNaTela bt = dono.get();
            if (bt == null) return false;
            selecionado = bt;
            iniX = e.getStageX(); iniY = e.getStageY();
            cfgIniX = bt.cfg.x; cfgIniY = bt.cfg.y;
            atualizarPainel();
            return true;
        }

        @Override public void touchDragged(InputEvent e, float x, float y, int pointer) {
            if (!editando) return;
            BotaoNaTela bt = dono.get();
            if (bt == null) return;
            float meio = bt.cfg.tamanho / 2f;
            bt.cfg.x = MathUtils.clamp(cfgIniX - (e.getStageX() - iniX), meio, stage.getWidth() - meio);
            bt.cfg.y = MathUtils.clamp(cfgIniY + (e.getStageY() - iniY), meio, stage.getHeight() - meio);
        }

        @Override public void touchUp(InputEvent e, float x, float y, int pointer, int b) {
            if (editando) Controles.salvarLayoutMobile(layout);
        }
    }

    public void entrarEdicao(Runnable aoSair) {
        if (!MOBILE) return;
        aoSairEdicao = aoSair;
        editando = true;
        selecionado = null;
        if (painelEdicao == null) {
            painelEdicao = new Table();
            painelEdicao.setFillParent(true);
            painelEdicao.top().padTop(96);
            painelEdicao.setTouchable(Touchable.childrenOnly);
            stage.addActor(painelEdicao);
        }
        painelEdicao.setVisible(true);
        painelEdicao.toFront();
        atualizarPainel();
        atualizar(); // numero de cada slot aparece no editor
    }

    private void sairEdicao() {
        editando = false;
        selecionado = null;
        Controles.salvarLayoutMobile(layout);
        for (BotaoNaTela b : botoes) b.fechar();
        if (painelEdicao != null) painelEdicao.setVisible(false);
        atualizar();
        if (aoSairEdicao != null) aoSairEdicao.run();
    }

    private int slotsUsados() {
        int n = 0;
        for (Controles.BotaoMobile b : layout) n += b.slots();
        return n;
    }

    /** Mudou o layout: salva, remonta e mantem a selecao no mesmo botao. */
    private void layoutMudou(Controles.BotaoMobile selecionar) {
        Controles.salvarLayoutMobile(layout);
        montarMobile();
        selecionado = null;
        for (BotaoNaTela b : botoes) if (b.cfg == selecionar) selecionado = b;
        atualizarPainel();
    }

    private TextButton botaoPainel(String texto, boolean ligado, Runnable acao) {
        TextButton.TextButtonStyle e = new TextButton.TextButtonStyle();
        e.font = skin.getFont("botao-pequeno-font");
        e.fontColor = Color.WHITE;
        Color borda = ligado ? COR_SELECAO : new Color(0.4f, 0.4f, 0.4f, 1f);
        e.up = UiSkin.retangulo(new Color(0.14f, 0.14f, 0.14f, 1f), borda, 2);
        e.down = UiSkin.retangulo(new Color(0.08f, 0.08f, 0.08f, 1f), borda, 2);
        TextButton b = new TextButton(texto, e);
        b.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { acao.run(); }
        });
        return b;
    }

    private void atualizarPainel() {
        if (painelEdicao == null) return;
        painelEdicao.clearChildren();
        Table caixa = new Table();
        caixa.setBackground(skin.getDrawable("popup-painel"));
        caixa.pad(10, 14, 10, 14);
        caixa.defaults().height(44).pad(3);

        int usados = slotsUsados();
        Table linha1 = new Table();
        linha1.defaults().height(44).pad(3);
        Label info = new Label("Slots " + usados + "/" + Controles.MAX_SLOTS_MOBILE, skin, "opcoes-label");
        linha1.add(info).padRight(10);
        linha1.add(botaoPainel("+ Fixed", false, () -> {
            if (slotsUsados() >= Controles.MAX_SLOTS_MOBILE) return;
            Controles.BotaoMobile novo = new Controles.BotaoMobile(true, 300f, 220f, 78f, false, false, false, false);
            layout.add(novo);
            layoutMudou(novo);
        })).width(120);
        linha1.add(botaoPainel("+ Drag", false, () -> {
            if (slotsUsados() >= Controles.MAX_SLOTS_MOBILE) return;
            Controles.BotaoMobile novo = new Controles.BotaoMobile(false, 400f, 220f, 88f, true, false, false, false);
            layout.add(novo);
            layoutMudou(novo);
        })).width(120);
        linha1.add(botaoPainel("Reset", false, () -> {
            layout = Controles.layoutPadrao();
            layoutMudou(null);
        })).width(100).padLeft(16);
        linha1.add(botaoPainel("Done", true, this::sairEdicao)).width(100);
        caixa.add(linha1).row();

        if (selecionado == null) {
            caixa.add(new Label("Tap a button to edit it, drag to move it", skin, "opcoes-label")).padTop(4).row();
        } else {
            final Controles.BotaoMobile c = selecionado.cfg;
            Table linha2 = new Table();
            linha2.defaults().height(44).pad(3);
            linha2.add(botaoPainel(c.fixo ? "Fixed" : "Drag", true, () -> {
                if (c.fixo) {
                    c.fixo = false;
                    java.util.Arrays.fill(c.direcoes, false);
                    c.direcoes[0] = true; // continua usando 1 slot
                } else {
                    c.fixo = true;
                }
                layoutMudou(c);
            })).width(100);
            linha2.add(botaoPainel("-", false, () -> {
                c.tamanho = Math.max(Controles.TAM_BOTAO_MIN, c.tamanho - 8f);
                layoutMudou(c);
            })).width(48);
            linha2.add(new Label(Math.round(c.tamanho) + "", skin, "opcoes-label")).width(48);
            linha2.add(botaoPainel("+", false, () -> {
                c.tamanho = Math.min(Controles.TAM_BOTAO_MAX, c.tamanho + 8f);
                layoutMudou(c);
            })).width(48);
            if (!c.fixo) {
                for (int d = 0; d < 4; d++) {
                    final int dir = d;
                    linha2.add(botaoPainel(NOMES_DIRECAO[d], c.direcoes[d], () -> {
                        if (c.direcoes[dir]) {
                            if (c.slots() <= 1) return; // pelo menos 1 direcao
                            c.direcoes[dir] = false;
                        } else {
                            if (slotsUsados() >= Controles.MAX_SLOTS_MOBILE) return;
                            c.direcoes[dir] = true;
                        }
                        layoutMudou(c);
                    })).width(84);
                }
            }
            linha2.add(botaoPainel("Delete", false, () -> {
                layout.remove(c);
                layoutMudou(null);
            })).width(100).padLeft(10);
            caixa.add(linha2).row();
            String dica = c.fixo ? "Fixed: tap to use (food & potions only)"
                : "Drag: hold, slide to a direction and release";
            caixa.add(new Label(dica, skin, "opcoes-label")).padTop(2).row();
        }
        painelEdicao.add(caixa);
    }

    // =====================================================================
    // Comum
    // =====================================================================

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

    private float tamanhoSlot(int i) {
        if (!MOBILE) return TAM_SLOT;
        BotaoNaTela b = donoDoSlot[i];
        return b != null && b.cfg.fixo ? b.cfg.tamanho : TAM_OPCAO;
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
            // PC: tecla do slot no canto; celular, no editor: numero do slot.
            if (!MOBILE || editando) {
                String texto = MOBILE ? String.valueOf(i + 1) : Controles.nomeTecla(Controles.tecla("hotbar" + (i + 1)));
                Label tecla = new Label(texto, skin, "hud");
                tecla.setFontScale(0.55f);
                tecla.setColor(1f, 1f, 1f, 0.5f);
                Table cantoTecla = new Table();
                if (MOBILE) { cantoTecla.top(); cantoTecla.add(tecla).padTop(4); }
                else { cantoTecla.top().left(); cantoTecla.add(tecla).pad(2, 4, 0, 0); }
                pilha.add(cantoTecla);
            }
        }
    }

    /** Usa o slot (clique, toque ou tecla): escurece rapidinho e manda pro servidor. */
    private void usar(int indice) {
        apertar(slots[indice]);
        escurecerIcones(conteudos[indice]);
        // Celular, opcao de botao de arrastar: ela some junto, entao o "apertado" vai no botao.
        BotaoNaTela dono = MOBILE ? donoDoSlot[indice] : null;
        if (dono != null && !dono.cfg.fixo) apertar(dono.principal);
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
        for (Actor filho : grupo.getChildren()) {
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

    /** Tecla de atalho (PC): slot 1-9. */
    public boolean usarTecla(int numero) {
        int i = numero - 1;
        if (i < 0 || i >= slots.length) return false;
        usar(i);
        return true;
    }

    public boolean isEditando() { return editando; }

    public void setVisivel(boolean visivel) {
        raiz.setVisible(visivel);
        if (!visivel && grupoMobile.isVisible()) for (BotaoNaTela b : botoes) if (!editando) b.fechar();
        grupoMobile.setVisible(visivel || editando);
    }
}
