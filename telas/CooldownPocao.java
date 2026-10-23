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

    // Scroll de teleporte: cooldown proprio (servidor.py::SCROLL_COOLDOWN_SEG).
    private static long fimScrollNanos = 0L;

    public static void iniciarScroll(float segundos) {
        fimScrollNanos = System.nanoTime() + (long) (segundos * 1_000_000_000L);
    }

    public static float restanteScroll() {
        return Math.max(0f, (fimScrollNanos - System.nanoTime()) / 1_000_000_000f);
    }

    // Magias: cada uma tem o seu cooldown (servidor.py::_lancar_magia "spell_cd").
    private static final java.util.Map<String, Long> fimMagias = new java.util.HashMap<>();

    public static void iniciarMagia(String magia, float segundos) {
        fimMagias.put(magia, System.nanoTime() + (long) (segundos * 1_000_000_000L));
    }

    public static float restanteMagia(String magia) {
        Long fim = magia == null ? null : fimMagias.get(magia);
        return fim == null ? 0f : Math.max(0f, (fim - System.nanoTime()) / 1_000_000_000f);
    }

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

    private static Texture circulo;

    /** Circulo branco cheio (alta resolucao, borda suave) pro slot redondo do celular. */
    private static Texture circulo() {
        if (circulo == null) {
            int n = 128;
            Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
            pm.setBlending(Pixmap.Blending.None);
            float c = (n - 1) / 2f, r = n / 2f;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    float d = (float) Math.hypot(x - c, y - c);
                    float a = Math.max(0f, Math.min(1f, r - d)); // 1px de borda suave
                    pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a));
                }
            }
            circulo = new Texture(pm);
            circulo.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            pm.dispose();
        }
        return circulo;
    }

    /** Camada por cima do icone de uma pocao (ocupa o slot inteiro). */
    public static final class Sobreposicao extends Widget {
        // Bem escuro, mas ainda deixa ver a pocao por baixo.
        private static final Color FUNDO = new Color(0.02f, 0.02f, 0.02f, 0.82f);
        private final BitmapFont fonte;
        private final GlyphLayout layout = new GlyphLayout();
        private final boolean scroll;
        private final String magia; // != null: cooldown dessa magia
        private boolean redondo; // slot redondo (hotbar do celular): escurece um circulo, nao um quadrado

        public Sobreposicao redondo(boolean r) { this.redondo = r; return this; }

        public Sobreposicao(BitmapFont fonte) { this(fonte, false); }

        /** scroll: usa o cooldown do scroll em vez do das pocoes. */
        public Sobreposicao(BitmapFont fonte, boolean scroll) {
            this.fonte = fonte;
            this.scroll = scroll;
            this.magia = null;
            setTouchable(Touchable.disabled);
        }

        /** Cooldown de uma magia (caminho dela no item_db). */
        public Sobreposicao(BitmapFont fonte, String magia) {
            this.fonte = fonte;
            this.scroll = false;
            this.magia = magia;
            setTouchable(Touchable.disabled);
        }

        @Override public void draw(Batch batch, float parentAlpha) {
            float falta = magia != null ? restanteMagia(magia) : scroll ? restanteScroll() : restante();
            if (falta <= 0f) return;
            Color antes = new Color(batch.getColor());
            batch.setColor(FUNDO.r, FUNDO.g, FUNDO.b, FUNDO.a * parentAlpha);
            if (redondo) {
                // Um pouco pra dentro: a borda do botao continua aparecendo.
                float m = Math.min(getWidth(), getHeight()) * 0.04f;
                float lado = Math.min(getWidth(), getHeight()) - m * 2f;
                batch.draw(circulo(), getX() + (getWidth() - lado) / 2f, getY() + (getHeight() - lado) / 2f, lado, lado);
            } else {
                batch.draw(pixel(), getX(), getY(), getWidth(), getHeight());
            }
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
