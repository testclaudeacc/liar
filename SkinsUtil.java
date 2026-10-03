package com.teste.game.telas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonValue;

import java.util.HashMap;
import java.util.Map;

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

    private static final String REFERENCIA_PES = "sprites/base/BaseSoul";
    private static final Map<String, TextureRegion> CACHE_PELES = new HashMap<>();
    private static final Map<Texture, Pixmap> PIXELS = new HashMap<>();

    /**
     * "res://sprites/hair/FemHair1.png" -> regiao "sprites/hair/FemHair1" do atlas.
     * Peles (sprites/base/...) podem ter sido exportadas com altura diferente
     * (ex: DarkBase com 32px, as outras com 17) - pra todas ficarem com os pes
     * no mesmo lugar, a tira e' recortada pra ter o mesmo tanto de linha vazia
     * embaixo dos pes que o BaseSoul (lido dos pixels do graphics.png).
     */
    public static TextureRegion regiao(TextureAtlas atlas, String caminho) {
        if (caminho == null || caminho.isEmpty()) return null;
        String nome = caminho.startsWith("res://") ? caminho.substring(6) : caminho;
        int ponto = nome.lastIndexOf('.');
        if (ponto > nome.lastIndexOf('/')) nome = nome.substring(0, ponto);
        TextureAtlas.AtlasRegion tira = atlas.findRegion(nome);
        if (tira == null || !nome.startsWith("sprites/base/") || nome.equals(REFERENCIA_PES)) return tira;
        TextureRegion pronta = CACHE_PELES.get(nome);
        if (pronta != null) return pronta;
        pronta = alinharPes(tira, atlas.findRegion(REFERENCIA_PES));
        CACHE_PELES.put(nome, pronta);
        return pronta;
    }

    private static TextureRegion alinharPes(TextureAtlas.AtlasRegion tira, TextureAtlas.AtlasRegion referencia) {
        int vaziasTira = linhasVaziasEmbaixo(tira);
        int vaziasRef = referencia == null ? 0 : linhasVaziasEmbaixo(referencia);
        int sobra = vaziasTira - vaziasRef;
        if (vaziasTira < 0 || vaziasRef < 0 || sobra <= 0 || sobra >= tira.getRegionHeight()) return tira;
        // Corta as linhas vazias a mais de baixo (y da regiao cresce pra baixo).
        return new TextureRegion(tira, 0, 0, tira.getRegionWidth(), tira.getRegionHeight() - sobra);
    }

    /** Quantas linhas totalmente transparentes ha embaixo do desenho (-1 = nao deu pra ler os pixels). */
    private static int linhasVaziasEmbaixo(TextureAtlas.AtlasRegion tira) {
        Pixmap pixels = pixelsDa(tira.getTexture());
        if (pixels == null) return -1;
        int x0 = tira.getRegionX(), y0 = tira.getRegionY();
        int w = tira.getRegionWidth(), h = tira.getRegionHeight();
        for (int linha = 0; linha < h; linha++) {
            int y = y0 + h - 1 - linha;
            for (int x = x0; x < x0 + w; x++) {
                if ((pixels.getPixel(x, y) & 0xff) != 0) return linha;
            }
        }
        return h;
    }

    private static Pixmap pixelsDa(Texture textura) {
        if (PIXELS.containsKey(textura)) return PIXELS.get(textura);
        Pixmap pixmap = null;
        try {
            if (textura.getTextureData() instanceof FileTextureData) {
                pixmap = new Pixmap(((FileTextureData) textura.getTextureData()).getFileHandle());
            }
        } catch (RuntimeException e) {
            pixmap = null;
        }
        PIXELS.put(textura, pixmap);
        return pixmap;
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
}
