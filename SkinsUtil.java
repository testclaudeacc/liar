package com.teste.game.telas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers de skin compartilhados entre o mundo (WorldScreen) e a aba Vanity
 * (BookMenuUI). Formato das skins (igual servidor.py::validar_skins):
 * {categoria: {nome, caminho: "res://sprites/.../X.png", cor: "rrggbbaa"}}.
 */
public final class SkinsUtil {

    /** Ordem de desenho das camadas (de baixo pra cima). */
    public static final String[] ORDEM_CAMADAS = {"base", "body", "helm", "acc"};
    public static final String BASE_PADRAO = "res://sprites/base/BaseSoul.png";
    public static final int FRAME_LARGURA = 16;
    // Indices na spritesheet: cima 0-2, baixo 3-5, esquerda 6-7, direita 8-9.
    public static final int FRAME_CIMA = 0, FRAME_BAIXO = 3, FRAME_ESQUERDA = 6, FRAME_DIREITA = 8;

    private SkinsUtil() {}

    /** "res://sprites/hair/FemHair1.png" -> regiao "sprites/hair/FemHair1" do atlas. */
    public static TextureAtlas.AtlasRegion regiao(TextureAtlas atlas, String caminho) {
        if (caminho == null || caminho.isEmpty()) return null;
        String nome = caminho.startsWith("res://") ? caminho.substring(6) : caminho;
        int ponto = nome.lastIndexOf('.');
        if (ponto > nome.lastIndexOf('/')) nome = nome.substring(0, ponto);
        return atlas.findRegion(nome);
    }

    public static TextureRegion quadro(TextureRegion tira, int indice) {
        return new TextureRegion(tira, indice * FRAME_LARGURA, 0, FRAME_LARGURA, tira.getRegionHeight());
    }

    public static Color cor(String hex) {
        if (hex == null) return new Color(Color.WHITE);
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6 && h.length() != 8) return new Color(Color.WHITE);
        try {
            return Color.valueOf(h);
        } catch (RuntimeException e) {
            return new Color(Color.WHITE);
        }
    }

    public static String hex(Color cor) {
        return cor.toString(); // rrggbbaa
    }

    public static String caminho(JsonValue skins, String categoria) {
        JsonValue item = skins == null ? null : skins.get(categoria);
        return item == null || !item.isObject() ? null : item.getString("caminho", null);
    }

    public static String corHex(JsonValue skins, String categoria) {
        JsonValue item = skins == null ? null : skins.get(categoria);
        return item == null || !item.isObject() ? "ffffffff" : item.getString("cor", "ffffffff");
    }

    /**
     * Boneco montado com as camadas de skin (pele, roupa, cabelo, costas) num
     * quadro so' (ex: FRAME_BAIXO = parado de frente). Todas as camadas na
     * mesma escala e ancoradas nos pes, igual o mundo desenha - por isso um
     * Widget proprio em vez de varias Image num Stack (cada Image escalaria
     * sozinha conforme a altura da propria tira).
     */
    public static class Preview extends Widget {
        private final List<TextureRegion> quadros = new ArrayList<>();
        private final List<Color> cores = new ArrayList<>();
        private float alturaMaxima = 17f;

        public Preview(TextureAtlas atlas, JsonValue skins, int frame) {
            for (String cat : ORDEM_CAMADAS) {
                String caminho = caminho(skins, cat);
                if (caminho == null && "base".equals(cat)) caminho = BASE_PADRAO;
                TextureRegion tira = regiao(atlas, caminho);
                if (tira == null) continue;
                quadros.add(quadro(tira, frame));
                cores.add("base".equals(cat) ? new Color(Color.WHITE) : cor(corHex(skins, cat)));
                alturaMaxima = Math.max(alturaMaxima, tira.getRegionHeight());
            }
        }

        @Override public float getPrefWidth() { return FRAME_LARGURA; }
        @Override public float getPrefHeight() { return alturaMaxima; }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            validate();
            if (quadros.isEmpty()) return;
            float escala = Math.min(getWidth() / FRAME_LARGURA, getHeight() / alturaMaxima);
            float largura = FRAME_LARGURA * escala;
            float x = getX() + (getWidth() - largura) / 2f;
            float y = getY() + (getHeight() - alturaMaxima * escala) / 2f;
            Color anterior = new Color(batch.getColor());
            Color propria = getColor();
            for (int i = 0; i < quadros.size(); i++) {
                Color c = cores.get(i);
                batch.setColor(c.r * propria.r, c.g * propria.g, c.b * propria.b, c.a * propria.a * parentAlpha);
                TextureRegion q = quadros.get(i);
                batch.draw(q, x, y, largura, q.getRegionHeight() * escala);
            }
            batch.setColor(anterior);
        }
    }
}
