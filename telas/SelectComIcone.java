package com.teste.game.telas;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

import java.util.Map;

/** Dropdown com um icone na frente de cada opcao (ranking, idioma com bandeira). */
public class SelectComIcone extends SelectSeta {
    private final Map<String, TextureRegion> icones;

    public SelectComIcone(Skin skin, String estilo, Map<String, TextureRegion> icones) {
        super(skin, estilo);
        this.icones = icones;
    }

    static com.badlogic.gdx.graphics.g2d.GlyphLayout desenhar(Map<String, TextureRegion> icones,
            com.badlogic.gdx.graphics.g2d.Batch batch, com.badlogic.gdx.graphics.g2d.BitmapFont fonte,
            String item, float x, float y, float largura) {
        TextureRegion icone = icones != null ? icones.get(item) : null;
        com.badlogic.gdx.graphics.g2d.GlyphLayout texto = new com.badlogic.gdx.graphics.g2d.GlyphLayout(fonte, item);
        float tam = fonte.getLineHeight() * 0.9f, espaco = icone != null ? tam * 0.3f : 0f;
        float total = (icone != null ? tam : 0f) + espaco + texto.width;
        float inicio = x + Math.max(0f, (largura - total) / 2f);
        if (icone != null) {
            // y e' o topo do texto: centraliza o icone na altura das letras.
            float meio = y - fonte.getCapHeight() / 2f;
            com.badlogic.gdx.graphics.Color antes = new com.badlogic.gdx.graphics.Color(batch.getColor());
            batch.setColor(1f, 1f, 1f, antes.a);
            batch.draw(icone, inicio, meio - tam / 2f, tam, tam);
            batch.setColor(antes);
        }
        return fonte.draw(batch, item, inicio + (icone != null ? tam : 0f) + espaco, y);
    }

    @Override protected com.badlogic.gdx.graphics.g2d.GlyphLayout drawItem(com.badlogic.gdx.graphics.g2d.Batch batch,
            com.badlogic.gdx.graphics.g2d.BitmapFont fonte, String item, float x, float y, float largura) {
        return desenhar(icones, batch, fonte, item, x, y, largura);
    }

    @Override protected SelectBoxScrollPane<String> newScrollPane() {
        return new SelectBoxScrollPane<String>(this) {
            @Override protected com.badlogic.gdx.scenes.scene2d.ui.List<String> newList() {
                return new com.badlogic.gdx.scenes.scene2d.ui.List<String>(SelectComIcone.this.getStyle().listStyle) {
                    @Override public String toString(String item) { return item; }
                    @Override protected com.badlogic.gdx.graphics.g2d.GlyphLayout drawItem(com.badlogic.gdx.graphics.g2d.Batch batch,
                            com.badlogic.gdx.graphics.g2d.BitmapFont fonte, int indice, String item, float x, float y, float largura) {
                        return desenhar(icones, batch, fonte, item, x, y, largura);
                    }
                };
            }
        };
    }
}
