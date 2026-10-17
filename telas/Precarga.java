package com.teste.game.telas;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;

/**
 * Carrega o mapa (World.tmx) e o atlas do jogo AOS POUCOS, enquanto a tela de
 * loading anima (AssetManager: le/decodifica em outra thread e so' sobe as
 * texturas pra placa de video no render). Antes isso era feito de uma vez no
 * construtor da WorldScreen e travava as bolinhas do loading.
 *
 * O atlas fica guardado pra proxima entrada no mundo. O mapa e' por sessao
 * (quests mexem nas celulas): quem pegou fica com ele, e a proxima entrada
 * descarta e carrega de novo.
 */
public final class Precarga {
    public static final String MAPA = "maps/World.tmx";
    public static final String ATLAS = "graphics/graphics.atlas";

    private static AssetManager am;
    private static boolean mapaTomado = false;

    private Precarga() {}

    /** Comeca (ou continua) a carregar. Chamar antes de mostrar o loading. */
    public static void iniciar() {
        if (am == null) {
            am = new AssetManager(new InternalFileHandleResolver());
            am.setLoader(TiledMap.class, new TmxMapLoader(new InternalFileHandleResolver()));
        }
        if (mapaTomado) {
            // Mapa da sessao anterior (com quests aplicadas): carrega um limpo.
            if (am.contains(MAPA)) am.unload(MAPA);
            mapaTomado = false;
        }
        if (!am.contains(MAPA)) am.load(MAPA, TiledMap.class);
        if (!am.contains(ATLAS)) am.load(ATLAS, TextureAtlas.class);
    }

    /** Um pedacinho do carregamento (no maximo ~12 ms por frame). true = acabou. */
    public static boolean atualizar() {
        return am == null || am.update(12);
    }

    /** Mapa ja' carregado (e marcado como em uso), ou null - quem chamou carrega do jeito antigo. */
    public static TiledMap tomarMapa() {
        if (am == null || mapaTomado || !am.isLoaded(MAPA, TiledMap.class)) return null;
        mapaTomado = true;
        return am.get(MAPA, TiledMap.class);
    }

    public static TextureAtlas atlas() {
        return am != null && am.isLoaded(ATLAS, TextureAtlas.class) ? am.get(ATLAS, TextureAtlas.class) : null;
    }
}
