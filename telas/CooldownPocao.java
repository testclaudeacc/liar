package com.teste.game.telas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;

/** Cooldown das pocoes (todas juntas): usou uma, TODAS ficam travadas por
 * DURACAO segundos (servidor.py::POCAO_COOLDOWN_SEG). Qualquer slot de pocao
 * (bag, hotbar, botoes do celular...) poe uma Sobreposicao por cima do icone:
 * cinza escuro + segundos que faltam, enquanto o cooldown corre. */
public final class CooldownPocao {

    public static final float DURACAO = 5f;
    private static long fimNanos = 0L;
    private static Texture pixel;

    private CooldownPocao() {}

    public static void iniciar(float segundos) {
        fimNanos = System.nanoTime() + (long) (segundos * 1_000_000_000L);
    }

    public static float restante() {
        return Math.max(0f, (fimNanos - System.nanoTime()) / 1_000_000_000f);
    }

    public static boolean ativo() { return restante() > 0f; }

    private static Texture pixel() {
        if (pixel == null) {
            Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pm.setColor(Color.WHITE);
            pm.fill();
            pixel = new Texture(pm);
            pm.dispose();
        }
        return pixel;
    }

    /** Camada por cima do icone de uma pocao (ocupa o slot inteiro). */
    public static final class Sobreposicao extends Widget {
        // Bem escuro, mas ainda deixa ver a pocao por baixo.
        private static final Color FUNDO = new Color(0.02f, 0.02f, 0.02f, 0.82f);
        private final BitmapFont fonte;
        private final GlyphLayout layout = new GlyphLayout();

        public Sobreposicao(BitmapFont fonte) {
            this.fonte = fonte;
            setTouchable(Touchable.disabled);
        }

        @Override public void draw(Batch batch, float parentAlpha) {
            float falta = restante();
            if (falta <= 0f) return;
            Color antes = new Color(batch.getColor());
            batch.setColor(FUNDO.r, FUNDO.g, FUNDO.b, FUNDO.a * parentAlpha);
            batch.draw(pixel(), getX(), getY(), getWidth(), getHeight());
            batch.setColor(antes);
            fonte.setColor(1f, 1f, 1f, parentAlpha);
            layout.setText(fonte, String.valueOf((int) Math.ceil(falta)));
            // Numero no pixel exato da tela (nitido), centralizado no slot.
            float tx = getX() + (getWidth() - layout.width) / 2f, ty = getY() + (getHeight() + layout.height) / 2f;
            com.badlogic.gdx.math.Vector2 p = localToStageCoordinates(new com.badlogic.gdx.math.Vector2(tx - getX(), ty - getY()));
            tx += LabelPixel.ajuste(getStage(), p.x, true);
            ty += LabelPixel.ajuste(getStage(), p.y, false);
            fonte.draw(batch, layout, tx, ty);
            fonte.setColor(Color.WHITE);
        }
    }
}
