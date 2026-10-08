package com.teste.game.mapa;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.maps.tiled.TiledMapTile;

import java.util.HashMap;
import java.util.Map;

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
        // Comeca com toda area de quest travada (o servidor manda as feitas no join).
        aplicarQuests(java.util.Collections.emptyList());
    }

    // ---- Areas de quest (ex: ponte do Kharon) ----
    // Tiles escondidos de cada area enquanto a quest nao foi feita: removidos
    // da camada (setCell null) DEPOIS do scan de colisao (MapaPropriedades) -
    // a colisao continua sendo a "com ponte"; quem trava sem a quest e' o
    // bloqueadoPorQuest (client) e o servidor.
    private static final class CelulaGuardada {
        final TiledMapTileLayer camada; final int cx, cy; final TiledMapTileLayer.Cell cell;
        CelulaGuardada(TiledMapTileLayer camada, int cx, int cy, TiledMapTileLayer.Cell cell) {
            this.camada = camada; this.cx = cx; this.cy = cy; this.cell = cell;
        }
    }
    private final Map<MapaPropriedades.AreaQuest, java.util.List<CelulaGuardada>> escondidas = new HashMap<>();
    private final java.util.Set<String> questsFeitas = new java.util.HashSet<>();

    private int[] celulasDe(com.badlogic.gdx.math.Rectangle r) {
        return new int[]{(int) Math.floor(r.x / tileWidth), (int) Math.floor((r.x + r.width - 1f) / tileWidth),
                         (int) Math.floor(r.y / tileHeight), (int) Math.floor((r.y + r.height - 1f) / tileHeight)};
    }

    // Linhas da ponte esperando pra aparecer (animacao do Pay): cada item =
    // as celulas de uma linha (3 SQMs numa ponte de 3 de largura).
    private final java.util.ArrayDeque<java.util.List<CelulaGuardada>> linhasPendentes = new java.util.ArrayDeque<>();

    /** Mostra/esconde os tiles de cada area conforme as quests feitas. */
    public void aplicarQuests(java.util.Collection<String> feitas) {
        aplicarQuests(feitas, java.util.Collections.emptySet(), 0f, 0f);
    }

    /** animar: quests que acabaram de ser feitas - a area delas aparece linha
     * por linha (revelarProximaLinha), comecando pela ponta mais perto de
     * (pertoX, pertoY), em vez de tudo de uma vez. */
    public void aplicarQuests(java.util.Collection<String> feitas, java.util.Set<String> animar, float pertoX, float pertoY) {
        questsFeitas.clear();
        for (String q : feitas) questsFeitas.add(q.toLowerCase());
        for (MapaPropriedades.AreaQuest a : propriedades.areasQuest) {
            boolean liberada = questsFeitas.contains(a.quest);
            java.util.List<CelulaGuardada> guardadas = escondidas.get(a);
            if (liberada) {
                if (guardadas == null) continue;
                escondidas.remove(a);
                if (animar.contains(a.quest)) {
                    enfileirarLinhas(a, guardadas, pertoX, pertoY);
                    continue;
                }
                for (CelulaGuardada g : guardadas) g.camada.setCell(g.cx, g.cy, g.cell);
            } else if (guardadas == null) {
                guardadas = new java.util.ArrayList<>();
                int[] c = celulasDe(a.area);
                java.util.Set<String> nomes = null;
                if (a.camadas != null) {
                    nomes = new java.util.HashSet<>();
                    for (String n : a.camadas.split(",")) nomes.add(n.trim());
                }
                for (com.badlogic.gdx.maps.MapLayer ml : mapa.getLayers()) {
                    if (!(ml instanceof TiledMapTileLayer)) continue;
                    if (nomes != null ? !nomes.contains(ml.getName()) : "Ground".equals(ml.getName())) continue;
                    TiledMapTileLayer camada = (TiledMapTileLayer) ml;
                    for (int x = c[0]; x <= c[1]; x++) {
                        for (int y = c[2]; y <= c[3]; y++) {
                            TiledMapTileLayer.Cell cell = camada.getCell(x, y);
                            if (cell == null) continue;
                            guardadas.add(new CelulaGuardada(camada, x, y, cell));
                            camada.setCell(x, y, null);
                        }
                    }
                }
                escondidas.put(a, guardadas);
            }
        }
    }

    private void enfileirarLinhas(MapaPropriedades.AreaQuest a, java.util.List<CelulaGuardada> guardadas, float px, float py) {
        // Ponte em pe (mais alta que larga): uma linha = mesma altura (cy);
        // deitada: mesma coluna (cx).
        boolean emPe = a.area.height >= a.area.width;
        java.util.TreeMap<Integer, java.util.List<CelulaGuardada>> porLinha = new java.util.TreeMap<>();
        for (CelulaGuardada g : guardadas) porLinha.computeIfAbsent(emPe ? g.cy : g.cx, k -> new java.util.ArrayList<>()).add(g);
        java.util.List<java.util.List<CelulaGuardada>> linhas = new java.util.ArrayList<>(porLinha.values());
        // Comeca pela ponta mais perto de quem pagou.
        float inicio = emPe ? porLinha.firstKey() * tileHeight : porLinha.firstKey() * tileWidth;
        float fim = emPe ? porLinha.lastKey() * tileHeight : porLinha.lastKey() * tileWidth;
        float ref = emPe ? py : px;
        if (Math.abs(ref - fim) < Math.abs(ref - inicio)) java.util.Collections.reverse(linhas);
        linhasPendentes.addAll(linhas);
    }

    /** Mostra a proxima linha da animacao. Devolve o centro (mundo) dela, ou
     * null se nao tem mais nada pra mostrar. */
    public float[] revelarProximaLinha() {
        java.util.List<CelulaGuardada> linha = linhasPendentes.poll();
        if (linha == null || linha.isEmpty()) return null;
        float somaX = 0f, somaY = 0f;
        java.util.Set<Long> vistos = new java.util.HashSet<>();
        int n = 0;
        for (CelulaGuardada g : linha) {
            g.camada.setCell(g.cx, g.cy, g.cell);
            if (vistos.add(((long) g.cx << 32) | (g.cy & 0xffffffffL))) {
                somaX += g.cx; somaY += g.cy; n++;
            }
        }
        return new float[]{(somaX / n + 0.5f) * tileWidth, (somaY / n) * tileHeight};
    }

    public boolean temLinhaPendente() { return !linhasPendentes.isEmpty(); }

    /** SQM (cx, cy com Y pra cima) dentro de uma area cuja quest nao foi feita. */
    public boolean bloqueadoPorQuest(int cx, int cy) {
        for (MapaPropriedades.AreaQuest a : propriedades.areasQuest) {
            if (questsFeitas.contains(a.quest)) continue;
            int[] c = celulasDe(a.area);
            if (cx >= c[0] && cx <= c[1] && cy >= c[2] && cy <= c[3]) return true;
        }
        return false;
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

    /** Minimapa: o mapa inteiro com 1 pixel por SQM. Cor de cada SQM = cor
     * media (pixels opacos) do tile mais de cima entre Ground/Buildings1/
     * Buildings2/overlays - sem telhados (mostra o interior das casas). Um
     * tile pode forcar a cor com a property "minimap_color" (ex: ff0000) no
     * .tsx. Linha 0 do pixmap = topo do mapa. */
    public Pixmap gerarPixmapMiniMapa(Color fundo) {
        int w = mapaLarguraTiles, h = mapaAlturaTiles;
        int[] cores = new int[w * h];
        Map<TiledMapTile, Integer> cache = new HashMap<>();
        Map<Texture, Pixmap> fontes = new HashMap<>();
        try {
            for (int indice : indicesCamadas) {
                if (indice < 0 || !(mapa.getLayers().get(indice) instanceof TiledMapTileLayer)) continue;
                TiledMapTileLayer camada = (TiledMapTileLayer) mapa.getLayers().get(indice);
                for (int y = 0; y < Math.min(h, camada.getHeight()); y++) {
                    for (int x = 0; x < Math.min(w, camada.getWidth()); x++) {
                        TiledMapTileLayer.Cell cell = camada.getCell(x, y);
                        if (cell == null || cell.getTile() == null) continue;
                        int c = corDoTile(cell.getTile(), cache, fontes);
                        if (c != 0) cores[y * w + x] = c;
                    }
                }
            }
            for (MapaPropriedades.CelulaOverlay c : propriedades.celulasOverlay) {
                if (c.cx < 0 || c.cy < 0 || c.cx >= w || c.cy >= h) continue;
                int cor = corDoTile(c.tile, cache, fontes);
                if (cor != 0) cores[c.cy * w + c.cx] = cor;
            }
        } finally {
            for (Pixmap p : fontes.values()) if (p != null) p.dispose();
        }
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        int corFundo = Color.rgba8888(fundo);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = cores[y * w + x];
                pm.drawPixel(x, h - 1 - y, c != 0 ? c : corFundo);
            }
        }
        return pm;
    }

    /** RGBA8888 da cor media do tile (0 = todo transparente). */
    private int corDoTile(TiledMapTile tile, Map<TiledMapTile, Integer> cache, Map<Texture, Pixmap> fontes) {
        Integer guardada = cache.get(tile);
        if (guardada != null) return guardada;
        int resultado = 0;
        Object forcada = tile.getProperties().get("minimap_color");
        if (forcada != null) {
            try {
                String hex = forcada.toString().replace("#", "");
                // Tiled grava cor como #AARRGGBB; texto simples como RRGGBB.
                if (hex.length() == 8) hex = hex.substring(2) + hex.substring(0, 2);
                resultado = Color.rgba8888(Color.valueOf(hex));
            } catch (RuntimeException ignorada) { }
        }
        if (resultado == 0) {
            TextureRegion regiao = tile.getTextureRegion();
            Pixmap fonte = regiao != null ? pixmapDe(regiao.getTexture(), fontes) : null;
            if (fonte != null) {
                long r = 0, g = 0, b = 0, n = 0;
                int x0 = regiao.getRegionX(), y0 = regiao.getRegionY();
                for (int y = y0; y < y0 + regiao.getRegionHeight(); y++) {
                    for (int x = x0; x < x0 + regiao.getRegionWidth(); x++) {
                        int p = fonte.getPixel(x, y);
                        if ((p & 0xff) < 128) continue;
                        r += (p >>> 24) & 0xff;
                        g += (p >>> 16) & 0xff;
                        b += (p >>> 8) & 0xff;
                        n++;
                    }
                }
                if (n > 0) resultado = (int) ((r / n) << 24 | (g / n) << 16 | (b / n) << 8 | 0xff);
            }
        }
        cache.put(tile, resultado);
        return resultado;
    }

    /** Pixels de uma textura do tileset (le de novo do arquivo; null se nao der). */
    private static Pixmap pixmapDe(Texture textura, Map<Texture, Pixmap> fontes) {
        if (fontes.containsKey(textura)) return fontes.get(textura);
        Pixmap p = null;
        try {
            TextureData dados = textura.getTextureData();
            if (dados.getType() == TextureData.TextureDataType.Pixmap) {
                if (!dados.isPrepared()) dados.prepare();
                Pixmap consumido = dados.consumePixmap();
                // Copia (o original pode ser descartado pelo TextureData).
                p = new Pixmap(consumido.getWidth(), consumido.getHeight(), Pixmap.Format.RGBA8888);
                p.setBlending(Pixmap.Blending.None);
                p.drawPixmap(consumido, 0, 0);
                if (dados.disposePixmap()) consumido.dispose();
            }
        } catch (RuntimeException e) {
            p = null;
        }
        fontes.put(textura, p);
        return p;
    }

    // Dimensoes achadas sozinhas (sem camada "Dimensions"): cada pedaco de
    // mapa desenhado separado dos outros por SQMs vazios e' uma dimensao.
    private int[] componenteDaCelula;
    private final java.util.List<com.badlogic.gdx.math.Rectangle> componentes = new java.util.ArrayList<>();
    private final com.badlogic.gdx.math.Rectangle mapaInteiro = new com.badlogic.gdx.math.Rectangle();

    /** Retangulo (mundo) da dimensao em que esse ponto esta: o da camada
     * "Dimensions" do Tiled se existir; senao o pedaco de mapa (SQMs nao
     * vazios ligados entre si) que contem o ponto; senao o mapa inteiro. */
    public com.badlogic.gdx.math.Rectangle dimensaoEm(float mundoX, float mundoY) {
        for (com.badlogic.gdx.math.Rectangle r : propriedades.dimensoes) if (r.contains(mundoX, mundoY)) return r;
        mapaInteiro.set(0, 0, larguraPx(), alturaPx());
        if (!propriedades.dimensoes.isEmpty()) return mapaInteiro;
        if (componenteDaCelula == null) acharComponentes();
        int cx = (int) Math.floor(mundoX / tileWidth), cy = (int) Math.floor(mundoY / tileHeight);
        if (cx < 0 || cy < 0 || cx >= mapaLarguraTiles || cy >= mapaAlturaTiles) return mapaInteiro;
        int id = componenteDaCelula[cy * mapaLarguraTiles + cx];
        return id > 0 ? componentes.get(id - 1) : mapaInteiro;
    }

    private void acharComponentes() {
        int w = mapaLarguraTiles, h = mapaAlturaTiles;
        boolean[] cheio = new boolean[w * h];
        for (int indice : indicesCamadas) {
            if (indice < 0 || !(mapa.getLayers().get(indice) instanceof TiledMapTileLayer)) continue;
            TiledMapTileLayer camada = (TiledMapTileLayer) mapa.getLayers().get(indice);
            for (int y = 0; y < Math.min(h, camada.getHeight()); y++)
                for (int x = 0; x < Math.min(w, camada.getWidth()); x++) {
                    TiledMapTileLayer.Cell c = camada.getCell(x, y);
                    if (c != null && c.getTile() != null) cheio[y * w + x] = true;
                }
        }
        for (MapaPropriedades.CelulaOverlay c : propriedades.celulasOverlay)
            if (c.cx >= 0 && c.cy >= 0 && c.cx < w && c.cy < h) cheio[c.cy * w + c.cx] = true;
        componenteDaCelula = new int[w * h];
        int[] fila = new int[w * h];
        for (int inicio = 0; inicio < w * h; inicio++) {
            if (!cheio[inicio] || componenteDaCelula[inicio] != 0) continue;
            int id = componentes.size() + 1;
            int minX = w, minY = h, maxX = -1, maxY = -1;
            int ini = 0, fim = 0;
            fila[fim++] = inicio;
            componenteDaCelula[inicio] = id;
            while (ini < fim) {
                int i = fila[ini++];
                int x = i % w, y = i / w;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                int[] viz = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
                for (int v : viz) {
                    if (v >= 0 && cheio[v] && componenteDaCelula[v] == 0) {
                        componenteDaCelula[v] = id;
                        fila[fim++] = v;
                    }
                }
            }
            componentes.add(new com.badlogic.gdx.math.Rectangle(minX * tileWidth, minY * tileHeight,
                (maxX - minX + 1) * tileWidth, (maxY - minY + 1) * tileHeight));
        }
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
