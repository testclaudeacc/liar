"""
Leitura do World.tmx pelo SERVIDOR (sem depender do que o client manda).

Porta 1:1 da logica do client libGDX:
  - mapa/MapaPropriedades.java  (colisao solid + formas do Tile Collision
    Editor com flip/rotacao da celula, anula_colisao_abaixo, bordas finas,
    SpawnPoints, NPCSpawns, MobSpawns)
  - mapa/ColisaoGrid.java       (rasterizacao em SQMs no espaco "cru",
    bits/bits_leste/bits_baixo e o fingerprint MD5)

Espacos de coordenada:
  - "mundo" = libGDX, Y pra cima (o TmxMapLoader flipa tudo).
  - "cru"   = o que o servidor usa; com o offset 0,0 do client e' igual ao
    pixel do proprio Tiled (Y pra baixo): cru_y = altura_mapa - mundo_y.
As contas sao feitas no espaco "mundo", igualzinho ao Java, e so' convertidas
pro "cru" no fim - assim os mesmos arredondamentos dao o mesmo resultado.

Uso: carregar_mapa('maps/World.tmx') -> dict (ver final do arquivo).
Tambem le o speed_modifier dos tiles (MapaPropriedades.velocidadeEm).
"""

import base64
import gzip
import hashlib
import math
import os
import struct
import zlib
import xml.etree.ElementTree as ET

SQM = 16
PADDING_SQM = 2
LIMIAR_FINO = 0.3
LIMIAR_COBERTURA = 0.6
CAMADAS_COLISAO = ("Ground", "Buildings1", "Buildings2", "Roofs", "Pillars")
# speed_modifier: todas as camadas de colisao; a mais de CIMA com a property
# na celula decide (igual MapaPropriedades.carregarVelocidades).
CAMADAS_VELOCIDADE = CAMADAS_COLISAO

FLIP_H = 0x80000000
FLIP_V = 0x40000000
FLIP_D = 0x20000000
MASCARA_GID = ~(FLIP_H | FLIP_V | FLIP_D) & 0xFFFFFFFF


def _f32(v):
    """Arredonda pra float de 32 bits (o Java faz essas contas em float)."""
    return struct.unpack('f', struct.pack('f', v))[0]


def _java_round(v):
    """Math.round(float) do Java: floor(x + 0.5)."""
    return int(math.floor(v + 0.5))


def _bool_prop(props, nome):
    return str(props.get(nome, '')).strip().lower() == 'true'


def _ler_props(elem):
    props = {}
    if elem is None: return props
    bloco = elem.find('properties')
    if bloco is None: return props
    for p in bloco.findall('property'):
        valor = p.get('value')
        if valor is None: valor = (p.text or '')
        props[p.get('name')] = valor
    return props


# ---------------------------------------------------------------- tilesets

class _Tile:
    __slots__ = ('props', 'formas', 'altura')

    def __init__(self, props, formas, altura):
        self.props = props
        self.formas = formas  # [(x, y, w, h)] em coords do Tiled (Y pra baixo) relativas ao tile
        self.altura = altura


def _formas_do_tile(elem_tile):
    formas = []
    grupo = elem_tile.find('objectgroup')
    if grupo is None: return formas
    for obj in grupo.findall('object'):
        x = float(obj.get('x', 0)); y = float(obj.get('y', 0))
        if obj.find('ellipse') is not None or obj.find('polyline') is not None or obj.find('point') is not None:
            continue  # o client (RectangleMapObject/PolygonMapObject) ignora esses tipos
        poly = obj.find('polygon')
        if poly is not None:
            pts = [tuple(map(float, par.split(','))) for par in poly.get('points', '').split()]
            if not pts: continue
            xs = [x + px for px, _ in pts]; ys = [y + py for _, py in pts]
            formas.append((min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys)))
        else:
            formas.append((x, y, float(obj.get('width', 0)), float(obj.get('height', 0))))
    return formas


def _carregar_tileset(elem, pasta):
    """-> (firstgid, {id_local: _Tile})"""
    firstgid = int(elem.get('firstgid'))
    fonte = elem.get('source')
    if fonte:
        caminho = os.path.join(pasta, fonte)
        elem = ET.parse(caminho).getroot()
    altura_ts = int(elem.get('tileheight', SQM))
    tiles = {}
    for t in elem.findall('tile'):
        img = t.find('image')
        altura = int(img.get('height')) if img is not None and img.get('height') else altura_ts
        tiles[int(t.get('id'))] = _Tile(_ler_props(t), _formas_do_tile(t), altura)
    return firstgid, tiles


# ---------------------------------------------------------------- camadas

def _decodificar_data(data_elem, largura, altura):
    """Lista de gids (linha a linha, de cima pra baixo) de uma <data>."""
    codificacao = data_elem.get('encoding')
    compressao = data_elem.get('compression')
    if codificacao == 'csv':
        return [int(v) for v in (data_elem.text or '').replace('\n', '').split(',') if v.strip()]
    if codificacao == 'base64':
        bruto = base64.b64decode((data_elem.text or '').strip())
        if compressao == 'zlib': bruto = zlib.decompress(bruto)
        elif compressao == 'gzip': bruto = gzip.decompress(bruto)
        elif compressao: raise ValueError(f"compressao '{compressao}' nao suportada (use CSV, zlib ou gzip no Tiled)")
        return list(struct.unpack('<%dI' % (len(bruto) // 4), bruto))
    return [int(t.get('gid', 0)) for t in data_elem.findall('tile')]


def _gids_da_camada(camada, largura_mapa, altura_mapa):
    """{(col, linha): gid} - linha de cima pra baixo (como no Tiled)."""
    data = camada.find('data')
    if data is None: return {}
    saida = {}
    chunks = data.findall('chunk')
    if chunks:  # mapa infinito
        for ch in chunks:
            cx0, cy0 = int(ch.get('x')), int(ch.get('y'))
            w, h = int(ch.get('width')), int(ch.get('height'))
            gids = _decodificar_data(_ChunkComoData(ch, data), w, h)
            for i, gid in enumerate(gids):
                if gid: saida[(cx0 + i % w, cy0 + i // w)] = gid
        return saida
    gids = _decodificar_data(data, largura_mapa, altura_mapa)
    w = int(camada.get('width', largura_mapa))
    for i, gid in enumerate(gids):
        if gid: saida[(i % w, i // w)] = gid
    return saida


class _ChunkComoData:
    """Chunk de mapa infinito herda encoding/compression do <data> pai."""
    def __init__(self, chunk, data_pai):
        self.text = chunk.text
        self._chunk = chunk
        self._pai = data_pai

    def get(self, chave, padrao=None):
        return self._pai.get(chave, padrao)

    def findall(self, nome):
        return self._chunk.findall(nome)


# ------------------------------------------------- flip/rotacao (libGDX)

def _flags_libgdx(gid_bruto):
    """Mesma traducao do BaseTmxMapLoader::createTileLayerCell ->
    (flipH, flipV, rotacao) da celula. rotacao: 0, 1 (90), 3 (270)."""
    h = bool(gid_bruto & FLIP_H); v = bool(gid_bruto & FLIP_V); d = bool(gid_bruto & FLIP_D)
    if d:
        if h and v: return True, False, 3
        if h: return False, False, 3
        if v: return False, False, 1
        return False, True, 3
    return h, v, 0


def _aplicar_flip_rotacao(local, flip_h, flip_v, rotacao, s):
    """MapaPropriedades.aplicarFlipRotacaoCelula (forma local Y pra cima)."""
    if not flip_h and not flip_v and rotacao == 0: return local
    x, y, w, h = local
    x0, y0, x1, y1 = x, y, _f32(x + w), _f32(y + h)
    xs, ys = [], []
    for lx in (x0, x1):
        for ly in (y0, y1):
            if rotacao == 1:
                sx, sy = s - ly, lx
            elif rotacao == 3:
                if flip_h and not flip_v: sx, sy = ly, lx
                elif not flip_h and not flip_v: sx, sy = ly, s - lx
                else: sx, sy = s - ly, s - lx
            elif rotacao == 0:
                sx = s - lx if flip_h else lx
                sy = s - ly if flip_v else ly
            else:
                sx, sy = s - lx, s - ly
            xs.append(_f32(sx)); ys.append(_f32(sy))
    return (min(xs), min(ys), _f32(max(xs) - min(xs)), _f32(max(ys) - min(ys)))


# ---------------------------------------------------------------- leitura

def carregar_mapa(caminho_tmx):
    pasta = os.path.dirname(os.path.abspath(caminho_tmx))
    raiz = ET.parse(caminho_tmx).getroot()
    tw = int(raiz.get('tilewidth')); th = int(raiz.get('tileheight'))
    largura = int(raiz.get('width')); altura = int(raiz.get('height'))
    altura_px = float(altura * th)

    tilesets = sorted((_carregar_tileset(ts, pasta) for ts in raiz.findall('tileset')), key=lambda t: t[0])

    def tile_do_gid(gid):
        tile = None
        for firstgid, tiles in tilesets:
            if gid >= firstgid:
                tile = tiles.get(gid - firstgid)
            else:
                break
        return tile

    # Camadas de colisao (so' as de topo, igual mapa.getLayers().get(nome)).
    camadas_por_nome = {}
    for c in raiz.findall('layer'):
        camadas_por_nome.setdefault(c.get('name'), c)
    camadas = []
    for nome in CAMADAS_COLISAO:
        c = camadas_por_nome.get(nome)
        camadas.append(_gids_da_camada(c, largura, altura) if c is not None else None)

    def celula_mundo(col, linha):
        # TmxMapLoader flipa Y: celula (col, linha do Tiled) -> (cx, cy) com cy de baixo pra cima.
        return col, altura - 1 - linha

    # anula_colisao_abaixo: indice da camada mais alta marcada por celula.
    anula = {}
    for idx, gids in enumerate(camadas):
        if gids is None: continue
        for pos, bruto in gids.items():
            tile = tile_do_gid(bruto & MASCARA_GID)
            if tile is not None and _bool_prop(tile.props, 'anula_colisao_abaixo'):
                anula[pos] = idx

    hitboxes = []   # (x, y, w, h) mundo Y pra cima
    bordas = []     # (worldCellX, worldCellY, lado)
    for idx, gids in enumerate(camadas):
        if gids is None: continue
        for (col, linha), bruto in gids.items():
            tile = tile_do_gid(bruto & MASCARA_GID)
            if tile is None: continue
            if (col, linha) in anula and idx < anula[(col, linha)]: continue
            cx, cy = celula_mundo(col, linha)
            wcx, wcy = float(cx * tw), float(cy * th)
            if _bool_prop(tile.props, 'solid'):
                hitboxes.append((wcx, wcy, float(tw), float(th)))
            flip_h, flip_v, rot = _flags_libgdx(bruto)
            for (fx, fy, fw, fh) in tile.formas:
                # Tiled (Y pra baixo, relativo ao tile) -> libGDX (Y pra cima).
                local = (_f32(fx), _f32(tile.altura - fy - fh), _f32(fw), _f32(fh))
                local = _aplicar_flip_rotacao(local, flip_h, flip_v, rot, float(tw))
                lx, ly, lw, lh = local
                fino_v = lw <= tw * LIMIAR_FINO and lh >= th * LIMIAR_COBERTURA
                fino_h = lh <= th * LIMIAR_FINO and lw >= tw * LIMIAR_COBERTURA
                if fino_v:
                    bordas.append((wcx, wcy, 'DIREITA' if _f32(lx + lw / 2.0) > tw / 2.0 else 'ESQUERDA'))
                elif fino_h:
                    bordas.append((wcx, wcy, 'CIMA' if _f32(ly + lh / 2.0) > th / 2.0 else 'BAIXO'))
                else:
                    hitboxes.append((_f32(wcx + lx), _f32(wcy + ly), lw, lh))

    grade = _rasterizar(hitboxes, bordas, altura_px)

    # ---- speed_modifier por SQM ----
    # {(col, linha): mult} so' com os SQMs != 1.0. (col, linha) do Tiled e'
    # o mesmo SQM que o servidor usa (tile_de no espaco "cru").
    velocidades = {}
    for nome in CAMADAS_VELOCIDADE:  # de baixo pra cima: a de cima sobrescreve
        c = camadas_por_nome.get(nome)
        if c is None: continue
        for pos, bruto in _gids_da_camada(c, largura, altura).items():
            tile = tile_do_gid(bruto & MASCARA_GID)
            if tile is None or 'speed_modifier' not in tile.props: continue
            try: mult = float(str(tile.props['speed_modifier']).strip())
            except ValueError: continue
            if mult > 0: velocidades[pos] = mult
    velocidades = {pos: v for pos, v in velocidades.items() if v != 1.0}

    # ---- Objetos (SpawnPoints, NPCSpawns, MobSpawns) ----
    def objetos(nome_camada):
        for grupo in raiz.findall('objectgroup'):
            if grupo.get('name') == nome_camada:
                return grupo.findall('object')
        return []

    def ancora_mundo(obj):
        # RectangleMapObject do libGDX: y = altura_mapa - y_tiled - height.
        x = float(obj.get('x', 0)); y = float(obj.get('y', 0)); h = float(obj.get('height', 0) or 0)
        ry = _f32(altura_px - y - h)
        mx = _f32(_java_round(_f32(x / SQM)) * SQM + SQM / 2.0)
        my = _f32(_java_round(_f32(ry / SQM)) * SQM)
        return mx, my

    spawns = {}
    for obj in objetos('SpawnPoints'):
        sid = _ler_props(obj).get('spawn_id')
        if sid: spawns.setdefault(sid, ancora_mundo(obj))
    spawn = spawns.get('initial') or (next(iter(spawns.values())) if spawns else None)
    spawn_cru = [spawn[0], altura_px - spawn[1]] if spawn else None

    npcs = []
    for obj in objetos('NPCSpawns'):
        npc_id = (_ler_props(obj).get('npc_id') or '').strip()
        if not npc_id: continue
        mx, my = ancora_mundo(obj)
        chave = f"{npc_id}_{_java_round(mx / SQM)}_{_java_round(my / SQM)}"
        npcs.append({'id': chave, 'npc_id': npc_id, 'x': mx, 'y': altura_px - my, 'floor': 1})

    mobs = []
    for obj in objetos('MobSpawns'):
        props = _ler_props(obj)
        mob_id = (props.get('mob_id') or '').strip().lower()
        if not mob_id: continue
        mx, my = ancora_mundo(obj)
        cru_x, cru_y = mx, _f32(altura_px - my)
        try: alcance = int(float(props.get('spawn_range', 0)))
        except ValueError: alcance = 0
        try: respawn = float(props.get('respawn_time', 120))
        except ValueError: respawn = 120.0
        mobs.append({'id': f"{mob_id}_{_java_round(cru_x)}_{_java_round(cru_y)}", 'type': mob_id,
                     'spawn_range': alcance, 'respawn_time': respawn})

    # Areas de quest (camada "QuestAreas", property quest=...): SQMs (coluna,
    # linha do Tiled - o mesmo tile_de do servidor) que so' quem fez a quest pisa.
    quest_areas = []
    for obj in objetos('QuestAreas'):
        quest = (_ler_props(obj).get('quest') or '').strip().lower()
        if not quest: continue
        x = float(obj.get('x', 0)); y = float(obj.get('y', 0))
        w = float(obj.get('width', 0) or 0); h = float(obj.get('height', 0) or 0)
        if w <= 0 or h <= 0: continue
        cells = set()
        for col in range(int(x // SQM), int((x + w - 1) // SQM) + 1):
            for lin in range(int(y // SQM), int((y + h - 1) // SQM) + 1):
                cells.add((col, lin))
        quest_areas.append({'quest': quest, 'cells': cells})

    return {'grade': grade, 'mobs': mobs, 'npcs': npcs, 'spawn': spawn_cru, 'velocidades': velocidades,
            'quest_areas': quest_areas}


def _rasterizar(hitboxes, bordas, altura_px):
    """ColisaoGrid.java -> {'fp','x0','y0','w','h','bits','bits_leste','bits_baixo'}"""
    def cru_y(mundo_y): return _f32(altura_px - mundo_y)

    cru = []
    min_x = min_y = float('inf'); max_x = max_y = float('-inf')
    for (x, y, w, h) in hitboxes:
        rx0, rx1 = x, _f32(x + w)
        ry0, ry1 = cru_y(y), cru_y(_f32(y + h))
        xmin, xmax = min(rx0, rx1), max(rx0, rx1)
        ymin, ymax = min(ry0, ry1), max(ry0, ry1)
        cru.append((xmin, ymin, _f32(xmax - xmin), _f32(ymax - ymin)))
        min_x = min(min_x, xmin); min_y = min(min_y, ymin); max_x = max(max_x, xmax); max_y = max(max_y, ymax)
    for (wx, wy, _) in bordas:
        rx0, rx1 = wx, _f32(wx + SQM)
        ry0, ry1 = cru_y(wy), cru_y(_f32(wy + SQM))
        min_x = min(min_x, rx0, rx1); min_y = min(min_y, ry0, ry1)
        max_x = max(max_x, rx0, rx1); max_y = max(max_y, ry0, ry1)

    if not cru and not bordas:
        return {'fp': _fingerprint(0, 0, 0, 0, b'', b'', b''), 'x0': 0, 'y0': 0, 'w': 0, 'h': 0,
                'bits': b'', 'bits_leste': b'', 'bits_baixo': b''}

    min_x = _f32(min_x - PADDING_SQM * SQM); min_y = _f32(min_y - PADDING_SQM * SQM)
    max_x = _f32(max_x + PADDING_SQM * SQM); max_y = _f32(max_y + PADDING_SQM * SQM)
    x0 = int(math.floor(_f32(min_x / SQM))); y0 = int(math.floor(_f32(min_y / SQM)))
    w = int(math.ceil(_f32(max_x / SQM))) - x0 + 1
    h = int(math.ceil(_f32(max_y / SQM))) - y0 + 1
    n = (w * h + 7) // 8
    bits, leste, baixo = bytearray(n), bytearray(n), bytearray(n)

    def marcar(plano, x, y):
        if x < 0 or y < 0 or x >= w or y >= h: return
        i = y * w + x
        plano[i >> 3] |= (1 << (i & 7))

    for (rx, ry, rw, rh) in cru:
        cx_ini = int(math.floor(_f32(rx / SQM))) - x0
        cx_fim = int(math.ceil(_f32(_f32(rx + rw) / SQM))) - 1 - x0
        cy_ini = int(math.floor(_f32(ry / SQM))) - y0
        cy_fim = int(math.ceil(_f32(_f32(ry + rh) / SQM))) - 1 - y0
        for cy in range(max(0, cy_ini), min(h - 1, cy_fim) + 1):
            for cx in range(max(0, cx_ini), min(w - 1, cx_fim) + 1):
                marcar(bits, cx, cy)

    def cel_x(mundo_x): return int(math.floor(mundo_x / SQM))
    def cel_y(mundo_y): return int(math.floor(cru_y(mundo_y) / SQM))

    for (wx, wy, lado) in bordas:
        meio_x, meio_y = _f32(wx + SQM / 2.0), _f32(wy + SQM / 2.0)
        dx = fx = meio_x; dy = fy = meio_y
        if lado == 'DIREITA': dx, fx = _f32(wx + SQM - 1), _f32(wx + SQM + 1)
        elif lado == 'ESQUERDA': dx, fx = _f32(wx + 1), _f32(wx - 1)
        elif lado == 'CIMA': dy, fy = _f32(wy + SQM - 1), _f32(wy + SQM + 1)
        else: dy, fy = _f32(wy + 1), _f32(wy - 1)
        ixd, ixf, iyd, iyf = cel_x(dx), cel_x(fx), cel_y(dy), cel_y(fy)
        if ixd != ixf: marcar(leste, min(ixd, ixf) - x0, iyd - y0)
        elif iyd != iyf: marcar(baixo, ixd - x0, min(iyd, iyf) - y0)

    bits, leste, baixo = bytes(bits), bytes(leste), bytes(baixo)
    return {'fp': _fingerprint(x0, y0, w, h, bits, leste, baixo), 'x0': x0, 'y0': y0, 'w': w, 'h': h,
            'bits': bits, 'bits_leste': leste, 'bits_baixo': baixo}


def _fingerprint(x0, y0, w, h, bits, leste, baixo):
    """Mesmo MD5 de ColisaoGrid.fingerprint() - da' pra conferir se a grade
    do servidor bate com a que o client calculou."""
    md = hashlib.md5()
    md.update(struct.pack('>iiii', x0, y0, w, h))
    md.update(bits); md.update(leste); md.update(baixo)
    return md.hexdigest()
