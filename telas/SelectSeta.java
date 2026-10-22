package com.teste.game.telas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

/**
 * Dropdown (estilo "zoom-select") com a setinha no canto direito: aponta pra
 * baixo fechado e GIRA suave pro lado (direita) enquanto a lista esta aberta.
 * O espaco da seta vem do fundo do estilo (UiSkin.comSeta).
 */
public class SelectSeta extends SelectBox<String> {
    private static Texture seta; // triangulo branco 9x5 apontando pra baixo
    private static final Color COR = new Color(0.85f, 0.85f, 0.85f, 1f);
    private static final float VELOCIDADE = 14f; // quanto mais, mais rapido gira
    private float angulo = 0f; // 0 = pra baixo, 90 = pra direita (aberto)

    public SelectSeta(Skin skin, String estilo) { super(skin, estilo); }

    private static Texture textura() {
        if (seta == null) {
            Pixmap pm = new Pixmap(9, 5, Pixmap.Format.RGBA8888);
            pm.setColor(Color.WHITE);
            for (int linha = 0; linha < 5; linha++) pm.drawLine(linha, linha, 8 - linha, linha); // linha 0 = topo (larga)
            seta = new Texture(pm);
            pm.dispose();
        }
        return seta;
    }

    @Override public void act(float delta) {
        super.act(delta);
        float alvo = getScrollPane().hasParent() ? 90f : 0f;
        angulo += (alvo - angulo) * Math.min(1f, delta * VELOCIDADE);
        if (Math.abs(alvo - angulo) < 0.5f) angulo = alvo;
    }

    @Override public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        Color antes = batch.getColor().cpy();
        batch.setColor(COR.r, COR.g, COR.b, COR.a * parentAlpha * getColor().a);
        float cx = getX() + getWidth() - 14f, cy = getY() + getHeight() / 2f;
        // Rotacao do Batch e' anti-horaria: 90 graus leva a seta de "baixo" pra "direita".
        batch.draw(textura(), cx - 4.5f, cy - 2.5f, 4.5f, 2.5f, 9f, 5f, 1f, 1f, angulo, 0, 0, 9, 5, false, false);
        batch.setColor(antes);
    }
}
