package com.teste.game.mapa;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.math.Matrix4;

/**
 * Carrega o World.tmx (desenhado direto no Tiled, 16x16, sem passar mais
 * pelo Godot) e le colisao/spawn/luz/overlay das properties do .tsx
 * (MapaPropriedades, ver instructions.txt). TmxMapLoader/
 * OrthogonalTiledMapRenderer ja vem dentro do gdx core 1.12.1, nao precisa
 * de dependencia extra "gdx-tiled".
 *
 * Ordem de desenho (a pedido do usuario - player SEMPRE por cima de
 * qualquer tile, exceto os marcados overlap_transparency=true OU nas
 * camadas Roofs/Pillars):
 * 1) desenharMapa() - Ground+Buildings1+Buildings2, MENOS as celulas
 *    overlap_transparency=true (removidas da layer em MapaPropriedades,
 *    ver escanearColisaoELuz) - sempre atras do player.
 * 2) player (WorldScreen::desenharJogador)
 * 3) desenharOverlays() - so' as celulas overlap_transparency=true,
 *    desenhadas uma a una - sempre na frente do player. Alpha e' por
 *    CELULA individual (0.5 so' na celula que o player esta ocupando agora,
 *    nao a camada toda nem celulas vizinhas do mesmo tipo).
 * 4) desenharTelhados() - camadas Roofs e Pillars INTEIRAS, sempre na
 *    frente do player. Diferente de desenharOverlays: aqui o gatilho e' por
 *    CAMADA (o jogador estar em cima de QUALQUER celula ocupada da camada
 *    esconde/esmaece a camada INTEIRA, nao so' aquela celula) - Roofs some
 *    100% (telhado de casa fechada, ve o interior todo de uma vez), Pillars
 *    fica 50% translucido (mesma ideia, mas o jogador ainda aparece por
 *    tras em vez de sumir).
 * 5) nome (WorldScreen::desenharNome) - sempre por cima de tudo.
 */
public class MapaMundo {

    private final TiledMap mapa;
    private final OrthogonalTiledMapRenderer renderer;
    private final int[] indicesCamadas;
    private final int[] indicesTelhados;
    private final TiledMapTileLayer camadaRoofs;
    private final TiledMapTileLayer camadaPillars;

    public final int tileWidth;
    public final int tileHeight;
    public final int mapaLarguraTiles;
    public final int mapaAlturaTiles;
    public final ConversorCoordenadas conversor;
    public final MapaPropriedades propriedades;

    public MapaMundo() {
        mapa = new TmxMapLoader().load("maps/World.tmx");
        renderer = new OrthogonalTiledMapRenderer(mapa);

        MapProperties props = mapa.getProperties();
        tileWidth = props.get("tilewidth", Integer.class);
        tileHeight = props.get("tileheight", Integer.class);
        mapaLarguraTiles = props.get("width", Integer.class);
        mapaAlturaTiles = props.get("height", Integer.class);

        // World.tmx foi desenhado do zero direto no Tiled (nao vem mais de
        // conversao do Godot, sem origem deslocada conhecida) - offset 0,0
        // como ponto de partida. Se o spawn/posicao de outros jogadores
        // aparecer deslocado do tile certo, e' esse par que precisa ajustar.
        conversor = new ConversorCoordenadas(0, 0, alturaPx());

        // Construido ANTES de ler os indices de layer - o scan de
        // MapaPropriedades remove (seta null) as celulas overlap_transparency
        // das layers originais, entao renderer.render(indicesCamadas) ja
        // deixa de desenha-las sozinho (elas so' saem via desenharOverlays).
        propriedades = new MapaPropriedades(mapa, tileWidth, tileHeight);

        indicesCamadas = new int[]{
                mapa.getLayers().getIndex("Ground"),
                mapa.getLayers().getIndex("Buildings1"),
                mapa.getLayers().getIndex("Buildings2")
        };
        indicesTelhados = new int[]{
                mapa.getLayers().getIndex("Roofs"),
                mapa.getLayers().getIndex("Pillars")
        };
        camadaRoofs = (TiledMapTileLayer) mapa.getLayers().get("Roofs");
        camadaPillars = (TiledMapTileLayer) mapa.getLayers().get("Pillars");
        // O jogo controla visibilidade dinamicamente (ver desenharTelhados) -
        // ignora o checkbox "visible" que o Tiled grava no .tmx (o usuario
        // deixa a camada Pillars escondida la' so' pra editar outra coisa
        // sem ela atrapalhar, isso nao e' estado de jogo).
        if (camadaRoofs != null) camadaRoofs.setVisible(true);
        if (camadaPillars != null) camadaPillars.setVisible(true);
    }

    public void atualizar(float delta) {
        // Decoracoes (pilar/tocha/pedra/grama) agora sao tiles normais do
        // Tileset, nao mais "objetos-cena" instanciados a parte - incluindo
        // as animadas (ver <animation> no Tileset1 (16x16).tsx), que o
        // TmxMapLoader ja registra como AnimatedTiledMapTile sozinho. Nao
        // sobrou nenhum estado pra atualizar aqui.
    }

    private void prepararView(OrthographicCamera camera) {
        // setView(camera) sozinho calcula os limites EXATOS do viewport - com
        // zoom fracionario (ver WorldScreen::NIVEIS_ZOOM, nao sao potencias
        // de 2) arredondamento de ponto flutuante podia deixar a ultima
        // coluna/linha de tile faltando bem na borda da tela por 1 frame
        // enquanto a camera anda (o "vazio por 1 segundo nas bordas" que o
        // usuario reportou). Folga de 2 tiles alem do viewport garante que
        // sempre sobra tile de verdade desenhado além do que aparece,
        // fechando essa margem de erro - custo extra e' desprezivel (poucas
        // colunas/linhas a mais por frame).
        float folga = Math.max(tileWidth, tileHeight) * 2f;
        float largura = camera.viewportWidth * camera.zoom + folga * 2f;
        float altura = camera.viewportHeight * camera.zoom + folga * 2f;
        renderer.setView(camera.combined, camera.position.x - largura / 2f, camera.position.y - altura / 2f, largura, altura);
    }

    /** Ground+Buildings1+Buildings2 MENOS as celulas overlap_transparency
     * (essas saem so' em desenharOverlays, por cima do player). Roofs e
     * Pillars NAO entram aqui - ver desenharTelhados. */
    public void desenharMapa(OrthographicCamera camera) {
        prepararView(camera);
        renderer.render(indicesCamadas);
    }

    /** Celulas overlap_transparency=true, uma a uma, por cima do player -
     * chamado DENTRO do batch.begin()/end() do WorldScreen (como o antigo
     * ObjetosCena.desenhar fazia), nao usa o renderer/batch interno do
     * OrthogonalTiledMapRenderer. Alpha 0.5 so' na celula exata que o
     * jogador esta ocupando agora - as outras celulas overlay (mesmo tipo
     * de tile, vizinhas) ficam 100% opacas. */
    public void desenharOverlays(Batch batch, float jogadorMundoX, float jogadorMundoY) {
        int jcx = (int) Math.floor(jogadorMundoX / tileWidth);
        int jcy = (int) Math.floor(jogadorMundoY / tileHeight);
        for (MapaPropriedades.CelulaOverlay c : propriedades.celulasOverlay) {
            TextureRegion regiao = c.tile.getTextureRegion();
            boolean jogadorAqui = c.cx == jcx && c.cy == jcy;
            batch.setColor(1f, 1f, 1f, jogadorAqui ? 0.5f : 1f);
            batch.draw(regiao, c.worldX, c.worldY, regiao.getRegionWidth(), regiao.getRegionHeight());
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    /** Camadas Roofs/Pillars INTEIRAS, por cima do player - gatilho e' por
     * CAMADA (diferente de desenharOverlays): se o jogador esta em cima de
     * QUALQUER celula ocupada de uma dessas camadas, a camada INTEIRA muda
     * de opacidade (nao so' aquela celula). Roofs some 100% (telhado de casa
     * fechada - ve' o interior inteiro de uma vez), Pillars fica 50%
     * translucido. Usa o renderer/batch interno do OrthogonalTiledMapRenderer
     * (igual desenharMapa) - por isso chamado FORA do batch.begin()/end() do
     * WorldScreen. */
    public void desenharTelhados(OrthographicCamera camera, float jogadorMundoX, float jogadorMundoY) {
        prepararView(camera);
        renderTelhados(jogadorMundoX, jogadorMundoY);
    }

    /** Pedaco do mapa pro minimapa (MiniMapa.Cache): chao + construcoes +
     * overlays, SEM telhados/pilares (mostra o interior das casas, igual
     * minimapa de Tibia). x/y/largura/altura = area do mundo que vai pro FBO.
     * Chamado FORA de qualquer batch.begin(). */
    public void desenharBlocoMiniMapa(Matrix4 projecao, float x, float y, float largura, float altura) {
        // Folga: tiles mais altos que 1 SQM (arvores) de celulas vizinhas
        // invadem o bloco e precisam entrar.
        float folga = Math.max(tileWidth, tileHeight) * 4f;
        renderer.setView(projecao, x - folga, y - folga, largura + folga * 2f, altura + folga * 2f);
        renderer.render(indicesCamadas);
        Batch b = renderer.getBatch();
        b.begin();
        for (MapaPropriedades.CelulaOverlay c : propriedades.celulasOverlay) {
            if (c.worldX + folga < x || c.worldX > x + largura || c.worldY + folga < y || c.worldY > y + altura) continue;
            TextureRegion regiao = c.tile.getTextureRegion();
            b.draw(regiao, c.worldX, c.worldY, regiao.getRegionWidth(), regiao.getRegionHeight());
        }
        b.end();
    }

    private void renderTelhados(float jogadorMundoX, float jogadorMundoY) {
        int jcx = (int) Math.floor(jogadorMundoX / tileWidth);
        int jcy = (int) Math.floor(jogadorMundoY / tileHeight);

        float alphaAnteriorRoofs = camadaRoofs != null ? camadaRoofs.getOpacity() : 1f;
        float alphaAnteriorPillars = camadaPillars != null ? camadaPillars.getOpacity() : 1f;
        if (camadaRoofs != null) camadaRoofs.setOpacity(celulaOcupada(camadaRoofs, jcx, jcy) ? 0f : 1f);
        if (camadaPillars != null) camadaPillars.setOpacity(celulaOcupada(camadaPillars, jcx, jcy) ? 0.5f : 1f);

        renderer.render(indicesTelhados);

        if (camadaRoofs != null) camadaRoofs.setOpacity(alphaAnteriorRoofs);
        if (camadaPillars != null) camadaPillars.setOpacity(alphaAnteriorPillars);
    }

    private boolean celulaOcupada(TiledMapTileLayer camada, int cx, int cy) {
        TiledMapTileLayer.Cell cell = camada.getCell(cx, cy);
        return cell != null && cell.getTile() != null;
    }

    public float larguraPx() {
        return mapaLarguraTiles * tileWidth;
    }

    public float alturaPx() {
        return mapaAlturaTiles * tileHeight;
    }

    public void dispose() {
        mapa.dispose();
        renderer.dispose();
    }
}
