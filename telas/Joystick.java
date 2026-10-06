package com.teste.game.telas;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/**
 * Porta de virtual_joystick.gd: base+knob, deadzone, clamp em max_distance e
 * limiar de 0.35 por eixo pra decidir direcao - mesmos valores numericos do
 * Godot. So' visivel em mobile (igual "OS.has_feature(mobile)" de la) -
 * continua funcional (so' invisivel) em qualquer outra plataforma, pra nao
 * precisar de nenhum caminho de codigo separado.
 *
 * Diferenca de eixo Y importante: Godot usa coordenada de tela Y-pra-baixo
 * (offset.y > 0 = arrastou pra baixo), Scene2D/libGDX usa Y-pra-cima (offset
 * POSITIVO aqui significa arrastou pra CIMA) - os thresholds de cima/baixo
 * abaixo sao invertidos de proposito em relacao a formula bruta do Godot pra
 * compensar isso.
 */
public class Joystick {

    // 85 -> 100 e 170 -> 200 (~18% maior) - "um pouco maior, nao muito", a
    // pedido do usuario - a posicao (canto inferior esquerdo, perto do
    // dedao) ja estava boa e nao mudou.
    private static final float MAX_DISTANCE_BASE = 100f;
    private static final float DEADZONE_BASE = 20f;
    private static final float TAM_BASE = 200f, TAM_KNOB = 56f;
    private static final float MARGEM_PADRAO = 40f;
    // Tamanho/posicao vem dos Controles (salvos no aparelho, tela de Settings).
    private float escala = 1f;
    private float MAX_DISTANCE = MAX_DISTANCE_BASE;
    private float DEADZONE = DEADZONE_BASE;
    /** Modo "mover joystick" (Settings): arrastar move a base em vez de andar. */
    private boolean editando = false;
    private float pegaX, pegaY; // onde o dedo pegou a base (modo edicao)
    private static final float IDLE_ALPHA = 0.4f;
    private static final float LIMIAR_DIRECAO = 0.35f;

    private final Image base;
    private final Image knob;
    private final Vector2 vetor = new Vector2();

    public Joystick(Stage stage, TextureRegion texBase, TextureRegion texKnob) {
        base = new Image(new TextureRegionDrawable(texBase));
        knob = new Image(new TextureRegionDrawable(texKnob));
        // 4x o tamanho cru da textura (64->170ish, 16->48) - mesma escala de
        // ampliacao que o Godot usa (Base real e' 256 em cima de um recorte
        // de 64px), mantendo o estilo pixel-art propositalmente "quadriculado"
        // em vez de suavizado. Nearest ja' vem do atlas (graphics.atlas, ver
        // WorldScreen/pack.json) - nao precisa mais setar aqui.
        // Canto inferior esquerdo, com uma margem - mesmo canto usado no
        // Joystick real de player.tscn (anchor bottom-left) - ou onde o
        // player arrastou (Controles).
        aplicarConfig();

        // Knob fica por cima da base no stage (adicionada depois - z-order
        // mais alto, ver addActor abaixo) e cobre bem o centro dela; sem
        // desabilitar o toque nela, um clique no meio (bem onde a bolinha
        // fica) acerta o knob (sem listener, nao faz nada) em vez de cair
        // pra base por baixo - so' os cantos (fora da area do knob) chegavam
        // a funcionar (achado pelo usuario testando).
        knob.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);

        base.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (editando) {
                    pegaX = x;
                    pegaY = y;
                    if (aoTocarEditando != null) aoTocarEditando.run();
                    return true;
                }
                atualizar(x, y);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (editando) {
                    moverBase(event.getStageX() - pegaX, event.getStageY() - pegaY);
                    return;
                }
                atualizar(x, y);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (editando) {
                    Controles.definirJoystickPosicao(base.getX(), base.getY());
                    return;
                }
                resetarKnob();
            }
        });

        stage.addActor(base);
        stage.addActor(knob);

        boolean mobile = Gdx.app.getType() == Application.ApplicationType.Android
            || Gdx.app.getType() == Application.ApplicationType.iOS;
        setVisivel(mobile);
    }

    /** x/y ja' chegam locais a base (origem no canto inferior-esquerdo dela,
     * Y crescendo pra cima - convencao padrao de InputListener no Scene2D). */
    private void atualizar(float x, float y) {
        base.getColor().a = 1f;
        float centerX = base.getWidth() / 2f;
        float centerY = base.getHeight() / 2f;
        float offX = x - centerX;
        float offY = y - centerY;
        float dist = (float) Math.sqrt(offX * offX + offY * offY);
        if (dist > MAX_DISTANCE) {
            offX = offX / dist * MAX_DISTANCE;
            offY = offY / dist * MAX_DISTANCE;
            dist = MAX_DISTANCE;
        }
        knob.setPosition(base.getX() + centerX + offX - knob.getWidth() / 2f,
            base.getY() + centerY + offY - knob.getHeight() / 2f);
        if (dist < DEADZONE) {
            vetor.set(0f, 0f);
        } else {
            vetor.set(offX / MAX_DISTANCE, offY / MAX_DISTANCE);
        }
    }

    /** Le tamanho e posicao salvos (Controles) e reposiciona. */
    public void aplicarConfig() {
        escala = Controles.joystickEscala();
        MAX_DISTANCE = MAX_DISTANCE_BASE * escala;
        DEADZONE = DEADZONE_BASE * escala;
        base.setSize(TAM_BASE * escala, TAM_BASE * escala);
        knob.setSize(TAM_KNOB * escala, TAM_KNOB * escala);
        float x = Controles.joystickX(), y = Controles.joystickY();
        if (x < 0f || y < 0f) { x = MARGEM_PADRAO; y = MARGEM_PADRAO; }
        moverBase(x, y);
    }

    /** Move a base (presa dentro da tela) e centraliza o knob nela. */
    private void moverBase(float x, float y) {
        com.badlogic.gdx.scenes.scene2d.Stage stage = base.getStage();
        if (stage != null) {
            x = Math.max(0f, Math.min(x, stage.getWidth() - base.getWidth()));
            y = Math.max(0f, Math.min(y, stage.getHeight() - base.getHeight()));
        }
        base.setPosition(x, y);
        resetarKnob();
    }

    /** Liga/desliga o modo "mover joystick" (arrastar move, nao anda). */
    public void setEditando(boolean editando) {
        this.editando = editando;
        resetarKnob();
        if (editando) base.getColor().a = 1f;
    }

    public boolean isEditando() { return editando; }

    /** Editor de controles: avisa quando tocam no joystick (pra seleciona-lo). */
    private Runnable aoTocarEditando;
    public void setAoTocarEditando(Runnable r) { aoTocarEditando = r; }

    /** Destaque (ciano) na base e no knob quando esta selecionado no editor. */
    public void setSelecionado(boolean sel) {
        com.badlogic.gdx.graphics.Color cor = sel ? HotbarUI.COR_EDICAO : com.badlogic.gdx.graphics.Color.WHITE;
        base.setColor(cor);
        knob.setColor(cor);
        if (!editando) base.getColor().a = IDLE_ALPHA;
    }

    private void resetarKnob() {
        base.getColor().a = editando ? 1f : IDLE_ALPHA;
        vetor.set(0f, 0f);
        float centerX = base.getWidth() / 2f;
        float centerY = base.getHeight() / 2f;
        knob.setPosition(base.getX() + centerX - knob.getWidth() / 2f, base.getY() + centerY - knob.getHeight() / 2f);
    }

    // 8 direcoes: o angulo do knob cai num de 8 setores de 45 graus
    // (0 = direita, 1 = cima-direita, 2 = cima, ... 7 = baixo-direita). Na
    // diagonal os dois eixos ficam ativos e o WorldScreen anda na diagonal.
    private int setor() {
        if (vetor.len2() < LIMIAR_DIRECAO * LIMIAR_DIRECAO) return -1;
        float ang = com.badlogic.gdx.math.MathUtils.atan2(vetor.y, vetor.x) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
        return ((Math.round(ang / 45f) % 8) + 8) % 8;
    }

    // Moving style "4 directions" (Controles): so' o eixo dominante, sem diagonal.
    private boolean ativo() { return vetor.len2() >= LIMIAR_DIRECAO * LIMIAR_DIRECAO; }
    private boolean horizontalDomina() { return Math.abs(vetor.x) >= Math.abs(vetor.y); }

    public boolean isDireita() {
        if (!Controles.oitoDirecoes()) return ativo() && horizontalDomina() && vetor.x > 0;
        int s = setor(); return s == 7 || s == 0 || s == 1;
    }
    public boolean isCima() {
        if (!Controles.oitoDirecoes()) return ativo() && !horizontalDomina() && vetor.y > 0;
        int s = setor(); return s == 1 || s == 2 || s == 3;
    }
    public boolean isEsquerda() {
        if (!Controles.oitoDirecoes()) return ativo() && horizontalDomina() && vetor.x < 0;
        int s = setor(); return s == 3 || s == 4 || s == 5;
    }
    public boolean isBaixo() {
        if (!Controles.oitoDirecoes()) return ativo() && !horizontalDomina() && vetor.y < 0;
        int s = setor(); return s == 5 || s == 6 || s == 7;
    }

    public void setVisivel(boolean visivel) {
        base.setVisible(visivel);
        knob.setVisible(visivel);
        base.setTouchable(visivel ? com.badlogic.gdx.scenes.scene2d.Touchable.enabled : com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
    }
}
