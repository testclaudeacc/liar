package com.teste.game.mapa;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.teste.game.entidades.Jogador;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le colisao/spawn/luz/overlay direto das properties do .tsx e do Tile
 * Collision Editor, a partir do TiledMap ja carregado pelo TmxMapLoader
 * (instructions.txt - nao depende mais de nenhum dado pre-calculado do Godot).
 *
 * Convencao de eixo Y: TmxMapLoader (flipY=true por padrao) ja flipa TUDO
 * pra espaco Y-pra-cima sozinho - tanto TiledMapTileLayer.getCell(x,y)
 * quanto os objetos (SpawnPoints, formas do Tile Collision Editor). Testado
 * na pratica (nao e' obvio pela doc): um objeto de layer com y="1119.88" no
 * .tmx chega em RectangleMapObject.getRectangle().y = mapHeightPx - y -
 * height; uma forma de Tile Collision Editor com y="0.09" chega com y =
 * tileHeight - y - height. Ou seja NAO flipar de novo aqui - os valores
 * crus do getRectangle()/getPolygon() ja sao direto utilizaveis em espaco
 * de mundo (just offset pela celula/origem, sem nenhuma conta de flip).
 */
public class MapaPropriedades {

    public enum Borda { ESQUERDA, DIREITA, CIMA, BAIXO }

    public static class Luz {
        public float x, y;
        public final Color cor;
        public final float raio;

        public Luz(float x, float y, Color cor, float raio) {
            this.x = x;
            this.y = y;
            this.cor = cor;
            this.raio = raio;
        }
    }

    /** Forma fina do Tile Collision Editor (cerca/corrimao/ponta de 1px) -
     * bloqueia so' a borda especifica da celula, nao a celula inteira. */
    public static class BordaFina {
        public final float worldCellX, worldCellY;
        public final Borda lado;

        BordaFina(float worldCellX, float worldCellY, Borda lado) {
            this.worldCellX = worldCellX;
            this.worldCellY = worldCellY;
            this.lado = lado;
        }
    }

    /** Tile com overlap_transparency=true - desenhado numa passada separada,
     * por cima do player (MapaMundo removeu essa celula da layer original,
     * ver escanearColisaoELuz). */
    public static class CelulaOverlay {
        public final int cx, cy;
        public final float worldX, worldY;
        public final TiledMapTile tile;

        CelulaOverlay(int cx, int cy, float worldX, float worldY, TiledMapTile tile) {
            this.cx = cx;
            this.cy = cy;
            this.worldX = worldX;
            this.worldY = worldY;
            this.tile = tile;
        }
    }

    /** Regiao nomeada da camada "AreasName" (area_id = nome a mostrar no
     * banner estilo "entrou numa nova area" - ver AreaNomeUI/WorldScreen).
     * Ao contrario de SpawnPoints/NPCSpawns, NAO snapa pro grid de tile -
     * e' uma area (retangulo arbitrario desenhado no Tiled), nao um ponto. */
    public static class AreaNomeada {
        public final String nome;
        public final Rectangle area;

        AreaNomeada(String nome, Rectangle area) {
            this.nome = nome;
            this.area = area;
        }
    }

    public static class NPCSpawn {
        public final String npcId;
        public final String spawnKey;
        public final float worldX;
        public final float worldY;

        NPCSpawn(String npcId, float worldX, float worldY) {
            this.npcId = npcId;
            this.worldX = worldX;
            this.worldY = worldY;
            this.spawnKey = npcId + "_" + Math.round(worldX / Jogador.TILE) + "_" + Math.round(worldY / Jogador.TILE);
        }
    }

    /**
     * Ponto de spawn de mob (camada de objetos "MobSpawns" no Tiled, um Point
     * por mob). Propriedades: mob_id (string, ex "rotworm" - nome do MOB_DB do
     * servidor), spawn_range (int, SQMs pra cada lado em que ele pode nascer,
     * 0 = so' no ponto) e respawn_time (float, segundos ate renascer).
     */
    public static class MobSpawn {
        public final String mobId;
        public final float worldX;
        public final float worldY;
        public final int spawnRange;
        public final float respawnTime;

        MobSpawn(String mobId, float worldX, float worldY, int spawnRange, float respawnTime) {
            this.mobId = mobId;
            this.worldX = worldX;
            this.worldY = worldY;
            this.spawnRange = spawnRange;
            this.respawnTime = respawnTime;
        }
    }

    private static final String[] CAMADAS_COLISAO_E_LUZ = {"Ground", "Buildings1", "Buildings2", "Buildings3", "Roofs", "Pillars"};
    private final Map<Long, Float> velocidadePorCelula = new HashMap<>();
    // Protection Zone: SQMs com qualquer tile na camada de tiles
    // "ProtectionZone" (nao e' desenhada). Mesma regra do servidor.py::na_pz.
    private final java.util.Set<Long> zonaProtegida = new java.util.HashSet<>();
    // Teleports: SQMs cobertos pelos retangulos da camada de objetos
    // "Teleports" (o destino so' o servidor usa - servidor.py::handle_tp).
    private final java.util.Set<Long> teleportes = new java.util.HashSet<>();

    // Forma e' "fina" se um lado for <= 30% do tile (~4.8px de 16) e o lado
    // oposto cobrir pelo menos 60% do tile (~9.6px) - bate com os valores
    // reais observados no .tsx (ex: width=1.09/height=15.9) sem confundir
    // com formas grandes/rampas de verdade.
    private static final float LIMIAR_FINO = 0.3f;
    private static final float LIMIAR_COBERTURA = 0.6f;

    public final List<Rectangle> hitboxesMundo = new ArrayList<>();
    public final List<BordaFina> bordasFinas = new ArrayList<>();
    public final List<CelulaOverlay> celulasOverlay = new ArrayList<>();
    /** Tiles com a property Above_Player=true (MapaMundo.desenharAcimaDoPlayer). */
    public final List<CelulaAcima> celulasAcimaDoPlayer = new ArrayList<>();

    public static class CelulaAcima {
        public final TiledMapTileLayer camada;
        public final int cx, cy;
        public final float worldX, worldY;
        public final TiledMapTile tile;
        CelulaAcima(TiledMapTileLayer camada, int cx, int cy, float worldX, float worldY, TiledMapTile tile) {
            this.camada = camada; this.cx = cx; this.cy = cy; this.worldX = worldX; this.worldY = worldY; this.tile = tile;
        }
    }

    private static boolean propriedadeTrue(MapProperties props, String nome) {
        Object v = props.get(nome);
        if (v == null) v = props.get(nome.toLowerCase());
        return Boolean.TRUE.equals(v) || (v != null && "true".equalsIgnoreCase(v.toString().trim()));
    }
    public final Map<String, Vector2> spawns = new HashMap<>();
    public final List<NPCSpawn> npcSpawns = new ArrayList<>();
    public final List<MobSpawn> mobSpawns = new ArrayList<>();
    public final List<Luz> luzes = new ArrayList<>();
    public final List<AreaNomeada> areasNomeadas = new ArrayList<>();
    public final List<Rectangle> dimensoes = new ArrayList<>();
    public final List<AreaQuest> areasQuest = new ArrayList<>();

    public static class AreaQuest {
        public final String quest;
        public final Rectangle area;      // mundo (Y pra cima)
        public final String camadas;      // "Buildings1,Buildings2" ou null (Buildings2)
        /** Linhas de cima que nao travam (property free_top_rows). */
        public final int linhasLivresTopo;
        AreaQuest(String quest, Rectangle area, String camadas, int linhasLivresTopo) {
            this.quest = quest; this.area = area; this.camadas = camadas; this.linhasLivresTopo = linhasLivresTopo;
        }
    }

    private final TiledMap mapa;
    private final int tileWidth, tileHeight;

    public MapaPropriedades(TiledMap mapa, int tileWidth, int tileHeight) {
        this.mapa = mapa;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;

        TiledMapTileLayer[] camadas = new TiledMapTileLayer[CAMADAS_COLISAO_E_LUZ.length];
        for (int i = 0; i < CAMADAS_COLISAO_E_LUZ.length; i++) {
            Object camada = mapa.getLayers().get(CAMADAS_COLISAO_E_LUZ[i]);
            if (camada instanceof TiledMapTileLayer) camadas[i] = (TiledMapTileLayer) camada;
        }
        carregarVelocidades(camadas);
        Object camadaPz = mapa.getLayers().get("ProtectionZone");
        if (camadaPz instanceof TiledMapTileLayer) {
            TiledMapTileLayer pz = (TiledMapTileLayer) camadaPz;
            for (int cx = 0; cx < pz.getWidth(); cx++)
                for (int cy = 0; cy < pz.getHeight(); cy++) {
                    TiledMapTileLayer.Cell cell = pz.getCell(cx, cy);
                    if (cell != null && cell.getTile() != null) zonaProtegida.add(chaveCelula(cx, cy));
                }
        }
        Gdx.app.log("MapaPropriedades", zonaProtegida.size() + " SQM(s) de Protection Zone");
        MapLayer camadaTps = mapa.getLayers().get("Teleports");
        if (camadaTps != null) {
            for (MapObject obj : camadaTps.getObjects()) {
                // Qualquer tipo de objeto (retangulo, objeto de tile, ponto):
                // x/y (canto de baixo-esquerda, Y pra cima), width, height.
                // Destino: property dest (id da area na TeleportDests) ou
                // dest_x/dest_y - quem usa e' o servidor (mapa_tiled.py).
                MapProperties pt = obj.getProperties();
                if (pt.get("dest") == null && (pt.get("dest_x") == null || pt.get("dest_y") == null)) continue;
                float ox = numeroFloat(pt.get("x"), 0f), oy = numeroFloat(pt.get("y"), 0f);
                float w = numeroFloat(pt.get("width"), 0f), h = numeroFloat(pt.get("height"), 0f);
                int[] fx = faixaSqm(ox, w, tileWidth), fy = faixaSqm(oy, h, tileHeight);
                for (int cx = fx[0]; cx <= fx[1]; cx++)
                    for (int cy = fy[0]; cy <= fy[1]; cy++)
                        teleportes.add(chaveCelula(cx, cy));
            }
        }
        Gdx.app.log("MapaPropriedades", teleportes.size() + " SQM(s) de teleport na camada Teleports");
        int[][] anulaAPartirDe = calcularAnulacaoPorCelula(camadas);
        for (int i = 0; i < camadas.length; i++) {
            if (camadas[i] != null) escanearColisaoELuz(camadas[i], i, anulaAPartirDe);
        }

        MapLayer spawnLayer = mapa.getLayers().get("SpawnPoints");
        if (spawnLayer != null) {
            for (MapObject obj : spawnLayer.getObjects()) {
                String spawnId = obj.getProperties().get("spawn_id", String.class);
                // Point e Rectangle do Tiled caem os 2 como RectangleMapObject
                // nesse loader (Point vira retangulo 0x0) - um unico caminho
                // cobre as 2 formas que a spec pede (secao 5).
                if (spawnId == null || !(obj instanceof RectangleMapObject)) continue;
                // r.x/r.y ja vem em espaco de mundo Y-pra-cima, (x,y) =
                // canto inferior-esquerdo do objeto (loader ja flipou) - so'
                // falta centralizar/ancorar no SQM mais proximo. Math.round
                // (nao floor) no indice da celula - um objeto arrastado a
                // mao no Tiled raramente cai EXATO num multiplo de 16 (ex:
                // y=1119.88, 0.12px abaixo de 1120) - floor() joga isso pra
                // a celula de BAIXO por engano (SQM inteiro errado), round()
                // vai pra celula mais perto de verdade. Bug real: usuario
                // nascia 1 SQM abaixo do spawn marcado no Tiled por causa
                // disso.
                Rectangle r = ((RectangleMapObject) obj).getRectangle();
                float mundoX = (float) Math.round(r.x / Jogador.TILE) * Jogador.TILE + (Jogador.TILE / 2f);
                float mundoY = (float) Math.round(r.y / Jogador.TILE) * Jogador.TILE;
                spawns.put(spawnId, new Vector2(mundoX, mundoY));
            }
        }

        MapLayer camadaNPCs = mapa.getLayers().get("NPCSpawns");
        if (camadaNPCs != null) {
            for (MapObject obj : camadaNPCs.getObjects()) {
                String npcId = obj.getProperties().get("npc_id", String.class);
                if (npcId == null || npcId.trim().isEmpty() || !(obj instanceof RectangleMapObject)) continue;
                Rectangle r = ((RectangleMapObject) obj).getRectangle();
                float mundoX = (float) Math.round(r.x / Jogador.TILE) * Jogador.TILE + (Jogador.TILE / 2f);
                float mundoY = (float) Math.round(r.y / Jogador.TILE) * Jogador.TILE;
                npcSpawns.add(new NPCSpawn(npcId.trim(), mundoX, mundoY));
            }
        }

        MapLayer camadaMobs = mapa.getLayers().get("MobSpawns");
        if (camadaMobs != null) {
            for (MapObject obj : camadaMobs.getObjects()) {
                String mobId = obj.getProperties().get("mob_id", String.class);
                if (mobId == null || mobId.trim().isEmpty()) {
                    Gdx.app.log("MapaPropriedades", "MobSpawns: objeto sem a propriedade mob_id, ignorado");
                    continue;
                }
                Vector2 pos = posicaoDoObjeto(obj);
                if (pos == null) {
                    Gdx.app.log("MapaPropriedades", "MobSpawns: objeto '" + mobId + "' sem posicao (tipo "
                        + obj.getClass().getSimpleName() + "), ignorado");
                    continue;
                }
                // Mesmo snap pro SQM dos SpawnPoints/NPCSpawns acima.
                float mundoX = (float) Math.round(pos.x / Jogador.TILE) * Jogador.TILE + (Jogador.TILE / 2f);
                float mundoY = (float) Math.round(pos.y / Jogador.TILE) * Jogador.TILE;
                mobSpawns.add(new MobSpawn(mobId.trim().toLowerCase(), mundoX, mundoY,
                    numeroInt(obj.getProperties().get("spawn_range"), 0),
                    numeroFloat(obj.getProperties().get("respawn_time"), 120f)));
            }
        }

        if (camadaMobs == null) {
            Gdx.app.log("MapaPropriedades", "Camada de objetos 'MobSpawns' nao encontrada no mapa - nenhum mob");
        } else {
            Gdx.app.log("MapaPropriedades", mobSpawns.size() + " mob(s) na camada MobSpawns");
        }

        // Dimensoes (minimapa so' mostra a do player): retangulos na camada
        // de objetos "Dimensions". Sem ela, MapaMundo acha sozinho.
        MapLayer camadaDimensoes = mapa.getLayers().get("Dimensions");
        if (camadaDimensoes != null) {
            for (MapObject obj : camadaDimensoes.getObjects()) {
                if (obj instanceof RectangleMapObject) dimensoes.add(new Rectangle(((RectangleMapObject) obj).getRectangle()));
            }
        }

        // Areas de quest (camada de objetos "QuestAreas", property quest=...;
        // opcional layers="Buildings1,Buildings2" - sem ela, so' Buildings2):
        // os tiles dessas camadas ali dentro (ex: uma ponte) so' aparecem e so'
        // da' pra pisar depois da quest (MapaMundo.aplicarQuests / servidor.py).
        MapLayer camadaQuests = mapa.getLayers().get("QuestAreas");
        if (camadaQuests != null) {
            for (MapObject obj : camadaQuests.getObjects()) {
                Object q = obj.getProperties().get("quest");
                if (q == null || q.toString().trim().isEmpty() || !(obj instanceof RectangleMapObject)) continue;
                Object camadasQuest = obj.getProperties().get("layers");
                int livres = 0;
                Object fl = obj.getProperties().get("free_top_rows");
                if (fl != null) {
                    try { livres = Math.max(0, Math.round(Float.parseFloat(fl.toString()))); } catch (NumberFormatException ignorada) { }
                }
                areasQuest.add(new AreaQuest(q.toString().trim().toLowerCase(),
                    new Rectangle(((RectangleMapObject) obj).getRectangle()), camadasQuest != null ? camadasQuest.toString() : null, livres));
            }
        }

        Gdx.app.log("MapaPropriedades", camadaQuests == null
            ? "Sem camada de objetos 'QuestAreas' no mapa (nenhuma ponte/area de quest)."
            : areasQuest.size() + " area(s) de quest na camada QuestAreas");

        MapLayer camadaAreas = mapa.getLayers().get("AreasName");
        if (camadaAreas != null) {
            for (MapObject obj : camadaAreas.getObjects()) {
                String areaId = obj.getProperties().get("area_id", String.class);
                if (areaId == null || areaId.trim().isEmpty() || !(obj instanceof RectangleMapObject)) continue;
                // r ja vem em espaco de mundo Y-pra-cima (mesmo comentario
                // de classe) - diferente de SpawnPoints/NPCSpawns, usa o
                // retangulo CRU (sem arredondar pro grid de tile), e' uma
                // area de verdade, nao um ponto.
                Rectangle r = ((RectangleMapObject) obj).getRectangle();
                areasNomeadas.add(new AreaNomeada(areaId.trim(), new Rectangle(r)));
            }
        }
    }

    /** Nome da area (area_id da camada "AreasName") que contem (mundoX,
     * mundoY), ou null se nenhuma - usado pro banner "entrou numa area nova"
     * (ver AreaNomeUI/WorldScreen). Se 2 areas se sobrepoem nesse ponto,
     * devolve a 1a encontrada (ordem da camada no Tiled). */
    public String areaEm(float mundoX, float mundoY) {
        for (AreaNomeada area : areasNomeadas) {
            if (area.area.contains(mundoX, mundoY)) return area.nome;
        }
        return null;
    }

    /** Pra cada celula, guarda o INDICE da camada mais alta (maior indice em
     * CAMADAS_COLISAO_E_LUZ) cujo tile tem a property "anula_colisao_abaixo"
     * = true, -1 se nenhuma camada tiver isso nessa celula. Qualquer camada
     * COM INDICE MENOR nessa mesma celula tem sua colisao (solid + formas)
     * ignorada - usado pra chao elevado de verdade (ex: tabuleiro de ponte
     * em Buildings2) anular a colisao do que tem embaixo (ex: parede em
     * Buildings1), SEM afetar tiles de decoracao/sombra que nao tem essa
     * property marcada (ex: base de pilar/tocha, que continuam colidindo
     * mesmo com outro tile desenhado por cima na mesma celula - so' um tile
     * MARCADO explicitamente como "isso aqui e' chao que anula o que tem
     * embaixo" entra nessa conta). */
    private int[][] calcularAnulacaoPorCelula(TiledMapTileLayer[] camadas) {
        int largura = 0, altura = 0;
        for (TiledMapTileLayer c : camadas) {
            if (c != null) { largura = c.getWidth(); altura = c.getHeight(); break; }
        }
        int[][] anulaAPartirDe = new int[largura][altura];
        for (int[] linha : anulaAPartirDe) java.util.Arrays.fill(linha, -1);
        for (int idx = 0; idx < camadas.length; idx++) {
            TiledMapTileLayer camada = camadas[idx];
            if (camada == null) continue;
            for (int cy = 0; cy < altura; cy++) {
                for (int cx = 0; cx < largura; cx++) {
                    TiledMapTileLayer.Cell cell = camada.getCell(cx, cy);
                    if (cell == null || cell.getTile() == null) continue;
                    if (Boolean.TRUE.equals(cell.getTile().getProperties().get("anula_colisao_abaixo", Boolean.class))) {
                        anulaAPartirDe[cx][cy] = idx; // indice crescente - a ultima marcacao e' a mais alta
                    }
                }
            }
        }
        return anulaAPartirDe;
    }

    private void escanearColisaoELuz(TiledMapTileLayer camada, int idxCamada, int[][] anulaAPartirDe) {
        for (int cy = 0; cy < camada.getHeight(); cy++) {
            for (int cx = 0; cx < camada.getWidth(); cx++) {
                TiledMapTileLayer.Cell cell = camada.getCell(cx, cy);
                if (cell == null) continue;
                TiledMapTile tile = cell.getTile();
                if (tile == null) continue;
                MapProperties props = tile.getProperties();
                float worldCellX = cx * tileWidth;
                float worldCellY = cy * tileHeight;
                // Camadas ABAIXO de uma marcada anula_colisao_abaixo=true
                // nessa celula nao contribuem com colisao (solid/formas) -
                // ver calcularAnulacaoPorCelula. Luz/overlay/velocidade
                // continuam por camada, sem anulacao (nao foi pedido).
                boolean anulado = anulaAPartirDe[cx][cy] != -1 && idxCamada < anulaAPartirDe[cx][cy];
                // Protection Zone: nada naquele SQM tem colisao (passagem
                // secreta) - igual mapa_tiled.py.
                if (zonaProtegida.contains(chaveCelula(cx, cy))) anulado = true;

                if (!anulado && Boolean.TRUE.equals(props.get("solid", Boolean.class))) {
                    hitboxesMundo.add(new Rectangle(worldCellX, worldCellY, tileWidth, tileHeight));
                }

                if (!anulado) {
                    for (MapObject shape : tile.getObjects()) {
                        classificarForma(shape, cell, worldCellX, worldCellY);
                    }
                }

                if (Boolean.TRUE.equals(props.get("is_light", Boolean.class))) {
                    float offsetX = props.get("offset_x", 0f, Float.class);
                    float offsetY = props.get("offset_y", 0f, Float.class);
                    // Property tipo "color" no Tiled ja vem como Color de
                    // verdade (nao String) - TmxMapLoader ja converte o
                    // "#AARRGGBB" do .tsx sozinho.
                    Color cor = props.get("light_color", Color.WHITE, Color.class);
                    float raio = props.get("light_radius", 32f, Float.class);
                    luzes.add(new Luz(worldCellX + offsetX, worldCellY + offsetY, cor, raio));
                }

                if (propriedadeTrue(props, "Above_Player")) {
                    celulasAcimaDoPlayer.add(new CelulaAcima(camada, cx, cy, worldCellX, worldCellY, tile));
                }

                if (Boolean.TRUE.equals(props.get("overlap_transparency", Boolean.class))) {
                    celulasOverlay.add(new CelulaOverlay(cx, cy, worldCellX, worldCellY, tile));
                    // Removida da layer original - MapaMundo desenha essas
                    // celulas numa passada separada, por cima do player.
                    camada.setCell(cx, cy, null);
                }
            }
        }
    }

    /** Forma do Tile Collision Editor - o loader ja devolve x/y em espaco
     * Y-pra-cima relativo ao tile (canto inferior-esquerdo), mas essa forma
     * vem da DEFINICAO do tile (tileset), sem saber nada sobre flip/rotacao
     * que a CELULA especifica pode ter (Tiled: girar/espelhar um tile no
     * mapa nao duplica o tile, so marca flags na celula) - por isso
     * aplicarFlipRotacaoCelula() corrige isso antes de classificar.
     *
     * Formas FINAS (cerca/corrimao/ponta de 1px - um lado <=30% do tile, o
     * oposto cobrindo >=60%) viram bloqueio de 1 BORDA especifica da celula
     * (ColisaoGrid), nao da celula inteira - uma cerca entre 2 SQMs nao
     * pode impedir andar nos OUTROS 3 lados. Formas grandes/ambiguas
     * (rampas, cantos) continuam virando bloqueio de celula inteira -
     * aproximacao aceitavel ja que o jogo so se move em passos de 1 SQM. */
    private void classificarForma(MapObject shape, TiledMapTileLayer.Cell cell, float worldCellX, float worldCellY) {
        Rectangle bruta;
        if (shape instanceof RectangleMapObject) {
            bruta = ((RectangleMapObject) shape).getRectangle();
        } else if (shape instanceof PolygonMapObject) {
            bruta = ((PolygonMapObject) shape).getPolygon().getBoundingRectangle();
        } else {
            return;
        }
        Rectangle local = aplicarFlipRotacaoCelula(bruta, cell);

        boolean finoVertical = local.width <= tileWidth * LIMIAR_FINO && local.height >= tileHeight * LIMIAR_COBERTURA;
        boolean finoHorizontal = local.height <= tileHeight * LIMIAR_FINO && local.width >= tileWidth * LIMIAR_COBERTURA;

        if (finoVertical) {
            boolean direita = (local.x + local.width / 2f) > tileWidth / 2f;
            bordasFinas.add(new BordaFina(worldCellX, worldCellY, direita ? Borda.DIREITA : Borda.ESQUERDA));
            return;
        }
        if (finoHorizontal) {
            boolean topo = (local.y + local.height / 2f) > tileHeight / 2f;
            bordasFinas.add(new BordaFina(worldCellX, worldCellY, topo ? Borda.CIMA : Borda.BAIXO));
            return;
        }
        hitboxesMundo.add(new Rectangle(worldCellX + local.x, worldCellY + local.y, local.width, local.height));
    }

    /** Aplica a transformacao de flip/rotacao da CELULA (nao da definicao do
     * tile) numa forma de colisao local. As 8 formulas abaixo (uma por
     * combinacao valida de flipHorizontally/flipVertically/rotation - so' 8
     * sao alcancaveis, confirmado lendo o bytecode de
     * BaseTmxMapLoader::createTileLayerCell) foram derivadas rastreando
     * exatamente qual pixel da textura o OrthogonalTiledMapRenderer desenha
     * em cada canto da celula pra cada combinacao (decompilado
     * OrthogonalTiledMapRenderer::renderTileLayer - cada rotacao/flip troca
     * os indices de U/V entre os 4 cantos do quad de um jeito especifico) -
     * NAO e' a formula "intuitiva" de transpor+espelhar (essa primeira
     * tentativa saiu com a direcao de rotacao invertida, bug reportado pelo
     * usuario: 90/270 trocados, so' flip puro sem rotacao batia). */
    private Rectangle aplicarFlipRotacaoCelula(Rectangle local, TiledMapTileLayer.Cell cell) {
        boolean cellH = cell.getFlipHorizontally();
        boolean cellV = cell.getFlipVertically();
        int rotacao = cell.getRotation();
        if (!cellH && !cellV && rotacao == 0) return local;

        float s = tileWidth; // tile sempre quadrado aqui (tileWidth == tileHeight)
        float x0 = local.x, y0 = local.y, x1 = local.x + local.width, y1 = local.y + local.height;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float lx : new float[]{x0, x1}) {
            for (float ly : new float[]{y0, y1}) {
                float sx, sy;
                if (rotacao == 1) { // ROTATE_90 - unica combinacao alcancavel: flipH=F,flipV=F
                    sx = s - ly; sy = lx;
                } else if (rotacao == 3) { // ROTATE_270
                    if (cellH && !cellV) { sx = ly; sy = lx; }
                    else if (!cellH && !cellV) { sx = ly; sy = s - lx; }
                    else { sx = s - ly; sy = s - lx; } // !cellH && cellV
                } else if (rotacao == 0) {
                    sx = cellH ? s - lx : lx;
                    sy = cellV ? s - ly : ly;
                } else { // ROTATE_180 - TmxMapLoader nunca produz isso (180 sai
                    // como flipH&&flipV&&rotation=0), fallback defensivo.
                    sx = s - lx; sy = s - ly;
                }
                minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
                minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
            }
        }
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    /** speed_modifier do SQM sob (mundoX, mundoY) - pes do player/mob, na
     * base do SQM -, 1.0 se nenhum tile ali tiver a property. Pre-calculado
     * no construtor (velocidadePorCelula). */
    public float velocidadeEm(float mundoX, float mundoY) {
        int cx = (int) Math.floor(mundoX / tileWidth);
        // +meio SQM: a ancora fica exatamente na borda de baixo do SQM, e um
        // arredondamento de float ali caia no SQM de baixo.
        int cy = (int) Math.floor((mundoY + tileHeight / 2f) / tileHeight);
        Float mod = velocidadePorCelula.get(chaveCelula(cx, cy));
        return mod != null ? mod : 1f;
    }

    /** SQMs que o objeto cobre em pelo menos metade (meio fora do grid nao
     * pega o SQM do lado); ponto/objeto pequeno = o do meio. Igual
     * mapa_tiled.py::faixa. */
    private static int[] faixaSqm(float inicio, float tamanho, int sqm) {
        int a = Math.round(inicio / sqm), b = Math.round((inicio + tamanho) / sqm) - 1;
        if (b < a) a = b = (int) Math.floor((inicio + tamanho / 2f) / sqm);
        return new int[]{a, b};
    }

    /** SQM sob (mundoX, mundoY) - pes, igual velocidadeEm - e' um teleport. */
    public boolean ehTeleporte(float mundoX, float mundoY) {
        if (teleportes.isEmpty()) return false;
        int cx = (int) Math.floor(mundoX / tileWidth);
        int cy = (int) Math.floor((mundoY + tileHeight / 2f) / tileHeight);
        return teleportes.contains(chaveCelula(cx, cy));
    }

    /** SQM sob (mundoX, mundoY) - pes, igual velocidadeEm - e' Protection Zone. */
    public boolean naZonaProtegida(float mundoX, float mundoY) {
        if (zonaProtegida.isEmpty()) return false;
        int cx = (int) Math.floor(mundoX / tileWidth);
        int cy = (int) Math.floor((mundoY + tileHeight / 2f) / tileHeight);
        return zonaProtegida.contains(chaveCelula(cx, cy));
    }

    private static long chaveCelula(int cx, int cy) { return ((long) cx << 32) ^ (cy & 0xffffffffL); }

    /** Todas as camadas de tile (mesmas da colisao); a mais de CIMA com
     * speed_modifier na celula decide (ex: ponte em cima de lama). A
     * property pode estar como float, int ou string no Tiled. Mesma regra de
     * mapa_tiled.py no servidor. */
    private void carregarVelocidades(TiledMapTileLayer[] camadas) {
        for (TiledMapTileLayer camada : camadas) {
            if (camada == null) continue;
            for (int cx = 0; cx < camada.getWidth(); cx++) {
                for (int cy = 0; cy < camada.getHeight(); cy++) {
                    TiledMapTileLayer.Cell cell = camada.getCell(cx, cy);
                    if (cell == null || cell.getTile() == null) continue;
                    Object valor = cell.getTile().getProperties().get("speed_modifier");
                    if (valor == null) continue;
                    float mod;
                    try { mod = valor instanceof Number ? ((Number) valor).floatValue() : Float.parseFloat(valor.toString().trim()); }
                    catch (NumberFormatException e) { continue; }
                    if (mod > 0f) velocidadePorCelula.put(chaveCelula(cx, cy), mod);
                }
            }
        }
        velocidadePorCelula.values().removeIf(v -> v == 1f);
        Gdx.app.log("Mapa", velocidadePorCelula.size() + " SQM(s) com speed_modifier");
    }

    /** Propriedade numerica do Tiled (int/float/string, dependendo de como
     * foi criada no editor) -> int. */
    private static int numeroInt(Object valor, int padrao) {
        if (valor instanceof Number) return ((Number) valor).intValue();
        try {
            return valor == null ? padrao : (int) Float.parseFloat(valor.toString().trim());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private static float numeroFloat(Object valor, float padrao) {
        if (valor instanceof Number) return ((Number) valor).floatValue();
        try {
            return valor == null ? padrao : Float.parseFloat(valor.toString().trim());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    /**
     * Canto inferior-esquerdo do objeto em espaco de mundo (Y pra cima).
     * Point do Tiled chega como RectangleMapObject 0x0 em algumas versoes do
     * libGDX e como PointMapObject em outras (que nem existe nas antigas) -
     * pra nao depender da versao, objeto que nao for retangulo usa as
     * propriedades "x"/"y" que o TmxMapLoader preenche (ja com Y invertido).
     */
    private static Vector2 posicaoDoObjeto(MapObject obj) {
        if (obj instanceof RectangleMapObject) {
            Rectangle r = ((RectangleMapObject) obj).getRectangle();
            return new Vector2(r.x, r.y);
        }
        Object x = obj.getProperties().get("x");
        Object y = obj.getProperties().get("y");
        if (x instanceof Number && y instanceof Number) {
            return new Vector2(((Number) x).floatValue(), ((Number) y).floatValue());
        }
        return null;
    }
}
