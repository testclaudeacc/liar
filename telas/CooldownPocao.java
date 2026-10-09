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
        private static final Color FUNDO = new Color(0.08f, 0.08f, 0.08f, 0.72f);
        private final BitmapFont fonte;
        private final float escalaFonte;
        private final GlyphLayout layout = new GlyphLayout();

        public Sobreposicao(BitmapFont fonte, float escalaFonte) {
            this.fonte = fonte;
            this.escalaFonte = escalaFonte;
            setTouchable(Touchable.disabled);
        }

        @Override public void draw(Batch batch, float parentAlpha) {
            float falta = restante();
            if (falta <= 0f) return;
            Color antes = new Color(batch.getColor());
            batch.setColor(FUNDO.r, FUNDO.g, FUNDO.b, FUNDO.a * parentAlpha);
            batch.draw(pixel(), getX(), getY(), getWidth(), getHeight());
            batch.setColor(antes);
            float escalaX = fonte.getData().scaleX, escalaY = fonte.getData().scaleY;
            fonte.getData().setScale(escalaX * escalaFonte, escalaY * escalaFonte);
            fonte.setColor(1f, 1f, 1f, parentAlpha);
            layout.setText(fonte, String.valueOf((int) Math.ceil(falta)));
            fonte.draw(batch, layout, getX() + (getWidth() - layout.width) / 2f, getY() + (getHeight() + layout.height) / 2f);
            fonte.getData().setScale(escalaX, escalaY);
            fonte.setColor(Color.WHITE);
        }
    }
}
