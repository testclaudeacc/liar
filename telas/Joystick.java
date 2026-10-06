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
    private static final float MAX_DISTANCE = 100f;
    private static final float DEADZONE = 20f;
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
        base.setSize(200, 200);
        knob.setSize(56, 56);
        // Canto inferior esquerdo, com uma margem - mesmo canto usado no
        // Joystick real de player.tscn (anchor bottom-left).
        base.setPosition(40, 40);
        resetarKnob();

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
                atualizar(x, y);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                atualizar(x, y);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
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

    private void resetarKnob() {
        base.getColor().a = IDLE_ALPHA;
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

    public boolean isDireita() { int s = setor(); return s == 7 || s == 0 || s == 1; }
    public boolean isCima() { int s = setor(); return s == 1 || s == 2 || s == 3; }
    public boolean isEsquerda() { int s = setor(); return s == 3 || s == 4 || s == 5; }
    public boolean isBaixo() { int s = setor(); return s == 5 || s == 6 || s == 7; }

    public void setVisivel(boolean visivel) {
        base.setVisible(visivel);
        knob.setVisible(visivel);
        base.setTouchable(visivel ? com.badlogic.gdx.scenes.scene2d.Touchable.enabled : com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
    }
}
