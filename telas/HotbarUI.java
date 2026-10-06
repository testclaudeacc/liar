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
 * Celular: botoes redondos (botao i = slot i), tocar usa. Comeca com 4;
 * em Settings > Controls > Edit buttons da' pra adicionar ate' 9, remover,
 * mover arrastando e mudar o tamanho de cada um (salvo no aparelho,
 * Controles.layoutMobile).
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
    /** Botao/joystick selecionado no editor de controles (celular). */
    public static final Color COR_EDICAO = new Color(0.3f, 0.88f, 1f, 1f);

    // ---- Celular ----

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
    /** Celular: botoes (1 a 9; botao i = slot i), salvo no aparelho. */
    private List<Controles.BotaoMobile> layout = new ArrayList<>();

    public HotbarUI(Stage stage, Skin skin, BookMenuUI livro, AoUsar aoUsar) {
        this.stage = stage;
        this.skin = skin;
        this.livro = livro;
        this.aoUsar = aoUsar;
        estiloSlot = new Button.ButtonStyle();
        if (MOBILE) {
            estiloSlot.up = circulo(new Color(0.15f, 0.15f, 0.15f, 0.92f), new Color(0.32f, 0.32f, 0.32f, 1f));
            estiloSlotEscolhido = new Button.ButtonStyle();
            estiloSlotEscolhido.up = circulo(new Color(0.22f, 0.22f, 0.22f, 0.96f), COR_EDICAO);
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
            if (MOBILE) slot.addListener(new ListenerEdicao(indice));
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
    // Celular: botoes fixos a partir do layout
    // =====================================================================

    /** Mostra os slots que tem botao no layout (botao i = slot i). */
    private void montarMobile() {
        grupoMobile.clearChildren();
        for (int i = 0; i < slots.length; i++) {
            boolean temBotao = i < layout.size();
            slots[i].setVisible(temBotao);
            slots[i].setTouchable(temBotao ? Touchable.enabled : Touchable.disabled);
            if (temBotao) grupoMobile.addActor(slots[i]);
        }
        atualizar();
    }

    private void posicionarMobile() {
        Stage st = grupoMobile.getStage();
        if (st == null) return;
        float w = st.getWidth();
        for (int i = 0; i < layout.size() && i < slots.length; i++) {
            Controles.BotaoMobile b = layout.get(i);
            slots[i].setBounds(w - b.x - b.tamanho / 2f, b.y - b.tamanho / 2f, b.tamanho, b.tamanho);
            slots[i].setStyle(editando && i == selecionado ? estiloSlotEscolhido : estiloSlot);
        }
    }

    // =====================================================================
    // Celular: editor (Settings > Controls > Edit buttons)
    // =====================================================================

    private boolean editando = false;
    /** Indice do botao selecionado, SEL_JOYSTICK ou -1 (nada). */
    private int selecionado = -1;
    private static final int SEL_JOYSTICK = -2;
    private Joystick joystick;

    /** O editor tambem move/redimensiona o joystick (tudo numa tela so'). */
    public void definirJoystick(Joystick j) {
        joystick = j;
        if (j != null) j.setAoTocarEditando(() -> {
            if (!editando) return;
            selecionado = SEL_JOYSTICK;
            atualizarPainel();
        });
    }
    private Table painelEdicao;
    private Runnable aoSairEdicao;

    /** Arrastar move o botao; tocar seleciona (painel mostra tamanho/remover). */
    private final class ListenerEdicao extends InputListener {
        final int indice;
        float iniX, iniY, cfgIniX, cfgIniY;

        ListenerEdicao(int indice) { this.indice = indice; }

        @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, int b) {
            if (!editando || indice >= layout.size()) return false;
            selecionado = indice;
            Controles.BotaoMobile bt = layout.get(indice);
            iniX = e.getStageX(); iniY = e.getStageY();
            cfgIniX = bt.x; cfgIniY = bt.y;
            atualizarPainel();
            return true;
        }

        @Override public void touchDragged(InputEvent e, float x, float y, int pointer) {
            if (!editando || indice >= layout.size()) return;
            Controles.BotaoMobile bt = layout.get(indice);
            float meio = bt.tamanho / 2f;
            bt.x = MathUtils.clamp(cfgIniX - (e.getStageX() - iniX), meio, stage.getWidth() - meio);
            bt.y = MathUtils.clamp(cfgIniY + (e.getStageY() - iniY), meio, stage.getHeight() - meio);
        }

        @Override public void touchUp(InputEvent e, float x, float y, int pointer, int b) {
            if (editando) Controles.salvarLayoutMobile(layout);
        }
    }

    public void entrarEdicao(Runnable aoSair) {
        if (!MOBILE) return;
        aoSairEdicao = aoSair;
        editando = true;
        selecionado = -1;
        if (joystick != null) joystick.setEditando(true);
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
        atualizar(); // numero de cada botao aparece no editor
    }

    private void sairEdicao() {
        editando = false;
        selecionado = -1;
        Controles.salvarLayoutMobile(layout);
        if (joystick != null) { joystick.setEditando(false); joystick.setSelecionado(false); }
        if (painelEdicao != null) painelEdicao.setVisible(false);
        atualizar();
        if (aoSairEdicao != null) aoSairEdicao.run();
    }

    private void mudarTamanhoJoystick(float delta) {
        Controles.definirJoystickEscala(Controles.joystickEscala() + delta);
        if (joystick != null) joystick.aplicarConfig();
        atualizarPainel();
    }

    private void layoutMudou(int selecionar) {
        Controles.salvarLayoutMobile(layout);
        montarMobile();
        selecionado = selecionar < layout.size() ? selecionar : -1;
        atualizarPainel();
    }

    /** Botao do painel nas cores do jogo: "verde-popup", "vermelho-popup" ou
     * "cinza-popup". "+"/"-" usam a fonte de simbolo (igual o +/- do chat:
     * a fonte normal dos botoes nao tem esses simbolos). */
    private TextButton botaoPainel(String texto, String estilo, Runnable acao) {
        TextButton.TextButtonStyle e = new TextButton.TextButtonStyle(skin.get(estilo, TextButton.TextButtonStyle.class));
        boolean simbolo = "+".equals(texto) || "-".equals(texto);
        e.font = skin.getFont(simbolo ? "simbolo-font" : "botao-pequeno-font");
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

        Table linha1 = new Table();
        linha1.defaults().height(44).pad(3);
        linha1.add(new Label("Buttons " + layout.size() + "/" + Controles.MAX_BOTOES_MOBILE, skin, "opcoes-label")).padRight(10);
        if (layout.size() < Controles.MAX_BOTOES_MOBILE) { // 9/9: some o Add
            linha1.add(botaoPainel("+ Add", "verde-popup", () -> {
                if (layout.size() >= Controles.MAX_BOTOES_MOBILE) return;
                layout.add(new Controles.BotaoMobile(300f, 220f, Controles.TAM_BOTAO_PADRAO));
                layoutMudou(layout.size() - 1);
            })).width(110);
        }
        linha1.add(botaoPainel("Reset", "cinza-popup", () -> {
            layout = Controles.layoutPadrao();
            Controles.restaurarJoystick();
            if (joystick != null) joystick.aplicarConfig();
            layoutMudou(-1);
        })).width(100).padLeft(16);
        linha1.add(botaoPainel("Done", "verde-popup", this::sairEdicao)).width(100);
        caixa.add(linha1).row();

        if (joystick != null) joystick.setSelecionado(selecionado == SEL_JOYSTICK);
        if (selecionado == SEL_JOYSTICK) {
            Table linha2 = new Table();
            linha2.defaults().height(44).pad(3);
            linha2.add(new Label("Joystick size", skin, "opcoes-label")).padRight(8);
            linha2.add(botaoPainel("-", "vermelho-popup", () -> mudarTamanhoJoystick(-0.1f))).width(48);
            linha2.add(new Label(Math.round(Controles.joystickEscala() * 100f) + "%", skin, "opcoes-label")).width(64);
            linha2.add(botaoPainel("+", "verde-popup", () -> mudarTamanhoJoystick(0.1f))).width(48);
            caixa.add(linha2).row();
        } else if (selecionado < 0) {
            caixa.add(new Label("Tap a button or the joystick to edit it, drag to move it", skin, "opcoes-label")).padTop(4).row();
        } else {
            final int i = selecionado;
            final Controles.BotaoMobile c = layout.get(i);
            Table linha2 = new Table();
            linha2.defaults().height(44).pad(3);
            linha2.add(new Label("Size", skin, "opcoes-label")).padRight(8);
            // Em % do tamanho padrao, de 10 em 10 (igual o joystick).
            float passo = Controles.TAM_BOTAO_PADRAO * 0.1f;
            linha2.add(botaoPainel("-", "vermelho-popup", () -> {
                c.tamanho = Math.max(Controles.TAM_BOTAO_MIN, c.tamanho - passo);
                layoutMudou(i);
            })).width(48);
            linha2.add(new Label(Math.round(c.tamanho / Controles.TAM_BOTAO_PADRAO * 100f) + "%", skin, "opcoes-label")).width(64);
            linha2.add(botaoPainel("+", "verde-popup", () -> {
                c.tamanho = Math.min(Controles.TAM_BOTAO_MAX, c.tamanho + passo);
                layoutMudou(i);
            })).width(48);
            linha2.add(botaoPainel("Delete", "vermelho-popup", () -> {
                if (layout.size() <= 1) return; // pelo menos 1 botao
                layout.remove(i);
                layoutMudou(-1);
            })).width(100).padLeft(16);
            caixa.add(linha2).row();
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
        return i < layout.size() ? layout.get(i).tamanho : Controles.TAM_BOTAO_PADRAO;
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
                    // Icone maior (o sprite do item ja' tem sobra transparente em volta).
                    centro.add(img).size(tamanhoSlot(i) * (MOBILE ? 0.74f : 0.86f));
                }
                pilha.add(centro);
                if (qtd > 0) {
                    Label numero = new Label(String.valueOf(qtd), skin, "hud");
                    numero.setFontScale(MOBILE ? 0.75f : 0.65f);
                    Table canto = new Table();
                    // Quantidade no canto de baixo a direita (no redondo, um pouco
                    // pra dentro pra nao sair do circulo).
                    canto.bottom().right();
                    float dentro = MOBILE ? tamanhoSlot(i) * 0.12f : 0f;
                    canto.add(numero).pad(0, 0, 2 + dentro, 4 + dentro);
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
        grupoMobile.setVisible(visivel || editando);
    }
}
