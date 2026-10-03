package com.teste.game.telas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

/**
 * Barras de HP e MP do player local, no canto superior esquerdo (igual o HUD
 * do Godot: icone + barra com "atual/maximo"). Os valores vem do servidor
 * (sync_local_player, sync_stats, sync_vitals, player_damaged).
 */
public final class HudVitais {

    private static final float LARGURA_BARRA = 150f;
    private static final float ALTURA_BARRA = 18f;

    private final Barra hp;
    private final Barra mp;
    private final Texture pixel;
    private final Table raiz = new Table();
    private float hpAtual = 1f, hpMax = 1f, mpAtual = 1f, mpMax = 1f;

    private static final class Barra {
        final Image preenchimento;
        final Table alinhador;
        final Label texto;
        Barra(Image preenchimento, Table alinhador, Label texto) {
            this.preenchimento = preenchimento;
            this.alinhador = alinhador;
            this.texto = texto;
        }
    }

    public HudVitais(Stage stage, Skin skin, TextureAtlas atlas) {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        hp = criarBarra(skin, Color.valueOf("c40000"));
        mp = criarBarra(skin, Color.valueOf("5f9dff"));

        raiz.setFillParent(true);
        raiz.top().left().pad(10);
        adicionarLinha(atlas, "ui/HPIcon", hp);
        adicionarLinha(atlas, "ui/MPIcon", mp);
        stage.addActor(raiz);
        atualizar();
    }

    private Barra criarBarra(Skin skin, Color cor) {
        Image preenchimento = new Image(new TextureRegionDrawable(new TextureRegion(pixel)));
        preenchimento.setColor(cor);
        Table alinhador = new Table();
        alinhador.left().pad(1);
        alinhador.add(preenchimento).size(0f, ALTURA_BARRA - 2f);
        Label texto = new Label("", skin, "hud");
        texto.setFontScale(0.8f);
        texto.setAlignment(Align.center);
        return new Barra(preenchimento, alinhador, texto);
    }

    private void adicionarLinha(TextureAtlas atlas, String icone, Barra barra) {
        TextureAtlas.AtlasRegion regiao = atlas.findRegion(icone);
        if (regiao != null) {
            Image img = new Image(new TextureRegionDrawable(regiao));
            img.setScaling(Scaling.fit);
            raiz.add(img).size(20).padRight(4);
        } else {
            raiz.add().size(20).padRight(4);
        }
        Table fundo = new Table();
        fundo.setBackground(UiSkin.retangulo(new Color(0.06f, 0.06f, 0.06f, 0.9f),
            new Color(0.3f, 0.3f, 0.3f, 1f), 1));
        Stack pilha = new Stack(fundo, barra.alinhador, barra.texto);
        raiz.add(pilha).size(LARGURA_BARRA, ALTURA_BARRA).padBottom(4).row();
    }

    /** Valores negativos = "nao mudou". */
    public void definir(float hpAtual, float hpMax, float mpAtual, float mpMax) {
        if (hpMax > 0f) this.hpMax = hpMax;
        if (mpMax > 0f) this.mpMax = mpMax;
        if (hpAtual >= 0f) this.hpAtual = hpAtual;
        if (mpAtual >= 0f) this.mpAtual = mpAtual;
        atualizar();
    }

    public float hpAtual() { return hpAtual; }
    public float hpMax() { return hpMax; }

    private void atualizar() {
        preencher(hp, hpAtual, hpMax);
        preencher(mp, mpAtual, mpMax);
    }

    private static void preencher(Barra barra, float atual, float max) {
        float pct = max > 0f ? Math.max(0f, Math.min(1f, atual / max)) : 0f;
        barra.alinhador.getCell(barra.preenchimento).width((LARGURA_BARRA - 2f) * pct);
        barra.alinhador.invalidateHierarchy();
        barra.texto.setText(Math.round(atual) + "/" + Math.round(max));
    }

    public void setVisivel(boolean visivel) {
        raiz.setVisible(visivel);
    }

    public void dispose() {
        pixel.dispose();
    }
}
