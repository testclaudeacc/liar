package com.teste.game.telas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

/**
 * HUD do canto superior esquerdo, medido pixel a pixel em
 * prints/tela desejada barras.png (1366x768):
 *   painel: borda 2px (83,83,83), fundo (48,48,48); coracao + barra de HP
 *   (198,0,0) e mana + barra de MP (96,153,255), 314x34 cada, com 1px de
 *   sombra (45,45,45) a direita/embaixo e 5px entre elas;
 *   XP: painel 262x34, borda 1px preta, fundo (14,14,14), icone a esquerda
 *   e "%" centralizado no painel todo.
 * Texto: Tahoma Bold branca com contorno preto de 2px.
 * Todas as medidas sao em PIXEL DE TELA e convertidas pra unidade do stage
 * (px()), senao bordas de 1-2px saem com espessura quebrada.
 */
public final class HudVitais {

    private static final Color COR_BORDA_PAINEL = new Color(83 / 255f, 83 / 255f, 83 / 255f, 1f);
    private static final Color COR_PAINEL = new Color(48 / 255f, 48 / 255f, 48 / 255f, 1f);
    private static final Color COR_SOMBRA = new Color(45 / 255f, 45 / 255f, 45 / 255f, 1f);
    private static final Color COR_HP = new Color(198 / 255f, 0f, 0f, 1f);
    private static final Color COR_MP = new Color(96 / 255f, 153 / 255f, 1f, 1f);
    private static final Color COR_XP_FUNDO = new Color(14 / 255f, 14 / 255f, 14 / 255f, 1f);
    private static final Color COR_XP = Color.valueOf("e8b020");

    private final float escala; // pixels de tela por unidade do stage
    // Tudo aqui e' medido em pixels de tela (da print do PC); no celular, com
    // tela de alta densidade, isso ficava pequeno demais - aumenta tudo junto.
    private static final float FATOR_MOBILE = 1.7f;
    private static final boolean MOBILE = com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
        || com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
    private static final float FATOR = MOBILE ? FATOR_MOBILE : 1f;
    private static final float FATOR_TEXTO = MOBILE ? 1.35f : 1f;
    private final Texture pixel;
    private final BitmapFont fonte;
    private final Table raiz = new Table();
    private final Barra hp, mp, xp;
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

    /** escala: a mesma usada pra gerar as fontes do skin (pixels reais / unidade do stage). */
    public HudVitais(Stage stage, TextureAtlas atlas, float escala) {
        this.escala = escala;
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
        fonte = gerarFonte(escala);
        Label.LabelStyle estilo = new Label.LabelStyle(fonte, Color.WHITE);

        hp = criarBarra(estilo, COR_HP, 314);
        mp = criarBarra(estilo, COR_MP, 314);

        // ---- Painel HP/MP ----
        Table interno = new Table();
        interno.setBackground(cor(COR_PAINEL));
        interno.pad(px(3), 0, px(2), px(3));
        adicionarLinhaVital(interno, atlas, "ui/HPIcon", hp);
        interno.row();
        adicionarLinhaVital(interno, atlas, "ui/MPIcon", mp).padTop(px(5));
        Table painel = new Table();
        painel.setBackground(cor(COR_BORDA_PAINEL));
        // Borda: 2px em cima, 1px nos outros lados (medido na print).
        painel.pad(px(2), px(1), px(1), px(1));
        painel.add(interno);

        // ---- Painel XP ----
        Image preenchXp = new Image(cor(COR_XP));
        Table alinhaXp = new Table();
        alinhaXp.left();
        alinhaXp.add(preenchXp).size(0f, px(32));
        Label textoXp = new Label("", estilo);
        textoXp.setAlignment(Align.center);
        xp = new Barra(preenchXp, alinhaXp, textoXp, px(260 - 31));
        Table linhaXp = new Table();
        linhaXp.left();
        Image iconeXp = icone(atlas, "ui/XPIcon");
        if (iconeXp != null) linhaXp.add(iconeXp).size(px(26)).padLeft(px(3)).padRight(px(2));
        else linhaXp.add().size(px(26)).padLeft(px(3)).padRight(px(2));
        linhaXp.add(alinhaXp).size(xp.largura, px(32));
        Table centroXp = new Table();
        centroXp.add(textoXp).expand().center();
        Table fundoXp = new Table();
        fundoXp.setBackground(cor(COR_XP_FUNDO));
        Stack pilhaXp = new Stack(fundoXp, linhaXp, centroXp);
        Table painelXp = new Table();
        painelXp.setBackground(cor(Color.BLACK));
        painelXp.pad(px(1));
        painelXp.add(pilhaXp).size(px(260), px(32));

        raiz.setFillParent(true);
        raiz.top().left().padLeft(px(17)).padTop(px(13));
        raiz.add(painel).left().row();
        raiz.add(painelXp).left().padTop(px(6));
        // Sempre por baixo de qualquer outra tela (BookMenu, chat, settings...).
        stage.getRoot().addActorAt(0, raiz);
        atualizar();
        definirXp(1, 0);
    }

    /** Tahoma Bold no tamanho da print, com contorno preto de 2px de tela
     * (1px ficava fino demais). Gerada na resolucao real e reduzida pro stage. */
    private static BitmapFont gerarFonte(float escala) {
        com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator gerador =
            new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator(
                com.badlogic.gdx.Gdx.files.internal("fonts/TAHOMAB0.TTF"));
        com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter p =
            new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter();
        // No mobile o texto cresce menos que as barras (FATOR_TEXTO_MOBILE):
        // com o FATOR inteiro ficava grande demais pra barra. A borda acompanha
        // o tamanho (2px pra 14px, igual o PC) - fixa em 2px, numa fonte
        // gerada bem maior na tela do celular, a letra parecia fina.
        p.size = Math.round(14 * FATOR_TEXTO * escala);
        p.borderWidth = Math.max(2, Math.round(p.size / 7f));
        p.borderColor = Color.BLACK;
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        BitmapFont f = gerador.generateFont(p);
        f.getData().setScale(1f / escala);
        gerador.dispose();
        return f;
    }

    /** Pixels de tela -> unidades do stage. */
    private float px(float pixels) {
        return pixels * FATOR / escala;
    }

    private Drawable cor(Color c) {
        return new TextureRegionDrawable(new TextureRegion(pixel)).tint(c);
    }

    private Image icone(TextureAtlas atlas, String nome) {
        TextureAtlas.AtlasRegion regiao = atlas.findRegion(nome);
        if (regiao == null) return null;
        Image img = new Image(new TextureRegionDrawable(regiao));
        img.setScaling(Scaling.fit);
        return img;
    }

    private Barra criarBarra(Label.LabelStyle estilo, Color cor, float larguraPx) {
        Image preenchimento = new Image(cor(cor));
        Table alinhador = new Table();
        alinhador.left();
        alinhador.add(preenchimento).size(0f, px(34));
        Label texto = new Label("", estilo);
        texto.setAlignment(Align.center);
        return new Barra(preenchimento, alinhador, texto, px(larguraPx));
    }

    /** [icone 39px + 5] [barra 314x34 + 1px de sombra a direita/embaixo]. */
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<?> adicionarLinhaVital(Table pai, TextureAtlas atlas, String nomeIcone, Barra barra) {
        Image img = icone(atlas, nomeIcone);
        // Icone 39x39 (16px * ~2.4) colado na borda esquerda; padding vertical
        // negativo pra ele nao aumentar a altura da linha (que e' a da barra).
        if (img != null) pai.add(img).size(px(39)).padTop(px(-2)).padBottom(px(-2)).padRight(px(5));
        else pai.add().size(px(39)).padTop(px(-2)).padBottom(px(-2)).padRight(px(5));
        Table fundo = new Table();
        fundo.setBackground(cor(COR_PAINEL));
        Stack pilha = new Stack(fundo, barra.alinhador, barra.texto);
        Table comSombra = new Table();
        comSombra.setBackground(cor(COR_SOMBRA));
        comSombra.pad(0, 0, px(1), px(1));
        comSombra.add(pilha).size(barra.largura, px(34));
        return pai.add(comSombra);
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
        fonte.dispose();
    }
}
