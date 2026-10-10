package com.teste.game.mapa;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Junta as imagens de todos os tilesets do mapa numa textura so' (ao
 * carregar). Cada tileset era uma textura separada, e numa casa/decoracao os
 * tiles se alternam entre elas na mesma camada - a cada troca o renderer
 * tem que mandar o que juntou pra GPU e trocar de textura (no medidor do
 * admin: "dc 205 tex 205", 1 troca por chamada). Com uma textura so' o mapa
 * inteiro sai em poucas chamadas.
 *
 * So' troca pra onde aponta a TextureRegion de cada tile (mesmos pixels),
 * entao nada mais no jogo muda: propriedades, animacoes (os quadros sao os
 * mesmos tiles), minimapa (le os pixels da textura nova).
 */
final class UnificadorTilesets {

    private UnificadorTilesets() {}

    static void unificar(TiledMap mapa) {
        try {
            unificarOuFalhar(mapa);
        } catch (RuntimeException e) {
            // Se der errado, o mapa continua com as texturas separadas (so' mais lento).
            Gdx.app.error("UnificadorTilesets", "Nao deu pra juntar os tilesets: " + e);
        }
    }

    private static void unificarOuFalhar(TiledMap mapa) {
        // Regioes de cada textura usada pelos tiles.
        Map<Texture, List<TextureRegion>> porTextura = new LinkedHashMap<>();
        java.util.Set<TextureRegion> vistas = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (TiledMapTileSet ts : mapa.getTileSets()) {
            for (TiledMapTile tile : ts) {
                if (tile instanceof AnimatedTiledMapTile) continue; // os quadros sao tiles normais do proprio tileset
                TextureRegion r = tile.getTextureRegion();
                if (r == null || r.getTexture() == null) continue;
                if (!vistas.add(r)) continue; // mesma regiao em 2 tiles: mexe 1 vez so'
                porTextura.computeIfAbsent(r.getTexture(), k -> new ArrayList<>()).add(r);
            }
        }
        if (porTextura.size() < 2) return; // ja' e' uma so'

        int maximo = Math.min(4096, tamanhoMaximoTextura());
        // Empacota em prateleiras (maiores primeiro).
        List<Texture> texturas = new ArrayList<>(porTextura.keySet());
        texturas.sort((a, b) -> b.getHeight() - a.getHeight());
        Map<Texture, int[]> posicao = new LinkedHashMap<>();
        int x = 0, y = 0, alturaPrateleira = 0, largura = 0, altura = 0;
        for (Texture t : texturas) {
            if (t.getWidth() > maximo || t.getHeight() > maximo) continue;
            if (x + t.getWidth() > maximo) { x = 0; y += alturaPrateleira; alturaPrateleira = 0; }
            if (y + t.getHeight() > maximo) continue; // nao cabe: essa fica separada
            posicao.put(t, new int[]{x, y});
            x += t.getWidth();
            alturaPrateleira = Math.max(alturaPrateleira, t.getHeight());
            largura = Math.max(largura, x);
            altura = Math.max(altura, y + t.getHeight());
        }
        if (posicao.size() < 2) return;

        Pixmap combinado = new Pixmap(largura, altura, Pixmap.Format.RGBA8888);
        combinado.setBlending(Pixmap.Blending.None);
        List<Texture> juntadas = new ArrayList<>();
        for (Map.Entry<Texture, int[]> e : posicao.entrySet()) {
            Pixmap fonte = pixelsDe(e.getKey());
            if (fonte == null) continue; // sem acesso aos pixels: fica separada
            combinado.drawPixmap(fonte, e.getValue()[0], e.getValue()[1]);
            fonte.dispose();
            juntadas.add(e.getKey());
        }
        if (juntadas.size() < 2) {
            combinado.dispose();
            return;
        }
        // O Pixmap fica vivo dentro da textura (o Android recria a textura
        // dele se perder o contexto; o minimapa tambem le os pixels daqui).
        Texture nova = new Texture(combinado);
        nova.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        for (Texture antiga : juntadas) {
            int[] p = posicao.get(antiga);
            for (TextureRegion r : porTextura.get(antiga)) {
                int rx = r.getRegionX(), ry = r.getRegionY(), rw = r.getRegionWidth(), rh = r.getRegionHeight();
                r.setTexture(nova);
                r.setRegion(p[0] + rx, p[1] + ry, rw, rh);
            }
        }
        Gdx.app.log("UnificadorTilesets", juntadas.size() + " tilesets juntados numa textura " + largura + "x" + altura);
    }

    /** Copia dos pixels de uma textura carregada de arquivo (null se nao der). */
    private static Pixmap pixelsDe(Texture textura) {
        TextureData dados = textura.getTextureData();
        if (dados.getType() != TextureData.TextureDataType.Pixmap) return null;
        if (!dados.isPrepared()) dados.prepare();
        Pixmap original = dados.consumePixmap();
        Pixmap copia = new Pixmap(original.getWidth(), original.getHeight(), Pixmap.Format.RGBA8888);
        copia.setBlending(Pixmap.Blending.None);
        copia.drawPixmap(original, 0, 0);
        if (dados.disposePixmap()) original.dispose();
        return copia;
    }

    private static int tamanhoMaximoTextura() {
        IntBuffer b = BufferUtils.newIntBuffer(16);
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, b);
        int v = b.get(0);
        return v > 0 ? v : 2048;
    }
}
