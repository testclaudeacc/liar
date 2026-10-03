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
 * HUD do canto superior esquerdo (prints/tela desejada barras.png):
 * painel escuro com HP (coracao + barra vermelha) e MP (barra azul), texto
 * "atual/max" centralizado; embaixo, painel menor com a barra de XP (% ate o
 * proximo level). Valores vindos do servidor (sync_local_player, sync_stats,
 * sync_vitals, player_damaged).
 */
public final class HudVitais {

    private static final float LARGURA_BARRA = 222f;
    private static final float ALTURA_BARRA = 24f;
    private static final float LARGURA_XP = 162f;
    private static final float ALTURA_XP = 20f;

    private static final Color COR_PAINEL = new Color(0.17f, 0.17f, 0.17f, 0.95f);
    private static final Color COR_BORDA = new Color(0.24f, 0.24f, 0.24f, 1f);
    private static final Color COR_FUNDO_BARRA = new Color(0.07f, 0.07f, 0.07f, 1f);
    private static final Color COR_HP = Color.valueOf("c40000");
    private static final Color COR_MP = Color.valueOf("5f9dff");
    private static final Color COR_XP = Color.valueOf("e8b020");

    private final Barra hp;
    private final Barra mp;
    private final Barra xp;
    private final Texture pixel;
    private final Table raiz = new Table();
    private float hpAtual = 1f, hpMax = 1f, mpAtual = 1f, mpMax = 1f;

    private static final class Barra {
        final Image preenchimento;
        final Table alinhador;
        final Label texto;
        final float largura;
        Barra(Image preenchimento, Table alinhador, Label texto, float largura) {
            this.preenchimento = preenchimento;
            this.alinhador = alinhador;
            this.texto = texto;
            this.largura = largura;
        }
    }

    public HudVitais(Stage stage, Skin skin, TextureAtlas atlas) {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        hp = criarBarra(skin, COR_HP, LARGURA_BARRA, ALTURA_BARRA, 0.8f);
        mp = criarBarra(skin, COR_MP, LARGURA_BARRA, ALTURA_BARRA, 0.8f);
        xp = criarBarra(skin, COR_XP, LARGURA_XP, ALTURA_XP, 0.7f);

        // Painel de HP + MP.
        Table vitais = new Table();
        vitais.setBackground(UiSkin.retangulo(COR_PAINEL, COR_BORDA, 2));
        vitais.pad(3);
        adicionarLinha(vitais, atlas, "ui/HPIcon", hp, ALTURA_BARRA, 28f);
        vitais.row();
        adicionarLinha(vitais, atlas, "ui/MPIcon", mp, ALTURA_BARRA, 28f);

        // Painel de XP, logo abaixo (icone dentro da propria barra escura).
        Table painelXp = new Table();
        painelXp.setBackground(UiSkin.retangulo(COR_FUNDO_BARRA, COR_BORDA, 1));
        adicionarLinha(painelXp, atlas, "ui/XPIcon", xp, ALTURA_XP, 18f);

        raiz.setFillParent(true);
        raiz.top().left().pad(10);
        raiz.add(vitais).left().row();
        raiz.add(painelXp).left().padTop(5);
        stage.addActor(raiz);
        atualizar();
        definirXp(1, 0);
    }

    private Barra criarBarra(Skin skin, Color cor, float largura, float altura, float escalaTexto) {
        Image preenchimento = new Image(new TextureRegionDrawable(new TextureRegion(pixel)));
        preenchimento.setColor(cor);
        Table alinhador = new Table();
        alinhador.left();
        alinhador.add(preenchimento).size(0f, altura);
        // Fonte "default" (branca com contorno preto), igual o texto da print.
        Label texto = new Label("", skin, "default");
        texto.setFontScale(escalaTexto);
        texto.setAlignment(Align.center);
        return new Barra(preenchimento, alinhador, texto, largura);
    }

    private void adicionarLinha(Table pai, TextureAtlas atlas, String icone, Barra barra, float altura, float tamanhoIcone) {
        TextureAtlas.AtlasRegion regiao = atlas.findRegion(icone);
        if (regiao != null) {
            Image img = new Image(new TextureRegionDrawable(regiao));
            img.setScaling(Scaling.fit);
            pai.add(img).size(tamanhoIcone).pad(1, 2, 1, 3);
        } else {
            pai.add().size(tamanhoIcone).pad(1, 2, 1, 3);
        }
        Table fundo = new Table();
        fundo.setBackground(new TextureRegionDrawable(new TextureRegion(pixel)).tint(COR_FUNDO_BARRA));
        Stack pilha = new Stack(fundo, barra.alinhador, barra.texto);
        pai.add(pilha).size(barra.largura, altura).pad(1, 0, 1, 1);
    }

    /** Valores negativos = "nao mudou". */
    public void definir(float hpAtual, float hpMax, float mpAtual, float mpMax) {
        if (hpMax > 0f) this.hpMax = hpMax;
        if (mpMax > 0f) this.mpMax = mpMax;
        if (hpAtual >= 0f) this.hpAtual = hpAtual;
        if (mpAtual >= 0f) this.mpAtual = mpAtual;
        atualizar();
    }

    /** Barra de XP: % do level atual ate o proximo (mesma formula do servidor,
     * servidor.py::get_exp_for_level). */
    public void definirXp(int level, long exp) {
        long atual = expParaLevel(level), proximo = expParaLevel(level + 1);
        float pct = proximo > atual ? (float) (exp - atual) / (proximo - atual) : 0f;
        pct = Math.max(0f, Math.min(1f, pct));
        preencher(xp, pct);
        xp.texto.setText(String.format(java.util.Locale.US, "%.2f%%", pct * 100f));
    }

    private static long expParaLevel(int level) {
        if (level <= 1) return 0;
        return (long) ((50.0 / 3.0) * (Math.pow(level, 3) - 6 * Math.pow(level, 2) + 17 * level - 12));
    }

    public float hpAtual() { return hpAtual; }
    public float hpMax() { return hpMax; }

    private void atualizar() {
        preencher(hp, hpMax > 0f ? hpAtual / hpMax : 0f);
        hp.texto.setText(Math.round(hpAtual) + "/" + Math.round(hpMax));
        preencher(mp, mpMax > 0f ? mpAtual / mpMax : 0f);
        mp.texto.setText(Math.round(mpAtual) + "/" + Math.round(mpMax));
    }

    private static void preencher(Barra barra, float pct) {
        pct = Math.max(0f, Math.min(1f, pct));
        barra.alinhador.getCell(barra.preenchimento).width(barra.largura * pct);
        barra.alinhador.invalidateHierarchy();
    }

    public void setVisivel(boolean visivel) {
        raiz.setVisible(visivel);
    }

    public void dispose() {
        pixel.dispose();
    }
}
