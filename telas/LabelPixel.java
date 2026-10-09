package com.teste.game.telas;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;

/** Label desenhado com a origem presa no pixel de TELA. A fonte e' gerada no
 * tamanho real da tela (UiSkin.gerarFonte), entao sem escala e com a origem
 * inteira cada letra cai certinho num pixel - nitido (fora disso a letra cai
 * entre 2 pixels e fica embacada). */
public class LabelPixel extends Label {
    private final Vector2 tmp = new Vector2();

    public LabelPixel(CharSequence texto, LabelStyle estilo) { super(texto, estilo); }

    /** Quanto mover (unidades do stage) pra (x, y) do stage cair num pixel de tela. */
    static float ajuste(Stage stage, float v, boolean eixoX) {
        if (stage == null) return 0f;
        float k = eixoX ? stage.getViewport().getScreenWidth() / stage.getViewport().getWorldWidth()
                        : stage.getViewport().getScreenHeight() / stage.getViewport().getWorldHeight();
        if (!(k > 0f)) return 0f;
        return Math.round(v * k) / k - v;
    }

    @Override public void draw(Batch batch, float parentAlpha) {
        float x = getX(), y = getY();
        Vector2 p = localToStageCoordinates(tmp.set(0, 0));
        setPosition(x + ajuste(getStage(), p.x, true), y + ajuste(getStage(), p.y, false));
        super.draw(batch, parentAlpha);
        setPosition(x, y);
    }
}
