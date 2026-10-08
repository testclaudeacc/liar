import eventlet
eventlet.monkey_patch()
# psycopg2 bloqueia o processo inteiro durante cada consulta (o eventlet nao
# consegue trocar de tarefa no meio dela): login, join e o autosave de todo
# mundo congelavam o jogo de todos. O psycogreen deixa as consultas
# "cooperativas" (pip install psycogreen).
try:
    from psycogreen.eventlet import patch_psycopg
    patch_psycopg()
except ImportError:
    print("[AVISO] psycogreen nao instalado: o banco vai travar o servidor durante as consultas (pip install psycogreen)")

import os
import json
import re
import psycopg2
from psycopg2 import pool
import smtplib
import random
import traceback
import time
import math
import uuid
import hmac
import hashlib
import secrets
import base64
import heapq
from email.mime.text import MIMEText
from flask import Flask, request, jsonify
from flask_cors import CORS
from flask_socketio import SocketIO, emit, join_room, leave_room, disconnect
from werkzeug.security import generate_password_hash, check_password_hash
from dotenv import load_dotenv
from eventlet.queue import Queue
from eventlet import tpool

# Hash de senha (pbkdf2) e' de proposito lento (~centenas de ms de CPU). No
# eventlet isso rodava no MESMO fio do jogo: cada login/cadastro travava o
# mundo pra todo mundo por esse tempo (e um monte de logins seguidos = lag
# geral). tpool roda numa thread de verdade, fora do loop do jogo.
def _hash_senha(senha):
    return tpool.execute(generate_password_hash, senha)

def _conferir_senha(hash_salvo, senha):
    return tpool.execute(check_password_hash, hash_salvo, senha)

load_dotenv()
GMAIL_SENDER = os.getenv("GMAIL_SENDER")
GMAIL_APP_PASSWORD = os.getenv("GMAIL_APP_PASSWORD")
# Versao exigida do client (rede/ServerConfig.java::CLIENT_VERSION). Subir as
# DUAS juntas a cada mudanca grande: APK antigo passa a ver "Version outdated".
SERVER_VERSION = "v0.19"
CHUNK_SIZE = 800
DIR_MAP = {0: 'down', 1: 'up', 2: 'left', 3: 'right'}

# --- CONFIGURAÇÃO DO POOL DO POSTGRESQL ---
try:
    db_pool = psycopg2.pool.SimpleConnectionPool(
        1, 50, # Min 1, Max 50 conexões simultâneas
        user=os.getenv("DB_USER", "postgres"),
        password=os.getenv("DB_PASS", "postgres"),
        host=os.getenv("DB_HOST", "localhost"),
        port=os.getenv("DB_PORT", "5432"),
        database=os.getenv("DB_NAME", "postgres")
    )
    if db_pool:
        print("Pool de conexões PostgreSQL criado com sucesso.")
except Exception as e:
    print(f"Erro fatal ao conectar ao PostgreSQL: {e}")

MOB_DB = {
    "rotworm": {"hp": 50, "xp": 15, "attack": 3.0, "drops": [], "currency": {"min": 5, "max": 10}},
}

# ---------------------------------------------------------------------------
# BESTIARY. Cada tipo do MOB_DB vira uma entrada (numero = ordem no MOB_DB).
# Matar libera aos poucos (por personagem, contado em skills['bestiary']):
#   1   -> descoberto: numero, imagem e nome
#   20  -> medalha de cobre: HP
#   100 -> medalha de prata: lootpool (so' os itens)
#   500 -> medalha de ouro: chances do loot + 5% de dano e defesa contra ele
# O servidor so' manda o que ja foi liberado (montar_bestiario).
# ---------------------------------------------------------------------------
BESTIARIO_MARCOS = (1, 20, 100, 500)
BESTIARIO_BONUS_OURO = 0.05

def bestiario_kills(p, tipo):
    # Da CONTA (users.bestiary): todos os personagens dela somam no mesmo.
    b = p.get('bestiary')
    if not isinstance(b, dict): return 0
    try: return int(b.get(tipo, 0))
    except (TypeError, ValueError): return 0

def bestiario_nivel(kills):
    # 0 = nao descoberto, 1 = descoberto, 2 = cobre, 3 = prata, 4 = ouro
    return sum(1 for m in BESTIARIO_MARCOS if kills >= m)

def montar_bestiario(p):
    entradas = []
    for i, (tipo, info) in enumerate(MOB_DB.items()):
        kills = bestiario_kills(p, tipo)
        nivel = bestiario_nivel(kills)
        e = {"id": i + 1, "tier": nivel}
        if nivel >= 1:
            e["type"] = tipo
            e["name"] = info.get("name", tipo.capitalize())
            e["kills"] = kills
        if nivel >= 2:
            e["hp"] = info.get("hp", 40)
        if nivel >= 3:
            loot = []
            for d in info.get("drops", []):
                item = validate_item(d.get("item"))
                if not item: continue
                linha = {"item": item}
                if nivel >= 4: linha["chance"] = float(d.get("chance", 0.0))
                loot.append(linha)
            e["loot"] = loot
            faixa = info.get("currency") or {}
            if int(faixa.get("max", 0) or 0) > 0:
                e["currency"] = {"min": int(faixa.get("min", 0) or 0), "max": int(faixa.get("max", 0) or 0)}
        entradas.append(e)
    return {"entries": entradas, "milestones": list(BESTIARIO_MARCOS)}

def bestiario_registrar_kill(sid, p, tipo):
    # Conta a morte; passou de um marco: avisa o client e manda o bestiary novo.
    if tipo not in MOB_DB: return
    b = p.get('bestiary')
    if not isinstance(b, dict):
        b = {}
        p['bestiary'] = b
    antes = bestiario_kills(p, tipo)
    b[tipo] = antes + 1
    nivel_antes, nivel_depois = bestiario_nivel(antes), bestiario_nivel(antes + 1)
    if nivel_depois > nivel_antes:
        nome = MOB_DB[tipo].get("name", tipo.capitalize())
        socketio.emit('bestiary_unlock', {"type": tipo, "name": nome, "tier": nivel_depois}, room=sid)
        socketio.emit('bestiary', montar_bestiario(p), room=sid)
    else:
        # So' o contador (a ficha aberta atualiza na hora).
        socketio.emit('bestiary_kills', {"type": tipo, "kills": antes + 1}, room=sid)

def bestiario_ouro(p, tipo):
    return bestiario_kills(p, tipo) >= BESTIARIO_MARCOS[-1]

NPC_DB = {
    # price (em cobre) + quest: botao "Pay" no dialogo (handle_npc_pay). Pagar
    # libera a quest pro personagem (ex: a ponte da area "kharon" no Tiled).
    "kharon": {"name": "Kharon", "price": 200, "quest": "kharon"},
}
NPC_PAY_DISTANCIA_SQM = 4
NPC_TEXTO_SEM_DINHEIRO = "Do not try to deceive me, mortal."
NPC_TEXTO_PAGO = "You shall pass."

# Areas de quest (camada de objetos "QuestAreas" do Tiled, property quest=...):
# so' quem fez a quest anda nesses SQMs (o client tambem so' desenha a ponte
# pra quem fez). mapa -> [{"quest": id, "cells": set((col, linha))}]
areas_de_quest = {}

def quests_feitas(p):
    estado = p.get('npc_dialogue_state')
    if not isinstance(estado, dict): return []
    return [k[6:] for k, v in estado.items() if str(k).startswith('quest:') and v]

# Protection Zone (camada ProtectionZone do World.tmx): {map_id: set de SQMs}.
# So' vale no andar 1 (o do mapa do servidor).
zonas_protegidas = {}

def na_pz(obj, tile=None):
    pz = zonas_protegidas.get(obj.get('mapa'))
    if not pz or int(obj.get('floor', 1) or 1) != 1: return False
    if tile is None: tile = tile_de(obj.get('pos_x', 0), obj.get('pos_y', 0))
    return tile in pz

# Teleports (camada Teleports do World.tmx): {map_id: {SQM do TP: SQM destino}}.
teleportes = {}
TP_COOLDOWN_SEG = 1.0
_DELTA_DIRECAO = {'up': (0, -1), 'down': (0, 1), 'left': (-1, 0), 'right': (1, 0)}

class _Bloqueados:
    """Dois conjuntos de SQMs bloqueados juntos, sem copiar (a PZ pode ser grande)."""
    __slots__ = ('a', 'b')
    def __init__(self, a, b): self.a, self.b = a, b
    def __contains__(self, t): return t in self.a or t in self.b

def bloqueado_por_quest(p, tile):
    feitas = None
    for area in areas_de_quest.get(p.get('mapa'), ()):
        if tile in area['cells']:
            if feitas is None: feitas = set(quests_feitas(p))
            if area['quest'] not in feitas: return True
    return False
NPC_WANDER_RADIUS_SQM = 5
# Mob parado (sem alvo) passeia perto de casa, numa area menor que a do NPC.
MOB_WANDER_RADIUS_SQM = 3
MOB_WANDER_MIN_WAIT = 5.0
MOB_WANDER_MAX_WAIT = 10.0
NPC_WANDER_MIN_WAIT = 5.0
NPC_WANDER_MAX_WAIT = 10.0
NPC_STEP_SECONDS = 1.0 / 2.2
NPC_TICK_SECONDS = 0.1

ground_loot = {}
LOOT_EXPIRA_SEG = 300  
# Quanto tempo a bag fica visível no chão (TEMPO_DESPAWN_SEG do loot_bag.gd).
LOOT_BAG_VISIVEL_SEG = float(LOOT_EXPIRA_SEG)  # bag visivel enquanto existir (5 min)

def rolar_loot(mob_type_id):
    drops = MOB_DB.get(mob_type_id, {}).get("drops", [])
    itens_rolados = []
    for entrada in drops:
        item_path = validate_item(entrada.get("item"))
        if not item_path:
            continue
        chance = float(entrada.get("chance", 0.0))
        if random.random() > chance:
            continue
        min_qty = max(1, int(entrada.get("min_qty", 1)))
        max_qty = max(min_qty, int(entrada.get("max_qty", 1)))
        itens_rolados.append({"item": item_path, "qty": random.randint(min_qty, max_qty)})
    return itens_rolados

def rolar_currency(mob_type_id):
    faixa = MOB_DB.get(mob_type_id, {}).get("currency", {})
    minimo = max(0, int(faixa.get("min", 0)))
    maximo = max(minimo, int(faixa.get("max", 0)))
    if maximo <= 0:
        return 0
    return random.randint(minimo, maximo)

CRIT_CHANCE = 0.10
# Chance do ataque básico do player errar o mob (dano 0, "Miss" no client).
MISS_CHANCE_PLAYER = 0.05
BLOCK_CHANCE = 0.10
DEFENSE_AFK_WINDOW = 12.0
PUNICAO_CLASSE_HP = 1.0
PUNICAO_CLASSE_MP = 1.0
PUNICAO_CLASSE_DANO = 1
PUNICAO_CLASSE_SPEED = 0.01

online_players = {}
players_by_name = {} # O(1) Lookup Table (Otimização CPU)
# Chats extras do client (abas Portuguese/Spanish/...): canal -> sids dentro,
# igual grupo. Local não entra aqui (é por área, ver 'c').
CANAIS_CHAT = ('Portuguese', 'Spanish', 'English', 'Russian', 'Help')
chat_channels = {canal: set() for canal in CANAIS_CHAT}
active_mobs = {}
active_npcs = {}
save_queue = Queue() # Fila de Escrita Assíncrona

# party_id -> {"leader_sid": sid, "members": [sid,...] (ordem de entrada),
# "pending_invites": {target_sid: inviter_sid}}. Só em memória: nunca vai pro
# banco, não sobrevive a desconexão (ver _remover_do_party) - é um conceito de
# "quem está online agora", igual online_players/players_by_name.
parties = {}
PARTY_MAX_SIZE = 6
CLASS_EXP_BONUS_PER_DISTINCT_CLASS = 5

MOB_DESPAWN_CORPO_SEG = 60.0
MOB_RESPAWN_SEG = 120.0

def get_exp_for_level(level):
    if level <= 1: return 0
    return int((50 / 3) * (level**3 - 6 * (level**2) + 17 * level - 12))

def validate_item(item_path):
    if isinstance(item_path, str) and item_path.startswith("res://sprites/items/") and item_path.endswith(".tres"):
        return item_path
    return None

SLOTS_VALIDOS = {"Helm", "Gloves", "Chest", "Boots", "Necklace", "Ring", "MainHand", "Hand"}

ITEM_DB = {
    "res://sprites/items/Sword.tres": {"name": "Sword", "type": "Sword", "req_level": 0, "req_class": "Knight", "bonus_damage": 100, "defense": 0, "stamina": 0, "mana": 0, "fourth_stat_type": "None", "fourth_stat_value": 0, "cap": 5.0},
    "res://sprites/items/Bard/Weapons/StarterFlute.tres": {"name": "Wooden Flute", "type": "Flute", "req_level": 0, "req_class": "Bard", "bonus_damage": 1, "defense": 0, "stamina": 5, "mana": 5, "fourth_stat_type": "Musicality", "fourth_stat_value": 3, "cap": 5.0},
    "res://sprites/items/Bard/SecondHand/StarterSheet.tres": {"name": "Basic Music Sheet", "type": "Music Sheet", "req_level": 0, "req_class": "Bard", "bonus_damage": 0, "defense": 0, "stamina": 10, "mana": 10, "fourth_stat_type": "Musicality", "fourth_stat_value": 3, "cap": 5.0},
    "res://sprites/items/Knight/Weapons/StarterSword.tres": {"name": "Iron Sword", "type": "Sword", "req_level": 0, "req_class": "Knight", "bonus_damage": 1, "defense": 5, "stamina": 10, "mana": 0, "fourth_stat_type": "Melee", "fourth_stat_value": 5, "cap": 5.0},
    "res://sprites/items/Knight/SecondHand/StarterShield.tres": {"name": "Wooden Shield", "type": "Shield", "req_level": 0, "req_class": "Knight", "bonus_damage": 1, "defense": 5, "stamina": 10, "mana": 0, "fourth_stat_type": "", "fourth_stat_value": 0, "cap": 5.0},
    "res://sprites/items/Mage/Weapons/StarterStaff.tres": {"name": "Apprentice Staff", "type": "Staff", "req_level": 0, "req_class": "Mage", "bonus_damage": 1, "defense": 0, "stamina": 0, "mana": 10, "fourth_stat_type": "Magic", "fourth_stat_value": 2, "mana_cost": 3, "cap": 5.0},
    "res://sprites/items/Mage/SecondHand/StarterBook.tres": {"name": "Apprentice Book", "type": "Book", "req_level": 0, "req_class": "Mage", "bonus_damage": 0, "defense": 0, "stamina": 0, "mana": 10, "fourth_stat_type": "Magic", "fourth_stat_value": 5, "cap": 5.0},
    "res://sprites/items/Ranger/Weapons/StarterBow.tres": {"name": "Wooden Bow", "type": "Bow", "req_level": 0, "req_class": "Ranger", "bonus_damage": 1, "defense": 0, "stamina": 5, "mana": 0, "fourth_stat_type": "Focus", "fourth_stat_value": 5, "cap": 5.0},
    "res://sprites/items/Ranger/SecondHand/StarterArrow.tres": {"name": "Wooden Arrow", "type": "Arrow", "req_level": 0, "req_class": "Ranger", "bonus_damage": 0, "defense": 0, "stamina": 0, "mana": 0, "fourth_stat_type": "Focus", "fourth_stat_value": 5, "ammo": True, "max_stack": 9999, "cap": 0.1},
    # Comida: "fullness" = quanto enche a barra de Fullness por unidade comida.
    "res://sprites/items/Food/Cookie.tres": {"name": "Cookie", "type": "Food", "req_level": 0, "req_class": "All", "bonus_damage": 0, "defense": 0, "stamina": 0, "mana": 0, "fourth_stat_type": "None", "fourth_stat_value": 0, "fullness": 5, "stackable": True, "max_stack": 100, "cap": 0.1},
}

SLOT_MUNICAO = "Hand"
# Classes que atacam a distancia (o resto e' corpo a corpo) e o alcance delas.
CLASSES_RANGED = ("Ranger", "Mage", "Bard")
ALCANCE_LOOT_SQM = 4
# Arma a distancia alcanca o mesmo raio em que o mob detecta o player
# (DETECCAO_SQM, distancia em linha reta - circulo, nao quadrado).
ALCANCE_RANGED_SQM = 5

# Campos do ITEM_DB que o client usa pra exibir os itens (nome, tipo, level,
# stats e slot) - mandado no sync_local_player, assim o client nao precisa
# de uma copia propria e o que aparece na tela e' sempre o valor real.
CAMPOS_ITEM_CLIENTE = ("name", "type", "req_level", "req_class", "bonus_damage", "defense",
                       "stamina", "mana", "fourth_stat_type", "fourth_stat_value", "ammo", "mana_cost",
                       "fullness", "stackable")

def montar_item_db_cliente():
    db = {}
    for item_path, dados in ITEM_DB.items():
        entrada = {k: dados[k] for k in CAMPOS_ITEM_CLIENTE if k in dados}
        entrada["slot"] = slot_do_item(item_path)
        db[item_path] = entrada
    return db

def slot_do_item(item_path):
    """Slot em que o item pode ser equipado (None = nao equipavel). Usa "slot"
    do ITEM_DB se existir; senao deduz do caminho - mesma regra do client
    (BookMenuUI.slotDoItem)."""
    if not isinstance(item_path, str): return None
    slot = ITEM_DB.get(item_path, {}).get("slot")
    if slot in SLOTS_VALIDOS: return slot
    if "/Weapons/" in item_path or item_path.endswith("/Sword.tres"): return "MainHand"
    if "/SecondHand/" in item_path: return "Hand"
    nome = item_path.rsplit("/", 1)[-1].lower()
    if "helm" in nome or "hat" in nome: return "Helm"
    if "neck" in nome or "amulet" in nome: return "Necklace"
    if "chest" in nome or "armor" in nome or "robe" in nome: return "Chest"
    if "glove" in nome: return "Gloves"
    if "boot" in nome: return "Boots"
    if "ring" in nome: return "Ring"
    return None
MAX_STACK_MUNICAO_PADRAO = 100

CAP_INICIAL = 100.0
CAP_POR_NIVEL = 5.0

FOURTH_STAT_MULTIPLIERS = {"Magic": 0.5, "Focus": 0.5, "Musicality": 0.5, "Melee": 0.5}

STARTING_EQUIPMENT = {
    "Knight":  [{"item": "res://sprites/items/Knight/Weapons/StarterSword.tres", "slot": "MainHand"}, {"item": "res://sprites/items/Knight/SecondHand/StarterShield.tres", "slot": "Hand"}],
    "Ranger": [{"item": "res://sprites/items/Ranger/Weapons/StarterBow.tres", "slot": "MainHand"}, {"item": "res://sprites/items/Ranger/SecondHand/StarterArrow.tres", "slot": "Hand", "qty": 300}],
    "Mage":  [{"item": "res://sprites/items/Mage/Weapons/StarterStaff.tres", "slot": "MainHand"}, {"item": "res://sprites/items/Mage/SecondHand/StarterBook.tres", "slot": "Hand"}],
    "Bard": [{"item": "res://sprites/items/Bard/Weapons/StarterFlute.tres", "slot": "MainHand"}, {"item": "res://sprites/items/Bard/SecondHand/StarterSheet.tres", "slot": "Hand"}],
}
# Item da bag inicial: caminho, ou (caminho, quantidade) pra empilhavel.
COOKIE = "res://sprites/items/Food/Cookie.tres"
STARTING_INVENTORY = {c: [(COOKIE, 10)] for c in ("Knight", "Ranger", "Mage", "Bard")}

def montar_kit_inicial(class_name):
    equipped = {}
    for entrada in STARTING_EQUIPMENT.get(class_name, []):
        slot = entrada.get("slot")
        item_path = validate_item(entrada.get("item"))
        if item_path and slot in SLOTS_VALIDOS: equipped[slot] = criar_instancia_item(item_path, entrada.get("qty", 1))
    inventory = []
    for entrada in STARTING_INVENTORY.get(class_name, []):
        item_path, qty = entrada if isinstance(entrada, tuple) else (entrada, 1)
        item_path = validate_item(item_path)
        if item_path: inventory.append(criar_instancia_item(item_path, qty))
    return inventory, equipped

def obter_dados_item(item_path):
    dados = ITEM_DB.get(item_path, {})
    bonus_damage = dados.get("bonus_damage", 0)
    adc_type = dados.get("fourth_stat_type", "None")
    adc_value = dados.get("fourth_stat_value", 0)
    if adc_type != "None" and adc_value:
        bonus_damage += adc_value * FOURTH_STAT_MULTIPLIERS.get(adc_type, 0.0)
    return {
        "req_class": dados.get("req_class", "All"),
        "bonus_damage": bonus_damage, "bonus_defense": dados.get("defense", 0),
        "bonus_hp": dados.get("stamina", 0), "bonus_mp": dados.get("mana", 0),
    }

def obter_req_class_item(item_path): return obter_dados_item(item_path).get("req_class", "All")

def somar_bonus_combate_equipados(equipped_items):
    total = {"bonus_damage": 0, "bonus_defense": 0.0, "bonus_hp": 0.0, "bonus_mp": 0.0}
    if not isinstance(equipped_items, dict): return total
    for inst in equipped_items.values():
        if not isinstance(inst, dict): continue
        dados = obter_dados_item(inst.get("item"))
        total["bonus_damage"] += dados.get("bonus_damage", 0)
        total["bonus_defense"] += dados.get("bonus_defense", 0.0)
        total["bonus_hp"] += dados.get("bonus_hp", 0.0)
        total["bonus_mp"] += dados.get("bonus_mp", 0.0)
    return total

def eh_municao(item_path):
    return bool(ITEM_DB.get(item_path, {}).get("ammo", False))

def eh_empilhavel(item_path):
    # Flecha ou qualquer item com "stackable" (ex: comida): guarda "qty".
    return eh_municao(item_path) or bool(ITEM_DB.get(item_path, {}).get("stackable", False))

def obter_max_stack(item_path):
    if not eh_empilhavel(item_path): return 1
    return max(1, int(ITEM_DB.get(item_path, {}).get("max_stack", MAX_STACK_MUNICAO_PADRAO)))

def obter_cap_unitario_item(item_path):
    return float(ITEM_DB.get(item_path, {}).get("cap", 0.0))

def obter_cap_instancia(inst):
    if not isinstance(inst, dict): return 0.0
    item_path = inst.get('item')
    cap_unit = obter_cap_unitario_item(item_path)
    if cap_unit <= 0: return 0.0
    if eh_empilhavel(item_path):
        return cap_unit * max(1, int(inst.get('qty', 1)))
    return cap_unit

def calcular_cap_usado(p):
    total = 0.0
    for inst in p.get('inventory', []):
        total += obter_cap_instancia(inst)
    for inst in p.get('equipped_items', {}).values():
        total += obter_cap_instancia(inst)
    return total

def calcular_cap_maximo(level):
    return CAP_INICIAL + CAP_POR_NIVEL * max(0, int(level) - 1)

def criar_instancia_item(item_path, qty=1):
    inst = {"id": uuid.uuid4().hex, "item": item_path, "favorite": False}
    if eh_empilhavel(item_path): inst["qty"] = max(1, min(int(qty), obter_max_stack(item_path)))
    return inst

def adicionar_municao_ao_jogador(p, item_path, qty):
    max_stack = obter_max_stack(item_path)
    restante = max(0, int(qty))
    equipados = p.setdefault('equipped_items', {})
    inventario = p.setdefault('inventory', [])

    def completar(inst):
        nonlocal restante
        espaco = max_stack - int(inst.get('qty', 1))
        add = min(espaco, restante)
        if add > 0:
            inst['qty'] = int(inst.get('qty', 1)) + add
            restante -= add

    eq = equipados.get(SLOT_MUNICAO)
    if isinstance(eq, dict) and eq.get('item') == item_path: completar(eq)
    for inst in inventario:
        if restante <= 0: break
        if inst.get('item') == item_path: completar(inst)
    while restante > 0:
        n = min(max_stack, restante)
        inventario.append(criar_instancia_item(item_path, n))
        restante -= n

def custo_mana_arma(p):
    # Mana gasta por ataque básico ("mana_cost" da arma da mão principal,
    # ex: varinha/cajado). 0 = arma não gasta mana.
    inst = p.get('equipped_items', {}).get('MainHand')
    if not isinstance(inst, dict): return 0
    try: return max(0, int(ITEM_DB.get(inst.get('item'), {}).get('mana_cost', 0)))
    except (TypeError, ValueError): return 0

def tem_municao_equipada(p):
    inst = p.get('equipped_items', {}).get(SLOT_MUNICAO)
    return isinstance(inst, dict) and eh_municao(inst.get('item')) and int(inst.get('qty', 1)) > 0

def consumir_municao(p):
    equipados = p.get('equipped_items', {})
    inst = equipados.get(SLOT_MUNICAO)
    restante = int(inst.get('qty', 1)) - 1
    if restante <= 0:
        del equipados[SLOT_MUNICAO]
        return 0, True
    inst['qty'] = restante
    return restante, False

def normalizar_instancia(entrada):
    if isinstance(entrada, str):
        item_path = validate_item(entrada)
        return criar_instancia_item(item_path) if item_path else None
    if isinstance(entrada, dict):
        item_path = validate_item(entrada.get("item"))
        if not item_path: return None
        instance_id = entrada.get("id")
        if not isinstance(instance_id, str) or not instance_id: instance_id = uuid.uuid4().hex
        favorite = bool(entrada.get("favorite", False))
        inst = {"id": instance_id, "item": item_path, "favorite": favorite}
        if eh_empilhavel(item_path):
            try: qty = int(entrada.get("qty", 1))
            except (TypeError, ValueError): qty = 1
            inst["qty"] = max(1, min(qty, obter_max_stack(item_path)))
        return inst
    return None

def ordenar_favoritos_primeiro(inventory):
    if not isinstance(inventory, list): return inventory
    favoritos = [inst for inst in inventory if inst.get("favorite")]
    resto = [inst for inst in inventory if not inst.get("favorite")]
    return favoritos + resto

def normalizar_inventario(inventory):
    if not isinstance(inventory, list): return []
    return [inst for inst in (normalizar_instancia(e) for e in inventory) if inst]

def normalizar_equipados(equipped_items):
    if not isinstance(equipped_items, dict): return {}
    normalizado = {}
    for slot, entrada in equipped_items.items():
        if slot not in SLOTS_VALIDOS: continue
        inst = normalizar_instancia(entrada)
        if inst: normalizado[slot] = inst
    return normalizado

def encontrar_instancia(inventory, instance_id):
    if not instance_id: return None
    for inst in inventory:
        if inst.get("id") == instance_id: return inst
    return None

def get_hits_to_level(level, multiplier):
    L = float(level - 3)
    return int(math.ceil((L * (1.0825 ** L) + (1.0825 * L) + 30.0) * multiplier))

def get_skill_multiplier(player_class, is_defense):
    if is_defense:
        if player_class == "Knight": return 0.8
        elif player_class == "Ranger": return 1.0
        elif player_class in ["Mage", "Bard"]: return 1.2
    else:
        if player_class == "Knight": return 1.2
        elif player_class == "Ranger": return 1.0
        elif player_class in ["Mage", "Bard"]: return 0.8
    return 1.0

BASE_MAX_HP = 50.0
BASE_MAX_MP = 50.0
SPEED_PCT_POR_LEVEL = 0.0001

def calc_level_bonuses(level, player_class):
    niveis_ganhos = max(0, level - 1)
    if player_class == "Knight": hp_p = 15.0; mp_p = 5.0
    elif player_class == "Ranger": hp_p = 10.0; mp_p = 10.0
    elif player_class in ["Mage", "Bard"]: hp_p = 5.0; mp_p = 15.0
    else: hp_p = 5.0; mp_p = 5.0
    bonus_hp = niveis_ganhos * hp_p
    bonus_mp = niveis_ganhos * mp_p
    speed_multiplier = 1.0 + (niveis_ganhos * SPEED_PCT_POR_LEVEL)
    return bonus_hp, bonus_mp, speed_multiplier

def calc_player_stats(player_class, level, skills, bonus_dmg=0, bonus_def=0):
    dmg = ((level - 1) * 0.5) + bonus_dmg
    defense = 0.0 + bonus_def
    adc = skills.get("adc", 0)
    dmg += adc * 0.5
    defense += adc * 0.5
    
    # Skill base (a da classe + defense): 1 de dano e 1 de redução por nível,
    # igual pra todas as classes. ADC (acima) e itens continuam à parte.
    dmg += skills.get(SKILL_DA_CLASSE.get(player_class, 'melee'), 10) * 1.0
    defense += skills.get("defense", 10) * 1.0
    return int(dmg), float(defense)

def _slots_com_classe_invalida(p):
    player_class = p.get('class_name', 'Knight')
    equipados = p.get('equipped_items', {})
    invalidos = set()
    for slot, inst in equipados.items():
        if not isinstance(inst, dict): continue
        req_class = obter_req_class_item(inst.get('item'))
        if req_class != "All" and req_class != player_class:
            invalidos.add(slot)
    return invalidos

def _montar_payload_sync_stats(p):
    p_class = p.get('class_name', 'Knight')
    level = p.get('level', 1)
    skills = p.get('skills', {})
    bonus_itens = somar_bonus_combate_equipados(p.get('equipped_items', {}))
    punido = len(_slots_com_classe_invalida(p)) > 0
    new_dmg, new_def = calc_player_stats(p_class, level, skills, bonus_dmg=bonus_itens['bonus_damage'], bonus_def=bonus_itens['bonus_defense'])
    hp_bonus, mp_bonus, speed_multiplier = calc_level_bonuses(level, p_class)
    hp_bonus += bonus_itens['bonus_hp']
    mp_bonus += bonus_itens['bonus_mp']

    if punido:
        new_dmg = PUNICAO_CLASSE_DANO
        speed_multiplier = PUNICAO_CLASSE_SPEED
    if esta_com_fome(p):
        new_dmg = max(1, int(new_dmg * FOME_MULT_DANO))

    # Sem o bestiary (vai so' no evento proprio): nao viaja em todo sync_stats.
    skills = {k: v for k, v in skills.items() if k != 'bestiary'} if isinstance(skills, dict) else skills
    return {
        'skills': skills, 'base_damage': new_dmg, 'defense_value': new_def,
        'level': level, 'exp': p.get('exp', 0), 'kills': p.get('kills', 0),
        'hp_bonus': hp_bonus, 'mana_bonus': mp_bonus, 'speed_multiplier': speed_multiplier,
        'classe_penalizada': punido,
        'cap_atual': calcular_cap_usado(p), 'cap_maximo': calcular_cap_maximo(level),
        # Vitais pra barra de HP/MP do client.
        'max_hp': calcular_max_vitais(p)[0], 'max_mp': calcular_max_vitais(p)[1],
        'current_hp': p.get('current_hp', -1), 'current_mp': p.get('current_mp', -1),
    }

def calcular_max_vitais(p):
    if len(_slots_com_classe_invalida(p)) > 0: return PUNICAO_CLASSE_HP, PUNICAO_CLASSE_MP
    p_class = p.get('class_name', 'Knight')
    level = p.get('level', 1)
    hp_bonus_nivel, mp_bonus_nivel, _ = calc_level_bonuses(level, p_class)
    bonus_itens = somar_bonus_combate_equipados(p.get('equipped_items', {}))
    max_hp = BASE_MAX_HP + hp_bonus_nivel + bonus_itens['bonus_hp']
    max_mp = BASE_MAX_MP + mp_bonus_nivel + bonus_itens['bonus_mp']
    return max_hp, max_mp

app = Flask(__name__)
CORS(app)
socketio = SocketIO(
    app, cors_allowed_origins="*", ping_interval=10, 
    ping_timeout=15, async_mode='eventlet'
)

# =========================================================================
# SEGURANCA: sessao (token), limite de tentativas e validacoes
# =========================================================================

# Segredo que assina os tokens de sessao. Vem do .env (SECRET_KEY) ou, sem
# ele, e' gerado uma vez e salvo do lado do servidor - sobrevive a reinicios,
# entao ninguem precisa logar de novo so' porque o servidor reiniciou.
_ARQUIVO_SEGREDO = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'segredo_sessao.key')

def _carregar_segredo():
    s = os.getenv('SECRET_KEY')
    if s: return s.encode()
    try:
        with open(_ARQUIVO_SEGREDO, 'r') as f:
            return f.read().strip().encode()
    except FileNotFoundError:
        novo = secrets.token_hex(32)
        with open(_ARQUIVO_SEGREDO, 'w') as f:
            f.write(novo)
        try: os.chmod(_ARQUIVO_SEGREDO, 0o600)
        except Exception: pass
        return novo.encode()

SEGREDO_SESSAO = _carregar_segredo()
TOKEN_VALIDADE_SEG = 30 * 24 * 3600  # 30 dias

def _assinar(texto):
    return hmac.new(SEGREDO_SESSAO, texto.encode(), hashlib.sha256).hexdigest()

def _versao_senha(hash_senha):
    # Entra na assinatura: trocar a senha invalida todos os tokens antigos.
    return hashlib.sha256((hash_senha or '').encode()).hexdigest()[:16]

def gerar_token(user_id, hash_senha):
    exp = int(time.time()) + TOKEN_VALIDADE_SEG
    base = f"{int(user_id)}.{exp}.{_versao_senha(hash_senha)}"
    return f"{int(user_id)}.{exp}.{_assinar(base)}"

def token_valido(cursor, user_id, token):
    """O token prova que quem pede ja' fez login nessa conta (o user_id que o
    client manda nao vale nada sozinho - qualquer um pode digitar outro)."""
    try:
        uid_txt, exp_txt, assinatura = str(token).split('.')
        uid, exp = int(uid_txt), int(exp_txt)
        if uid != int(user_id) or exp < time.time(): return False
    except (ValueError, TypeError):
        return False
    cursor.execute("SELECT password FROM users WHERE id = %s", (uid,))
    row = cursor.fetchone()
    if not row: return False
    base = f"{uid}.{exp}.{_versao_senha(row[0])}"
    return hmac.compare_digest(_assinar(base), assinatura)

# ---- Limite de tentativas (HTTP) ----
# chave -> timestamps recentes. Barra forca bruta de senha/codigo e spam de
# cadastro. Em memoria (zera ao reiniciar o servidor, tudo bem).
_tentativas = {}

def _limite_excedido(chave, maximo, janela_seg):
    agora = time.time()
    lista = [t for t in _tentativas.get(chave, []) if agora - t < janela_seg]
    if len(lista) >= maximo:
        _tentativas[chave] = lista
        return True
    lista.append(agora)
    _tentativas[chave] = lista
    return False

def _ip():
    # Atras de proxy (nginx/cloudflare), configure o proxy pra mandar o IP
    # real e troque isto por request.headers['X-Forwarded-For'].
    return request.remote_addr or '?'

def _resposta_limite():
    return jsonify({"erro": "Too many attempts. Wait a few minutes and try again."}), 429

def _erro_interno(e):
    # Nunca devolve a mensagem da excecao pro client (vazava detalhes do banco).
    traceback.print_exc()
    return jsonify({"erro": "Server error."}), 500

def _json_requisicao():
    dados = request.get_json(silent=True)
    return dados if isinstance(dados, dict) else {}

RE_EMAIL = re.compile(r"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$")
# Letra no comeco; letras (com acento), numeros e espaco simples; 3 a 12.
RE_NOME_PERSONAGEM = re.compile(r"^[A-Za-zÀ-ÖØ-öø-ÿ][A-Za-zÀ-ÖØ-öø-ÿ0-9 ]{2,11}$")
CLASSES_VALIDAS = ("Knight", "Ranger", "Mage", "Bard")
SENHA_MIN, SENHA_MAX = 5, 128

# ---- Limite de eventos por conexao (socket) ----
# Cada conexao tem um "balde" de eventos: rajada de ate EVENTOS_RAJADA,
# recarregando EVENTOS_POR_SEG por segundo. Passou disso o evento e'
# descartado; quem continua inundando muito acima disso e' desconectado.
EVENTOS_POR_SEG = 30.0
EVENTOS_RAJADA = 60.0
EVENTOS_DESCARTADOS_MAX = 300
_baldes_eventos = {}
_on_original = socketio.on

def _socketio_on_com_limite(evento, *args, **kwargs):
    decorador_original = _on_original(evento, *args, **kwargs)
    if evento in ('connect', 'disconnect'):
        return decorador_original
    def decorador(funcao):
        def com_limite(*f_args, **f_kwargs):
            sid = request.sid
            agora = time.time()
            balde = _baldes_eventos.get(sid)
            if balde is None:
                balde = _baldes_eventos[sid] = [EVENTOS_RAJADA, agora, 0]
            balde[0] = min(EVENTOS_RAJADA, balde[0] + (agora - balde[1]) * EVENTOS_POR_SEG)
            balde[1] = agora
            if balde[0] < 1.0:
                balde[2] += 1
                if balde[2] >= EVENTOS_DESCARTADOS_MAX:
                    print(f"[SEGURANCA] {sid} desconectado por flood de eventos")
                    try: socketio.server.disconnect(sid, namespace='/')
                    except Exception: pass
                return None
            balde[0] -= 1.0
            balde[2] = max(0, balde[2] - 1)
            return funcao(*f_args, **f_kwargs)
        com_limite.__name__ = funcao.__name__
        return decorador_original(com_limite)
    return decorador

socketio.on = _socketio_on_com_limite

# Bloqueio de nomes de personagem via substring (name.lower() contém algum
# destes) - cobre (1) tentativa de se passar por staff/sistema e (2) nomes
# ofensivos (slurs, discurso de ódio, referências a regimes/figuras
# genocidas, termos sexuais/vulgares). Termos curtos demais (tipo "ass"
# sozinho) foram evitados de propósito pra não colidir com nomes legítimos
# (ex: "Cassandra", "Bastian") - se um nome legítimo for bloqueado por engano
# no futuro, é sinal de que algum termo daqui está genérico demais.
BANNED_NAMES = [
    # Impersonação de staff/sistema
    "admin", "administrator", "gamemaster", "moderator", "moderador",
    "suporte", "support", "sistema", "system", "official", "oficial",
    "root", "developer", "desenvolvedor",

    # Regimes/figuras genocidas e símbolos de ódio
    "hitler", "nazi", "nazista", "hitlerm", "kkk", "isis", "terrorist",
    "terrorista", "supremacist", "supremacista",

    # Slurs e discurso de ódio (raça/etnia/orientação/religião)
    "nigger", "nigga", "chink", "spic", "kike", "faggot", "fag",
    "retard", "retardado", "macaco", "crioulo", "viado", "bicha",
    "sapatao", "traveco",

    # Profanidade/vulgaridade forte (inglês)
    "fuck", "motherfucker", "bitch", "asshole", "bastard", "whore",
    "slut", "cunt", "dickhead", "cocksucker",

    # Profanidade/vulgaridade forte (português)
    "buceta", "caralho", "porra", "piroca", "pinto", "pau no cu",
    "arrombado", "arrombada", "corno", "cornuda", "vagabunda",
    "vagabundo", "puta", "putaria", "safada", "safado", "vadia",
    "desgraca", "filho da puta", "fdp", "cuzao", "cuzão", "otario",
    "otária", "babaca", "idiota", "imbecil", "escroto", "escrota",

    # Sexual explícito
    "sex", "porn", "porno", "nude", "nudes", "pedofilo", "pedophile",
    "estuprador", "rapist",
]

def get_chunk(x, y, floor=1):
    # O andar NÃO entra na sala: a ponte (andar 2) fica em cima do andar 1 no
    # mesmo lugar do mapa, e quem está embaixo tem que ver quem está na ponte
    # (e vice-versa). Andar só muda colisão/z no client.
    try: return f"c_0_{int(float(x) // CHUNK_SIZE)}_{int(float(y) // CHUNK_SIZE)}"
    except Exception: return "c_0_0_0"

def salas_vizinhas(room):
    try:
        _, floor, cx, cy = room.split('_'); floor, cx, cy = int(floor), int(cx), int(cy)
    except Exception:
        return [room] if room else []
    return [f"c_{floor}_{cx+dx}_{cy+dy}" for dx in (-1, 0, 1) for dy in (-1, 0, 1)]

def emit_area(event, payload, room, skip_sid=None):
    # Usa socketio.emit (e não o emit() do flask_socketio) porque este helper
    # também é chamado de background tasks (regen_loop). Lá não existe request
    # context, o emit() estourava RuntimeError e matava o regen_loop inteiro
    # (por isso o regen "parava" depois do primeiro tick).
    for r in salas_vizinhas(room):
        socketio.emit(event, payload, room=r, skip_sid=skip_sid)

# HP "público" de um player (o que os outros clientes devem ver na barra dele).
# current_hp == -1 no banco/memória significa "cheio".
def hp_publico(p):
    max_hp, _ = calcular_max_vitais(p)
    if p.get('is_dead'):
        return 0.0, max_hp
    try: hp = float(p.get('current_hp', -1))
    except (TypeError, ValueError): hp = -1.0
    if hp < 0: hp = max_hp
    return min(hp, max_hp), max_hp

def dados_publicos_player(p):
    hp, max_hp = hp_publico(p)
    dados = dict(p)
    dados['current_hp'] = hp
    dados['max_hp'] = max_hp
    dados['is_dead'] = bool(p.get('is_dead', False))
    return dados

def resumo_player_area(p):
    hp, max_hp = hp_publico(p)
    return {
        "name": p.get('name', ''),
        "class_name": p.get('class_name', 'Knight'),
        "skins": p.get('skins', {}),
        "floor": p.get('floor', 1),
        "pos_x": p.get('pos_x', 0),
        "pos_y": p.get('pos_y', 0),
        "direction": p.get('direction', 'down'),
        "current_hp": hp,
        "max_hp": max_hp,
        "is_dead": bool(p.get('is_dead', False)),
        "is_typing": bool(p.get('is_typing', False)),
        "is_in_settings": bool(p.get('is_in_settings', False)),
        "is_in_skins": bool(p.get('is_in_skins', False)),
    }

# Replica o HP do player pros outros clientes da área (a barra do "remote").
# Só manda quando muda, pra não floodar (o client reporta vitals em todo sync_stats).
def broadcast_hp(sid, p):
    room = p.get('room'); name = p.get('name')
    if not room or not name: return
    hp, max_hp = hp_publico(p)
    if p.get('_ultimo_hp_broadcast') == [hp, max_hp]: return
    p['_ultimo_hp_broadcast'] = [hp, max_hp]
    emit_area('player_status_updated', {"name": name, "current_hp": hp, "max_hp": max_hp}, room, skip_sid=sid)

# =====================================================================
# MOBS — IA 100% NO SERVIDOR
# Antes a IA rodava no client "dono" de cada mob e o dono mudava (escada,
# morte, sair de perto); cada tela via um mob diferente. Agora o servidor
# decide tudo (alvo, A*, passo, ataque, flag, morte, respawn) e os clients
# só desenham o que chega em mob_pos / player_damaged / mob_died / mob_respawn.
# As paredes vêm da grade de colisão que o client escaneia do mapa e manda
# uma vez (register_map -> need_map_grid -> map_grid); fica salva em
# mapas_colisao.json e só é pedida de novo quando o mapa muda.
# =====================================================================

# TILE=16 (era 32) - alinhado ao tile visual de verdade do World.tmx novo do
# client libGDX (ver [[libgdx_migration]]); o jogo/*.gd do Godot fica pra
# tras de proposito, nao e' mais o alvo de sincronia (so' serve de
# referencia pra copiar coisas). Os *_SQM abaixo (DETECCAO/PERSISTE/LEASH)
# sao contagem de celulas, nao pixels - o alcance real em pixels cai pela
# metade com esse TILE menor (ex: LEASH_SQM=25 era 800px, agora 400px) -
# rebalancear esses numeros e' decisao separada, nao mexi neles aqui.
TILE = 16
MOB_FLOOR = 1
DETECCAO_SQM = 5
# +4 SQMs de folga: dá tempo do mob perseguir até a escada/transição em vez de
# desistir na hora só porque o alvo mudou de andar no meio da perseguição.
PERSISTE_SQM = 8
SEM_CAMINHO_SEG = 1.5
# Focando o mesmo alvo esse tempo sem conseguir bater: o próximo player que
# bater no mob rouba o foco (anti-abuso).
TROCA_ALVO_SEM_HIT_SEG = 4.0
# Caminho até o alvo trancado só por criaturas (ex: corredor com outro mob
# na frente) por esse tempo: desiste e volta pra casa com flag, igual quando
# a parede bloqueia. Um pouco mais que SEM_CAMINHO_SEG pra não desistir só
# porque o mob da frente ainda está terminando o passo.
BLOQUEADO_CRIATURA_SEG = 2.0
# Ranged (cajado/arco/arma de bard) batendo colado (1 SQM) num mob que está
# focando OUTRO player: dano cortado (anti "ficar colado no lure do knight").
DANO_RANGED_COLADO_MULT = 0.5
PATH_RECALC_SEG = 0.3
# Distância máxima (em SQMs) que o mob pode se afastar do spawn perseguindo
# alguém. Ultrapassou: em vez de voltar andando (às vezes por um mapa
# inteiro), ele "some" (efeito SpawnEffect no client) e conta como morto pro
# respawn normal (MOB_RESPAWN_SEG) — ver _mob_desaparece_por_perseguicao.
LEASH_SQM = 25
ALCANCE_RECALC_SEG = 0.5
MOB_TICK_SEG = 0.05
DIR_TO_INT = {'down': 0, 'up': 1, 'left': 2, 'right': 3}
ASTAR_LIMITE_NOS = 4000
MOB_SPEED_PADRAO = 1.0
# Intervalo entre golpes (player e mob): uma "cadencia" de ataque.
ATAQUE_COOLDOWN_SEG = 2.385
MOB_COOLDOWN_PADRAO = ATAQUE_COOLDOWN_SEG
MAPAS_ARQUIVO = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'mapas_colisao.json')
mapas_colisao = {}  # map_id -> {'fp', 'x0', 'y0', 'w', 'h', 'bits': bytes}

def tile_de(x, y):
    # Mesmo snap_to_tile_center dos .gd (âncora no pé do SQM).
    return (int(math.floor(float(x) / TILE)), int(math.floor((float(y) - 1.0) / TILE)))

def centro_tile(t):
    return (t[0] * TILE + TILE / 2.0, t[1] * TILE + float(TILE))

def encaixar_no_tile(x, y):
    """Leva (x, y) pro centro/pe do SQM em que cai. Posicoes salvas antes do
    TILE mudar de 32 pra 16 tinham X em 16+32k - exatamente na divisa entre 2
    SQMs do grid novo (player nascia "no meio de 2 SQMs"). (-1, -1) = sem posicao."""
    if x == -1 and y == -1: return x, y
    return centro_tile(tile_de(x, y))

def _bits_vazios(w, h):
    return bytes((w * h + 7) // 8)

def carregar_mapas():
    try:
        with open(MAPAS_ARQUIVO, 'r') as f:
            dados = json.load(f)
        for map_id, m in dados.items():
            w, h = int(m['w']), int(m['h'])
            mapas_colisao[map_id] = {
                'fp': m['fp'], 'x0': int(m['x0']), 'y0': int(m['y0']), 'w': w, 'h': h,
                'bits': base64.b64decode(m['bits']),
                # Bordas finas (leste/baixo por SQM) - campo novo, opcional.
                # Grade salva antes dessa mudanca nao tem essas chaves; grade
                # sem nenhuma borda fina marcada e' o comportamento antigo
                # (so' colisao de celula inteira), entao o default e' zerado.
                'bits_leste': base64.b64decode(m['bits_leste']) if m.get('bits_leste') else _bits_vazios(w, h),
                'bits_baixo': base64.b64decode(m['bits_baixo']) if m.get('bits_baixo') else _bits_vazios(w, h),
            }
        print(f"[MAPA] {len(mapas_colisao)} grade(s) de colisão carregada(s).")
    except FileNotFoundError:
        pass
    except Exception:
        traceback.print_exc()

def salvar_mapas():
    try:
        dados = {k: {'fp': v['fp'], 'x0': v['x0'], 'y0': v['y0'], 'w': v['w'], 'h': v['h'],
                     'bits': base64.b64encode(v['bits']).decode(),
                     'bits_leste': base64.b64encode(v['bits_leste']).decode(),
                     'bits_baixo': base64.b64encode(v['bits_baixo']).decode()} for k, v in mapas_colisao.items()}
        with open(MAPAS_ARQUIVO, 'w') as f:
            json.dump(dados, f)
    except Exception:
        traceback.print_exc()

def eh_parede(grade, t):
    if grade is None: return True
    x, y = t[0] - grade['x0'], t[1] - grade['y0']
    if x < 0 or y < 0 or x >= grade['w'] or y >= grade['h']: return True
    i = y * grade['w'] + x
    return bool(grade['bits'][i >> 3] & (1 << (i & 7)))

def _bit_ligado(bits, w, h, x, y):
    if x < 0 or y < 0 or x >= w or y >= h: return False
    i = y * w + x
    return bool(bits[i >> 3] & (1 << (i & 7)))

# Barreira fina (grade/corrimão sem parede solida por tras, ex: borda de
# templo) - bloqueia atravessar UMA borda especifica entre 2 SQMs vizinhos,
# sem tornar nenhum dos dois solido (ao contrario de eh_parede). 'de' e
# 'para' precisam ser ortogonalmente adjacentes (dx/dy = +-1 em um so eixo -
# e' a unica forma de movimento que astar/astar_ate_adjacente geram).
def linha_de_visao(grade, de, para):
    # Ataque a distancia: a reta (centro a centro) entre os 2 SQMs nao pode
    # passar por SQM solido. Anda 1 SQM por vez no eixo maior; quando a reta
    # passa exatamente entre 2 SQMs, so' bloqueia se os DOIS forem solidos.
    # Cercas/bordas finas nao bloqueiam (da' pra atirar por cima).
    # Mesma conta de WorldScreen.temLinhaDeVisao (client).
    if grade is None: return True
    dx, dy = para[0] - de[0], para[1] - de[1]
    passos = max(abs(dx), abs(dy))
    for k in range(1, passos):
        if abs(dx) >= abs(dy):
            x = de[0] + (k if dx > 0 else -k)
            v = de[1] + dy * k / passos
            base = math.floor(v)
            if abs(v - base - 0.5) < 1e-9:
                if eh_parede(grade, (x, base)) and eh_parede(grade, (x, base + 1)): return False
            elif eh_parede(grade, (x, math.floor(v + 0.5))): return False
        else:
            y = de[1] + (k if dy > 0 else -k)
            v = de[0] + dx * k / passos
            base = math.floor(v)
            if abs(v - base - 0.5) < 1e-9:
                if eh_parede(grade, (base, y)) and eh_parede(grade, (base + 1, y)): return False
            elif eh_parede(grade, (math.floor(v + 0.5), y)): return False
    return True

def borda_bloqueada(grade, de, para):
    if grade is None: return False
    dx, dy = para[0] - de[0], para[1] - de[1]
    x0, y0, w, h = grade['x0'], grade['y0'], grade['w'], grade['h']
    if dx == 1:
        return _bit_ligado(grade['bits_leste'], w, h, de[0] - x0, de[1] - y0)
    if dx == -1:
        return _bit_ligado(grade['bits_leste'], w, h, para[0] - x0, para[1] - y0)
    if dy == 1:
        return _bit_ligado(grade['bits_baixo'], w, h, de[0] - x0, de[1] - y0)
    if dy == -1:
        return _bit_ligado(grade['bits_baixo'], w, h, para[0] - x0, para[1] - y0)
    return False

def diagonal_bloqueada(grade, de, para):
    # Passo na diagonal (player): passa se pelo menos um dos dois caminhos em
    # "L" (horizontal+vertical ou vertical+horizontal) não cruza cerca fina.
    # Parede sólida no canto não bloqueia (igual Tibia, dá pra cortar quina).
    # Mesma regra do client (WorldScreen.diagonalBloqueada).
    dx, dy = para[0] - de[0], para[1] - de[1]
    if abs(dx) != 1 or abs(dy) != 1: return False
    via_x, via_y = (de[0] + dx, de[1]), (de[0], de[1] + dy)
    caminho_x = borda_bloqueada(grade, de, via_x) or borda_bloqueada(grade, via_x, para)
    caminho_y = borda_bloqueada(grade, de, via_y) or borda_bloqueada(grade, via_y, para)
    return caminho_x and caminho_y

def astar(grade, inicio, fim, bloqueados=None, limite=ASTAR_LIMITE_NOS, bounds=None):
    # A* em grade 4-direções (igual ao AStarGrid2D DIAGONAL_MODE_NEVER +
    # Manhattan do client). O destino nunca conta como bloqueado (é o SQM do
    # player-alvo). Retorna a lista de tiles [inicio, ..., fim] ou None.
    if inicio == fim: return [inicio]
    if eh_parede(grade, fim): return None
    bloqueados = bloqueados or ()
    def h(t): return abs(t[0] - fim[0]) + abs(t[1] - fim[1])
    aberto = [(h(inicio), 0, inicio)]
    veio = {inicio: None}
    custo = {inicio: 0}
    expandidos = 0
    while aberto:
        _, g_neg, atual = heapq.heappop(aberto)
        g = -g_neg
        if atual == fim:
            caminho = [atual]
            while veio[caminho[-1]] is not None:
                caminho.append(veio[caminho[-1]])
            caminho.reverse()
            return caminho
        if g > custo.get(atual, 1 << 30): continue
        expandidos += 1
        if expandidos > limite: return None
        for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            viz = (atual[0] + dx, atual[1] + dy)
            if bounds is not None:
                min_x, max_x, min_y, max_y = bounds
                if viz[0] < min_x or viz[0] > max_x or viz[1] < min_y or viz[1] > max_y:
                    continue
            # borda_bloqueada NAO entra na excecao "destino nunca bloqueado" -
            # ao contrario de eh_parede/bloqueados (que so' fazem sentido pro
            # SQM em si, onde o player-alvo garantidamente pode estar), uma
            # borda fina e' uma barreira de verdade entre 2 SQMs: o mob tem
            # que rodear ela pra chegar no alvo, nao atravessar direto.
            if (viz != fim and (viz in bloqueados or eh_parede(grade, viz))) or borda_bloqueada(grade, atual, viz): continue
            ng = g + 1
            if ng < custo.get(viz, 1 << 30):
                custo[viz] = ng
                veio[viz] = atual
                # desempate por g maior: prefere seguir reto até o fim
                heapq.heappush(aberto, (ng + h(viz), -ng, viz))
    return None

def astar_ate_adjacente(grade, inicio, alvo, bloqueados=None, limite=ASTAR_LIMITE_NOS):
    # Igual ao astar(), mas o destino é qualquer SQM livre EM VOLTA do alvo (as
    # 8 casas, diagonal conta) e não o SQM dele: o mob para no mais perto. Se
    # estava na diagonal e o player deu um passo, é 1 passo reto e ele continua
    # na diagonal. Antes ia pro SQM do player e o desempate às vezes escolhia
    # 2 passos terminando reto (saía da diagonal a cada SQM, ruim pros beams).
    # Só sai da diagonal se a casa dela estiver bloqueada (parede/criatura) ou
    # se o próprio player se alinhar com ele.
    bloqueados = bloqueados or ()
    def eh_fim(t): return t != alvo and _adjacente(t, alvo)
    if eh_fim(inicio): return [inicio]
    # Distância (4 direções) até o quadrado 3x3 em volta do alvo.
    def h(t): return max(abs(t[0] - alvo[0]) - 1, 0) + max(abs(t[1] - alvo[1]) - 1, 0)
    aberto = [(h(inicio), 0, inicio)]
    veio = {inicio: None}
    custo = {inicio: 0}
    expandidos = 0
    while aberto:
        _, g_neg, atual = heapq.heappop(aberto)
        g = -g_neg
        if eh_fim(atual):
            caminho = [atual]
            while veio[caminho[-1]] is not None:
                caminho.append(veio[caminho[-1]])
            caminho.reverse()
            return caminho
        if g > custo.get(atual, 1 << 30): continue
        expandidos += 1
        if expandidos > limite: return None
        for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            viz = (atual[0] + dx, atual[1] + dy)
            # Aqui (ao contrario do astar() acima) viz == alvo TAMBEM pula -
            # o mob para do LADO do alvo, nunca em cima; nao tem excecao de
            # "destino sempre alcancavel" nenhuma pra preservar, entao
            # borda_bloqueada so' entra como mais um OR de bloqueio normal.
            if viz == alvo or viz in bloqueados or eh_parede(grade, viz) or borda_bloqueada(grade, atual, viz): continue
            ng = g + 1
            if ng < custo.get(viz, 1 << 30):
                custo[viz] = ng
                veio[viz] = atual
                heapq.heappush(aberto, (ng + h(viz), -ng, viz))
    return None

def tipo_do_mob_id(mob_id):
    # IDs vêm do mob.gd: "<mob_type_id>_<x>_<y>" (x/y podem ser negativos).
    partes = mob_id.rsplit('_', 2)
    return partes[0].lower() if len(partes) == 3 else mob_id.lower()

def spawn_do_mob_id(mob_id):
    partes = mob_id.rsplit('_', 2)
    try: return tile_de(float(partes[1]), float(partes[2]))
    except (IndexError, ValueError): return None

def obter_ou_criar_mob(mob_id, mob_type_id=None, room=None):
    # Cria a entrada do mob com HP do MOB_DB e posição = spawn (tirado do id).
    mob_data = active_mobs.get(mob_id)
    if mob_data is None:
        tipo = (mob_type_id or tipo_do_mob_id(mob_id)).lower()
        max_hp = MOB_DB.get(tipo, {}).get("hp", 40)
        spawn = spawn_do_mob_id(mob_id)
        mob_data = {"hp": max_hp, "max_hp": max_hp, "type_id": tipo, "dmg_tracker": {},
                    "spawn": spawn, "direction": "down", "returning": False, "target_sid": None,
                    "move_until": 0.0, "next_attack": 0.0, "sem_caminho": 0.0, "last_tick": time.time(),
                    "speed": MOB_DB.get(tipo, {}).get("speed", MOB_SPEED_PADRAO),
                    "cooldown": MOB_DB.get(tipo, {}).get("cooldown", MOB_COOLDOWN_PADRAO),
                    "hit_effect": "physical_hit", "mapa": None, "path": None, "alcance": {}}
        if spawn is not None:
            mob_data['pos_x'], mob_data['pos_y'] = centro_tile(spawn)
            mob_data['room'] = get_chunk(mob_data['pos_x'], mob_data['pos_y'], MOB_FLOOR)
        else:
            mob_data['room'] = room
        active_mobs[mob_id] = mob_data
    return mob_data

def tile_do_mob(m):
    return tile_de(m.get('pos_x', 0), m.get('pos_y', 0))

def _tile_de_nascimento(mob_id, m):
    """SQM onde o mob (re)nasce: qualquer SQM livre (sem parede, sem player/mob
    em cima) dentro de spawn_range SQMs do ponto marcado no Tiled. Sem grade de
    colisao ainda ou sem SQM livre, usa o proprio ponto."""
    centro = spawn_do_mob_id(mob_id)
    if centro is None: return None
    alcance = int(m.get('spawn_range', 0) or 0)
    if alcance <= 0: return centro
    grade = mapas_colisao.get(m.get('mapa'))
    if grade is None: return centro
    cx, cy = centro_tile(centro)
    ocupados = _tiles_ocupados(excluir_mob=mob_id, sala=get_chunk(cx, cy, MOB_FLOOR))
    livres = [(centro[0] + dx, centro[1] + dy)
              for dx in range(-alcance, alcance + 1) for dy in range(-alcance, alcance + 1)]
    livres = [t for t in livres if not eh_parede(grade, t) and t not in ocupados]
    return random.choice(livres) if livres else centro

def payload_mob_pos(mob_id, m, step=0.0):
    # [mob_id, x, y, dir_int, owner(sempre ""), returning, duração do passo]
    return [mob_id, m.get('pos_x', 0), m.get('pos_y', 0), DIR_TO_INT.get(m.get('direction', 'down'), 0),
            '', 1 if m.get('returning') else 0, step]

def _mob_emitir_estado(mob_id, m, step=0.0):
    if m.get('room'):
        emit_area('mob_pos', payload_mob_pos(mob_id, m, step), m['room'])

def _player_alvo_valido(sid, m):
    p = online_players.get(sid)
    if p is None or p.get('is_dead'): return None
    try:
        if float(p.get('current_hp', -1)) == 0: return None
    except (TypeError, ValueError): pass
    if int(p.get('floor', 1) or 1) != MOB_FLOOR: return None
    if m.get('room') and p.get('room') not in salas_vizinhas(m['room']): return None
    if na_pz(p): return None  # Protection Zone: mob nao mira
    return p

# Igual _player_alvo_valido, mas SEM checar andar: usado pra manter a
# perseguição de um alvo que já estava mirado antes de trocar de andar (ex:
# subiu a escada fugindo). O andar só barra o ATAQUE em si (_mob_tick) e a
# escolha de NOVO alvo (aqui embaixo e em mob_focar_agressor) — assim o mob
# ainda tenta ir até a escada/transição em vez de travar na hora.
def _player_pursuit_valido(sid):
    p = online_players.get(sid)
    if p is None or p.get('is_dead'): return None
    try:
        if float(p.get('current_hp', -1)) == 0: return None
    except (TypeError, ValueError): pass
    return p

def _dist_spawn_sqm(m):
    if m.get('spawn') is None: return 0.0
    sx, sy = centro_tile(m['spawn'])
    return math.hypot(m.get('pos_x', 0) - sx, m.get('pos_y', 0) - sy) / TILE

def _dist_px(m, p):
    return math.hypot(float(p.get('pos_x', 0)) - m.get('pos_x', 0), float(p.get('pos_y', 0)) - m.get('pos_y', 0))

def _adjacente(a, b):
    return max(abs(a[0] - b[0]), abs(a[1] - b[1])) <= 1

def _direcao_para(atual, origem, alvo):
    # Pra onde olhar pra encarar o alvo. Na diagonal exata faz igual ao Tibia:
    # se já está olhando pra um dos dois lados do alvo, continua (não fica
    # trocando de lado); senão vira 90° pro lado do alvo.
    dx, dy = alvo[0] - origem[0], alvo[1] - origem[1]
    if dx == 0 and dy == 0: return atual
    h = 'right' if dx > 0 else 'left'
    v = 'down' if dy > 0 else 'up'
    if abs(dx) > abs(dy): return h
    if abs(dy) > abs(dx): return v
    if atual in (h, v): return atual
    return v if atual in ('left', 'right') else h

def _virar_para(m, origem, alvo):
    m['direction'] = _direcao_para(m.get('direction', 'down'), origem, alvo)

def _npc_payload(npc_id, npc):
    return {
        'id': npc_id,
        'npc_id': npc.get('npc_id', ''),
        'map': npc.get('mapa', ''),
        'x': npc.get('pos_x', 0),
        'y': npc.get('pos_y', 0),
        'direction': npc.get('direction', 'down'),
    }

def _registrar_npcs_do_mapa(map_id, definicoes, sid):
    agora = time.time()
    for definicao in (definicoes or [])[:256]:
        if not isinstance(definicao, dict): continue
        npc_id = str(definicao.get('npc_id', '')).strip().lower()
        instance_id = str(definicao.get('id', '')).strip()[:120]
        if npc_id not in NPC_DB or not instance_id: continue
        try:
            x, y = float(definicao['x']), float(definicao['y'])
            floor = max(1, min(100, int(definicao.get('floor', 1))))
        except (KeyError, TypeError, ValueError):
            continue
        if not (math.isfinite(x) and math.isfinite(y) and abs(x) < 100000000 and abs(y) < 100000000): continue
        chave = f'{map_id}:{instance_id}'
        if chave in active_npcs: continue
        spawn = tile_de(x, y)
        pos_x, pos_y = centro_tile(spawn)
        active_npcs[chave] = {
            'npc_id': npc_id,
            'mapa': map_id,
            'floor': floor,
            'spawn': spawn,
            'pos_x': pos_x,
            'pos_y': pos_y,
            'direction': 'down',
            'path': None,
            'move_until': 0.0,
            'next_move_at': agora + random.uniform(NPC_WANDER_MIN_WAIT, NPC_WANDER_MAX_WAIT),
            'room': get_chunk(pos_x, pos_y, floor),
        }
    socketio.emit('npc_sync', {
        'map': map_id,
        'npcs': [_npc_payload(npc_id, npc) for npc_id, npc in active_npcs.items() if npc.get('mapa') == map_id]
    }, room=sid)

def _npc_dar_passo(npc_id, npc, origem, destino, now):
    if max(abs(destino[0] - npc['spawn'][0]), abs(destino[1] - npc['spawn'][1])) > NPC_WANDER_RADIUS_SQM:
        return False
    if eh_parede(mapas_colisao.get(npc['mapa']), destino) or borda_bloqueada(mapas_colisao.get(npc['mapa']), origem, destino):
        return False
    _virar_para(npc, origem, destino)
    npc['pos_x'], npc['pos_y'] = centro_tile(destino)
    npc['move_until'] = now + NPC_STEP_SECONDS
    npc['next_move_at'] = npc['move_until'] + random.uniform(NPC_WANDER_MIN_WAIT, NPC_WANDER_MAX_WAIT)
    npc['room'] = get_chunk(npc['pos_x'], npc['pos_y'], npc.get('floor', 1))
    emit_area('npc_moved', _npc_payload(npc_id, npc), npc['room'])
    return True

def _npc_tick(npc_id, npc, now):
    grade = mapas_colisao.get(npc.get('mapa'))
    if grade is None or npc.get('spawn') is None: return
    if now < npc.get('next_move_at', 0.0) or now < npc.get('move_until', 0.0): return
    origem = tile_de(npc.get('pos_x', 0), npc.get('pos_y', 0))
    sx, sy = npc['spawn']
    destinos = [
        (origem[0] + dx, origem[1] + dy)
        for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0))
        if abs(origem[0] + dx - sx) <= NPC_WANDER_RADIUS_SQM
        and abs(origem[1] + dy - sy) <= NPC_WANDER_RADIUS_SQM
    ]
    random.shuffle(destinos)
    for destino in destinos:
        if eh_parede(grade, destino): continue
        ocupada_por_jogador = any(
            not p.get('is_dead')
            and int(p.get('floor', 1) or 1) == int(npc.get('floor', 1))
            and tile_de(p.get('pos_x', 0), p.get('pos_y', 0)) == destino
            for p in online_players.values()
        )
        if ocupada_por_jogador: continue
        ocupada_por_npc = any(
            outro_id != npc_id and outro.get('mapa') == npc.get('mapa')
            and int(outro.get('floor', 1)) == int(npc.get('floor', 1))
            and tile_de(outro.get('pos_x', 0), outro.get('pos_y', 0)) == destino
            for outro_id, outro in active_npcs.items()
        )
        if ocupada_por_npc: continue
        if _npc_dar_passo(npc_id, npc, origem, destino, now): return
    npc['next_move_at'] = now + random.uniform(NPC_WANDER_MIN_WAIT, NPC_WANDER_MAX_WAIT)

def npc_ai_loop():
    while True:
        socketio.sleep(NPC_TICK_SECONDS)
        now = time.time()
        ativas = _salas_ativas()
        for npc_id, npc in list(active_npcs.items()):
            if npc.get('room') not in ativas: continue  # ninguem vendo: parado
            try:
                _npc_tick(npc_id, npc, now)
            except Exception:
                traceback.print_exc()

def _mob_encarar(mob_id, m, origem, t_alvo):
    # Vira pro alvo e avisa os clients só quando a direção muda. Antes o mob só
    # virava no ataque (a cada cooldown, 3s) e nem mandava a direção nova: se o
    # player andava em volta dele, o mob ficava olhando pro lado errado.
    antes = m.get('direction')
    _virar_para(m, origem, t_alvo)
    if m['direction'] != antes:
        _mob_emitir_estado(mob_id, m)

# ---- Indice por area (refeito a cada tick da IA dos mobs) ----
# sala -> ids/sids que estao nela. Evita varrer TODOS os mobs/players do
# servidor a cada passo/busca: com centenas de players e mobs isso era
# milhoes de checagens por segundo. As posicoes continuam lidas ao vivo do
# dict de cada um; o indice so' diz QUEM olhar (a area 3x3 de chunks em
# volta cobre quem andou de chunk desde o ultimo tick).
_mobs_por_sala = {}
_players_por_sala = {}

def _reindexar_areas():
    global _mobs_por_sala, _players_por_sala
    mobs = {}
    for m_id, m in active_mobs.items():
        sala = m.get('room')
        if sala: mobs.setdefault(sala, []).append(m_id)
    players = {}
    for sid, p in online_players.items():
        sala = p.get('room')
        if sala: players.setdefault(sala, []).append(sid)
    _mobs_por_sala, _players_por_sala = mobs, players

def _mobs_perto(sala):
    for r in salas_vizinhas(sala):
        for m_id in _mobs_por_sala.get(r, ()):
            m = active_mobs.get(m_id)
            if m is not None: yield m_id, m

def _sids_perto(sala):
    for r in salas_vizinhas(sala):
        for sid in _players_por_sala.get(r, ()):
            if sid in online_players: yield sid

def _tiles_ocupados(excluir_mob=None, excluir_sid=None, sala=None):
    # sala: so' olha a area 3x3 em volta (bem mais barato). Sem sala, o
    # servidor inteiro (so' em lugar raro, fora dos loops).
    ocupados = set()
    mobs = _mobs_perto(sala) if sala else active_mobs.items()
    for o_id, o in mobs:
        if o_id == excluir_mob or o.get('hp', 1) <= 0 or 'pos_x' not in o: continue
        ocupados.add(tile_do_mob(o))
    sids = _sids_perto(sala) if sala else online_players.keys()
    for o_sid in sids:
        p = online_players.get(o_sid)
        if p is None or o_sid == excluir_sid or p.get('is_dead'): continue
        if int(p.get('floor', 1) or 1) != MOB_FLOOR: continue
        ocupados.add(tile_de(p.get('pos_x', 0), p.get('pos_y', 0)))
    return ocupados

def _salas_ativas():
    """Salas com algum player nela ou do lado (o que aparece na tela de
    alguem). Mob/NPC fora disso fica parado feito estatua."""
    ativas = set()
    for sala in _players_por_sala:
        ativas.update(salas_vizinhas(sala))
    return ativas

def _alcanca(m, sid, origem, destino, now):
    # "Dá pra chegar no player?" — só paredes (criaturas andam). Cache 0.5s.
    c = m['alcance'].get(sid)
    if c and c[1] == origem and c[2] == destino and now - c[0] < ALCANCE_RECALC_SEG:
        return c[3]
    ok = astar(mapas_colisao.get(m.get('mapa')), origem, destino) is not None
    m['alcance'][sid] = (now, origem, destino, ok)
    return ok

def _mob_voltar_pra_casa(mob_id, m):
    m['target_sid'] = None
    m['sem_caminho'] = 0.0
    m['bloqueado_criatura'] = 0.0
    m['path'] = None
    voltando = m.get('spawn') is not None and tile_do_mob(m) != m['spawn']
    if voltando != m.get('returning'):
        m['returning'] = voltando
        _mob_emitir_estado(mob_id, m)

# Perseguiu longe demais (LEASH_SQM) do próprio spawn: em vez de voltar
# andando (às vezes cruzando o mapa inteiro), some com o efeito SpawnEffect no
# client e conta como morto pra tudo (mob_cleanup_loop cuida do respawn depois
# de MOB_RESPAWN_SEG, igual uma morte de verdade). Sem loot/xp: ninguém matou.
def _mob_desaparece_por_perseguicao(mob_id, m, now):
    m['target_sid'] = None
    m['returning'] = False
    m['path'] = None
    m['sem_caminho'] = 0.0
    m['dmg_tracker'] = {}
    m['hp'] = 0
    m['died_at'] = now
    room = m.get('room')
    if room:
        emit_area('mob_vanish', {'mob_id': mob_id, 'pos_x': m.get('pos_x', 0), 'pos_y': m.get('pos_y', 0)}, room)

def _duracao_passo_mob(m, origem, destino):
    # Tempo de 1 passo do mob: speed dele x speed_modifier do SQM de onde sai
    # (mesma tabela dos players, velocidade_tiles). O client recebe essa
    # duração no mob_state, então a animação acompanha.
    vel = velocidade_tiles.get(m.get('mapa')) or {}
    # Metade do passo na velocidade do SQM de saida, metade na do de chegada.
    fator = 0.5 / max(0.05, vel.get(origem, 1.0)) + 0.5 / max(0.05, vel.get(destino, 1.0))
    return fator / max(0.1, float(m.get('speed', MOB_SPEED_PADRAO)))

def _mob_dar_passo(mob_id, m, destino, now, ate_adjacente=False):
    # ate_adjacente: perseguindo um player (para em volta dele, ver
    # astar_ate_adjacente). Sem ele vai até o próprio destino (volta pro spawn).
    grade = mapas_colisao.get(m.get('mapa'))
    if grade is None: return False
    origem = tile_do_mob(m)
    ocupados = _tiles_ocupados(excluir_mob=mob_id, sala=m.get('room'))
    # Mob nao entra na Protection Zone (se ja' esta dentro, deixa sair).
    pz = zonas_protegidas.get(m.get('mapa'))
    if pz and origem not in pz:
        livres = ocupados - {destino}
        ocupados = _Bloqueados(ocupados, pz)
        livres = _Bloqueados(livres, pz)
    else:
        livres = ocupados - {destino}
    cache = m.get('path')
    caminho = None
    if cache and cache['fim'] == destino and cache.get('adj') == ate_adjacente and cache['caminho'] and cache['caminho'][0] == origem and now - cache['t'] < PATH_RECALC_SEG:
        caminho = cache['caminho']
    else:
        if ate_adjacente:
            caminho = astar_ate_adjacente(grade, origem, destino, ocupados)
        else:
            caminho = astar(grade, origem, destino, livres)
        m['path'] = {'fim': destino, 'adj': ate_adjacente, 'caminho': caminho, 't': now}
    if not caminho or len(caminho) < 2: return False
    proximo = caminho[1]
    if proximo in ocupados:
        # Alguém entrou no SQM (ou é o próprio player-alvo / spawn ocupado):
        # espera e recalcula no próximo tick.
        m['path'] = None
        return False
    _virar_para(m, origem, proximo)
    m['pos_x'], m['pos_y'] = centro_tile(proximo)
    passo = _duracao_passo_mob(m, origem, proximo)
    m['move_until'] = now + passo
    m['path']['caminho'] = caminho[1:]
    m['room'] = get_chunk(m['pos_x'], m['pos_y'], MOB_FLOOR)
    _mob_emitir_estado(mob_id, m, passo)
    return True

def _mob_atacar(mob_id, m, target_sid, now):
    target_player = online_players.get(target_sid)
    if target_player is None: return
    target_name = target_player.get('name', '')
    m['next_attack'] = now + max(0.3, float(m.get('cooldown', MOB_COOLDOWN_PADRAO)))
    m['ultimo_golpe'] = now
    mob_attack_damage = MOB_DB.get(m.get('type_id'), {}).get("attack", 3.0)
    p_class = target_player.get('class_name', 'Knight')
    bonus_itens = somar_bonus_combate_equipados(target_player.get('equipped_items', {}))
    _, def_value = calc_player_stats(p_class, target_player.get('level', 1), target_player.get('skills', {}), bonus_def=bonus_itens['bonus_defense'])
    dano_final = 0 if random.random() < BLOCK_CHANCE else max(1.0, mob_attack_damage - def_value)
    # Medalha de ouro no bestiary desse mob: -5% do dano recebido dele.
    if dano_final > 0 and bestiario_ouro(target_player, m.get('type_id')):
        dano_final = max(1.0, dano_final * (1.0 - BESTIARIO_BONUS_OURO))

    max_hp_alvo, _ = calcular_max_vitais(target_player)
    hp_atual = float(target_player.get('current_hp', -1))
    if hp_atual < 0: hp_atual = max_hp_alvo
    hp_atual = max(0.0, hp_atual - dano_final)
    target_player['current_hp'] = hp_atual
    target_player['_ultimo_hp_broadcast'] = [hp_atual, max_hp_alvo]
    target_player['last_hit_by_mob'] = now  # libera o treino de defense (register_skill_hit)

    emit_area('player_damaged', {'target_player': target_name, 'damage': dano_final, 'new_hp': hp_atual,
                                 'max_hp': max_hp_alvo, 'hit_type': m.get('hit_effect', 'physical_hit'),
                                 'attacker_mob_id': mob_id}, target_player.get('room'))
    if hp_atual <= 0:
        target_player['is_dead'] = True
        sair_da_batalha(target_sid, target_player)
        emit_area('player_status_updated', {"name": target_name, "is_dead": True}, target_player.get('room'), skip_sid=target_sid)
        # Aviso "X has been killed by <mob>" no chat de quem está perto (o
        # próprio morto incluso). O client pega o nome exibido do mob pelo id.
        emit_area('player_killed', {'name': target_name, 'mob_id': mob_id, 'mob_type': m.get('type_id', '')}, target_player.get('room'))

def _mob_tick(mob_id, m, now):
    if m.get('hp', 1) <= 0 or m.get('spawn') is None or 'pos_x' not in m: return
    dt = min(0.5, max(0.0, now - m.get('last_tick', now)))
    m['last_tick'] = now
    origem = tile_do_mob(m)

    # Ataque (pode acontecer até no meio de um passo, igual era no client).
    # Exige o MESMO andar (_player_alvo_valido) — só perseguir não basta pra
    # bater; ver o bloco de perseguição mais abaixo.
    alvo_sid = m.get('target_sid')
    # Mob focando o player: mantém ele em battle (antes do "esperando o
    # passo acabar" lá embaixo, senão mob lento deixava buracos).
    if alvo_sid is not None and not m.get('returning'):
        marcar_batalha(alvo_sid, online_players.get(alvo_sid), now)
    alvo_combate = _player_alvo_valido(alvo_sid, m) if alvo_sid else None
    if alvo_combate is not None and not m.get('returning'):
        t_alvo = tile_de(alvo_combate.get('pos_x', 0), alvo_combate.get('pos_y', 0))
        if _adjacente(origem, t_alvo) and now >= m.get('next_attack', 0) and now >= m.get('move_until', 0):
            _mob_encarar(mob_id, m, origem, t_alvo)  # a virada chega antes do ataque
            _mob_atacar(mob_id, m, alvo_sid, now)

    if now < m.get('move_until', 0): return

    if m.get('returning'):
        spawn = m['spawn']
        perto_de_casa = abs(origem[0] - spawn[0]) + abs(origem[1] - spawn[1]) <= 2
        if origem == spawn or (perto_de_casa and m.get('sem_caminho', 0) >= SEM_CAMINHO_SEG):
            m['returning'] = False
            m['sem_caminho'] = 0.0
            _mob_emitir_estado(mob_id, m)
        elif _mob_dar_passo(mob_id, m, spawn, now):
            m['sem_caminho'] = 0.0
        else:
            m['sem_caminho'] = m.get('sem_caminho', 0) + dt
        return

    if alvo_sid is not None:
        # Perseguição: NÃO exige o mesmo andar (só existir e estar vivo) —
        # assim o mob ainda vai atrás de quem subiu a escada fugindo, em vez
        # de travar/desistir na hora só por causa do andar (o andar já barra
        # o ataque em si, lá em cima). Escolher um NOVO alvo continua exigindo
        # o mesmo andar (mais abaixo).
        alvo = _player_pursuit_valido(alvo_sid)
        if alvo is None or na_pz(alvo):  # morreu, saiu, desconectou, entrou na PZ
            _mob_voltar_pra_casa(mob_id, m)
            return
        if _dist_px(m, alvo) > (DETECCAO_SQM + PERSISTE_SQM) * TILE:
            _mob_voltar_pra_casa(mob_id, m)
            return
        if _dist_spawn_sqm(m) > LEASH_SQM:
            _mob_desaparece_por_perseguicao(mob_id, m, now)
            return
        t_alvo = tile_de(alvo.get('pos_x', 0), alvo.get('pos_y', 0))
        if _adjacente(origem, t_alvo):
            m['sem_caminho'] = 0.0
            m['bloqueado_criatura'] = 0.0
            _mob_encarar(mob_id, m, origem, t_alvo)
            return
        if _alcanca(m, alvo_sid, origem, t_alvo, now):
            m['sem_caminho'] = 0.0
            if _mob_dar_passo(mob_id, m, t_alvo, now, ate_adjacente=True):
                m['bloqueado_criatura'] = 0.0
            else:
                # Bloqueado por criatura (outro mob/player no caminho): espera
                # olhando pro alvo, mas se continuar trancado desiste com flag.
                m['bloqueado_criatura'] = m.get('bloqueado_criatura', 0) + dt
                if m['bloqueado_criatura'] >= BLOQUEADO_CRIATURA_SEG:
                    _mob_voltar_pra_casa(mob_id, m)
                else:
                    _mob_encarar(mob_id, m, origem, t_alvo)
        else:
            m['sem_caminho'] = m.get('sem_caminho', 0) + dt
            if m['sem_caminho'] >= SEM_CAMINHO_SEG:
                _mob_voltar_pra_casa(mob_id, m)
            else:
                _mob_encarar(mob_id, m, origem, t_alvo)
        return

    # Sem alvo: procura o player mais perto (5 SQMs) que dê pra alcançar.
    # _player_alvo_valido já filtra andar: um mob parado ignora quem já está
    # num andar diferente (só continua perseguindo quem virou alvo ANTES de
    # trocar de andar, no bloco acima).
    melhor, melhor_dist = None, DETECCAO_SQM * TILE
    for sid in list(_sids_perto(m['room'])) if m.get('room') else list(online_players.keys()):
        p = _player_alvo_valido(sid, m)
        if p is None: continue
        d = _dist_px(m, p)
        if d > melhor_dist: continue
        t_p = tile_de(p.get('pos_x', 0), p.get('pos_y', 0))
        if _adjacente(origem, t_p) or _alcanca(m, sid, origem, t_p, now):
            melhor, melhor_dist = sid, d
    if melhor is not None:
        _mob_definir_alvo(m, melhor, now)
        m['sem_caminho'] = 0.0
        return

    _mob_passear(mob_id, m, origem, now)

def _mob_passear(mob_id, m, origem, now):
    """Sem alvo: igual o NPC (_npc_tick), da' 1 passo aleatorio de tempos em
    tempos, sem sair de MOB_WANDER_RADIUS_SQM da casa dele (menor que a do
    NPC). Cada mob tem o proprio relogio aleatorio (5-10s), pra nao andarem
    todos juntos."""
    if 'next_wander_at' not in m:
        m['next_wander_at'] = now + random.uniform(MOB_WANDER_MIN_WAIT, MOB_WANDER_MAX_WAIT)
        return
    if now < m['next_wander_at']: return
    m['next_wander_at'] = now + random.uniform(MOB_WANDER_MIN_WAIT, MOB_WANDER_MAX_WAIT)
    grade = mapas_colisao.get(m.get('mapa'))
    casa = m.get('spawn')
    if grade is None or casa is None: return
    ocupados = _tiles_ocupados(excluir_mob=mob_id, sala=m.get('room'))
    destinos = [(origem[0] + dx, origem[1] + dy) for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0))]
    random.shuffle(destinos)
    for destino in destinos:
        if max(abs(destino[0] - casa[0]), abs(destino[1] - casa[1])) > MOB_WANDER_RADIUS_SQM: continue
        if destino in ocupados or eh_parede(grade, destino) or borda_bloqueada(grade, origem, destino): continue
        if na_pz(m, destino): continue
        _virar_para(m, origem, destino)
        m['pos_x'], m['pos_y'] = centro_tile(destino)
        passo = _duracao_passo_mob(m, origem, destino)
        m['move_until'] = now + passo
        m['path'] = None
        m['room'] = get_chunk(m['pos_x'], m['pos_y'], MOB_FLOOR)
        _mob_emitir_estado(mob_id, m, passo)
        m['next_wander_at'] = m['move_until'] + random.uniform(MOB_WANDER_MIN_WAIT, MOB_WANDER_MAX_WAIT)
        return

def _mob_definir_alvo(m, sid, now=None):
    # Troca de alvo zera o relógio de "focando sem bater" (ver mob_focar_agressor).
    if m.get('target_sid') != sid:
        m['alvo_desde'] = time.time() if now is None else now
    m['target_sid'] = sid

def _mob_preso_sem_bater(m, now):
    # Mob focando o mesmo alvo há TROCA_ALVO_SEM_HIT_SEG sem conseguir bater
    # (desde que mirou ou desde o último golpe, o que for mais recente).
    ultimo = max(m.get('alvo_desde', 0), m.get('ultimo_golpe', 0))
    return now - ultimo >= TROCA_ALVO_SEM_HIT_SEG

def mob_focar_agressor(mob_id, m, sid):
    # Quem bate no mob vira o alvo se ele estava sem alvo ou voltando pra casa.
    # Também troca de alvo se o mob está focando alguém há 4s sem conseguir
    # bater (anti-abuso: um player "segurando" o mob longe enquanto outro bate).
    if m.get('hp', 1) <= 0: return
    now = time.time()
    atual = m.get('target_sid')
    if atual == sid: return
    if atual is not None and not m.get('returning') and not _mob_preso_sem_bater(m, now): return
    p = _player_alvo_valido(sid, m)
    if p is None or _dist_px(m, p) > (DETECCAO_SQM + PERSISTE_SQM) * TILE: return
    _mob_definir_alvo(m, sid, now)
    m['sem_caminho'] = 0.0
    if m.get('returning'):
        m['returning'] = False
        _mob_emitir_estado(mob_id, m)

def mob_ai_loop():
    while True:
        socketio.sleep(MOB_TICK_SEG)
        now = time.time()
        _reindexar_areas()
        ativas = _salas_ativas()
        for mob_id, m in list(active_mobs.items()):
            # Longe de todo mundo: estatua (nao anda, nao procura alvo, nao
            # passeia). Volta a agir quando alguem chega perto.
            if m.get('room') not in ativas:
                m['last_tick'] = now
                continue
            try:
                _mob_tick(mob_id, m, now)
            except Exception:
                traceback.print_exc()

def montar_sync_area(sid, room):
    rooms = set(salas_vizinhas(room))
    players_na_area = [resumo_player_area(o_p) for o_sid, o_p in online_players.items()
                       if o_sid != sid and o_p.get('room') in rooms]

    mobs_info = []
    for m_id, m_data in active_mobs.items():
        if m_data.get('room') not in rooms: continue
        morto = m_data.get('hp', 1) <= 0
        mob_payload = {
            "mob_id": m_id,
            "hp": m_data.get('hp', 0),
            "max_hp": m_data.get('max_hp', m_data.get('hp', 0)),
            "owner": "",
            "is_dead": morto,
            "returning": bool(m_data.get('returning', False)),
        }
        if morto:
            # Há quantos segundos morreu: o client só mostra o cadáver pelo
            # tempo que falta dos 60s (antes todo relog recriava o corpo).
            mob_payload["dead_for"] = max(0.0, time.time() - m_data.get('died_at', time.time()))
        if 'pos_x' in m_data and 'pos_y' in m_data:
            mob_payload["pos_x"] = m_data["pos_x"]
            mob_payload["pos_y"] = m_data["pos_y"]
            mob_payload["direction"] = m_data.get("direction", "down")
        mobs_info.append(mob_payload)

    # Bags DESTE player que ainda estão no chão na área (pra recriar no relog).
    bags = []
    p_nome = online_players.get(sid, {}).get('name')
    agora = time.time()
    for l_id, loot in ground_loot.items():
        if p_nome not in loot.get('owners', ()) or 'pos' not in loot: continue
        idade = agora - loot.get('criado_em', agora)
        if idade >= LOOT_BAG_VISIVEL_SEG: continue
        if get_chunk(loot['pos'][0], loot['pos'][1]) not in rooms: continue
        bags.append({"loot_id": l_id, "pos_x": loot['pos'][0], "pos_y": loot['pos'][1],
                     "has_items": bool(loot.get('has_items', True)), "idade": idade})
    return {"players": players_na_area, "mobs": mobs_info, "bags": bags}

def _posicao_e_vitals_para_salvar(player):
    pos_x, pos_y = player.get('pos_x', -1), player.get('pos_y', -1)
    current_hp, current_mp = player.get('current_hp', -1), player.get('current_mp', -1)
    if current_hp is not None and float(current_hp) == 0:
        pos_x, pos_y = -1, -1
        current_hp, current_mp = -1, -1
    return pos_x, pos_y, current_hp, current_mp

def _queue_save(p, position_ack_sid=None):
    if not p.get('user_id') or not p.get('name'): return
    pos_x, pos_y, hp, mp = _posicao_e_vitals_para_salvar(p)
    # time_played soma o que já estava salvo (time_played_base, carregado no
    # join) + os segundos decorridos desde que essa sessão começou
    # (session_start). Não é uma coluna gravada à parte: pega carona nos
    # saves que já existem (movimento, disconnect etc.), sem nenhum I/O extra.
    tempo_jogado = p.get('time_played_base', 0)
    if 'session_start' in p:
        tempo_jogado += int(time.time() - p['session_start'])

    save_data = {
        'user_id': p['user_id'], 'name': p['name'], 'pos_x': pos_x, 'pos_y': pos_y,
        'direction': p.get('direction', 'down'), 'floor': p.get('floor', 1),
        'level': p.get('level', 1), 'exp': p.get('exp', 0), 'kills': p.get('kills', 0),
        'current_hp': hp, 'current_mp': mp, 'currency': p.get('currency', 0),
        'inventory': p.get('inventory', []).copy() if isinstance(p.get('inventory'), list) else [],
        'equipped_items': p.get('equipped_items', {}).copy() if isinstance(p.get('equipped_items'), dict) else {},
        'skills': p.get('skills', {}).copy() if isinstance(p.get('skills'), dict) else {},
        'skins': p.get('skins', {}).copy() if isinstance(p.get('skins'), dict) else {},
        'npc_dialogue_state': p.get('npc_dialogue_state', {}).copy() if isinstance(p.get('npc_dialogue_state'), dict) else {},
        'hotbar': normalizar_hotbar(p.get('hotbar')),
        'time_played': tempo_jogado,
        'bestiary': dict(p['bestiary']) if isinstance(p.get('bestiary'), dict) else None,
        'position_ack_sid': position_ack_sid
    }
    save_queue.put(save_data)

def db_writer_worker():
    while True:
        player_data = save_queue.get()
        if player_data is None: continue
        conn = None
        try:
            conn = db_pool.getconn()
            c = conn.cursor()
            
            valid_inventory = normalizar_inventario(player_data.get('inventory', []))
            valid_equipped = normalizar_equipados(player_data.get('equipped_items', {}))
            
            query = """
                UPDATE characters
                SET pos_x = %s, pos_y = %s, direction = %s, floor = %s, level = %s,
                    exp = %s, kills = %s, current_hp = %s, current_mp = %s,
                    inventory = %s, equipped_items = %s, skills = %s, skins = %s, currency = %s,
                    time_played = %s, npc_dialogue_state = %s, hotbar = %s
                WHERE user_id = %s AND name = %s
            """
            params = (
                player_data.get('pos_x'), player_data.get('pos_y'), player_data.get('direction'),
                player_data.get('floor'), player_data.get('level'), player_data.get('exp'),
                player_data.get('kills'), player_data.get('current_hp'), player_data.get('current_mp'),
                json.dumps(valid_inventory), json.dumps(valid_equipped),
                json.dumps(player_data.get('skills')), json.dumps(player_data.get('skins')),
                int(player_data.get('currency', 0)), player_data.get('time_played', 0),
                json.dumps(player_data.get('npc_dialogue_state', {})),
                json.dumps(normalizar_hotbar(player_data.get('hotbar'))),
                player_data.get('user_id'), player_data.get('name')
            )
            c.execute(query, params)
            # Bestiary e' da conta (todos os personagens dela).
            if player_data.get('bestiary') is not None:
                c.execute("UPDATE users SET bestiary = %s WHERE id = %s",
                          (json.dumps(player_data['bestiary']), player_data.get('user_id')))
            conn.commit()
            c.close()
            if player_data.get('position_ack_sid'):
                socketio.emit('position_saved', {'name': player_data.get('name', '')}, room=player_data['position_ack_sid'])
        except Exception as e:
            print(f"[DB_WORKER_ERROR] {e}")
        finally:
            if conn: db_pool.putconn(conn)

def init_db():
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute('''CREATE TABLE IF NOT EXISTS users (
                        id SERIAL PRIMARY KEY,
                        email TEXT UNIQUE,
                        password TEXT)''')
        c.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_code TEXT")
        c.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_code_expires DOUBLE PRECISION")
        c.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS bestiary TEXT DEFAULT '{}'")
        c.execute('''CREATE TABLE IF NOT EXISTS characters (
                        id SERIAL PRIMARY KEY, 
                        user_id INTEGER REFERENCES users(id) ON DELETE CASCADE, 
                        name TEXT UNIQUE, 
                        class_name TEXT, 
                        level INTEGER DEFAULT 1,
                        exp INTEGER DEFAULT 0,
                        pos_x REAL DEFAULT -1,
                        pos_y REAL DEFAULT -1,
                        direction TEXT DEFAULT 'down',
                        skins TEXT DEFAULT '{}',
                        floor INTEGER DEFAULT 1,
                        inventory TEXT DEFAULT '[]',
                        equipped_items TEXT DEFAULT '{}',
                        skills TEXT DEFAULT '{}',
                        kills INTEGER DEFAULT 0,
                        current_hp REAL DEFAULT -1,
                        current_mp REAL DEFAULT -1,
                        currency INTEGER DEFAULT 0,
                        time_played INTEGER DEFAULT 0)''')
        c.execute("ALTER TABLE characters ADD COLUMN IF NOT EXISTS time_played INTEGER DEFAULT 0")
        # Guarda, por NPC, se o jogador ja viu o dialogo completo dele (ex:
        # {"kharon": true}) - pra nao repetir a fala de 1a vez (nem a
        # "quest"/pedido do NPC) toda vez que ele conversa de novo.
        c.execute("ALTER TABLE characters ADD COLUMN IF NOT EXISTS npc_dialogue_state TEXT DEFAULT '{}'")
        # Barra de atalhos: {"slots": [8]} (ver normalizar_hotbar).
        c.execute("ALTER TABLE characters ADD COLUMN IF NOT EXISTS hotbar TEXT DEFAULT '{}'")
        # Personagem "apagado" (soft delete, ver delete_character): NULL = ativo.
        c.execute("ALTER TABLE characters ADD COLUMN IF NOT EXISTS deleted_at DOUBLE PRECISION")
        c.execute('''CREATE TABLE IF NOT EXISTS friendships (
                        id SERIAL PRIMARY KEY,
                        owner_name TEXT NOT NULL,
                        friend_name TEXT NOT NULL,
                        icon_pk BOOLEAN DEFAULT FALSE,
                        icon_guild BOOLEAN DEFAULT FALSE,
                        icon_seller BOOLEAN DEFAULT FALSE,
                        UNIQUE(owner_name, friend_name))''')
        conn.commit()
        c.close()
    except Exception as e:
        print(f"Erro no INIT DB: {e}")
    finally:
        if conn: db_pool.putconn(conn)

RESET_CODE_TTL_SEG = 900  # 15 min

def enviar_codigo_reset(email, codigo):
    msg = MIMEText(f"Your password reset code is: {codigo}\n\nThis code expires in 15 minutes.")
    msg['Subject'] = "Password Reset Code"
    msg['From'] = GMAIL_SENDER
    msg['To'] = email
    with smtplib.SMTP('smtp.gmail.com', 587) as server:
        server.starttls()
        server.login(GMAIL_SENDER, GMAIL_APP_PASSWORD)
        server.sendmail(GMAIL_SENDER, email, msg.as_string())

@app.route('/ping', methods=['GET'])
def ping(): return jsonify({"status": "online"}), 200

@app.route('/check_version', methods=['POST'])
def check_version():
    dados = _json_requisicao()
    if dados.get('version', '') == SERVER_VERSION: return jsonify({"valid": True, "mensagem": "Version OK."}), 200
    return jsonify({"valid": False, "erro": "Version outdated, please update your game"}), 426

@app.route('/register', methods=['POST'])
def register():
    dados = _json_requisicao()
    email = str(dados.get('email') or '').strip().lower()
    senha = str(dados.get('password') or '')
    if not email or not senha: return jsonify({"erro": "Missing fields"}), 400
    if len(email) > 254 or not RE_EMAIL.match(email): return jsonify({"erro": "Invalid email."}), 400
    if not (SENHA_MIN <= len(senha) <= SENHA_MAX): return jsonify({"erro": "Invalid password."}), 400
    if _limite_excedido(('register', _ip()), 5, 3600): return _resposta_limite()
    hashed_password = _hash_senha(senha)
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("INSERT INTO users (email, password) VALUES (%s, %s) RETURNING id", (email, hashed_password))
        new_user_id = c.fetchone()[0]
        conn.commit()
        return jsonify({"mensagem": "Account created!", "user_id": new_user_id, "email": email,
                        "token": gerar_token(new_user_id, hashed_password)}), 201
    except psycopg2.IntegrityError:
        if conn: conn.rollback()
        return jsonify({"erro": "Email in use."}), 409
    except Exception as e: return _erro_interno(e)
    finally:
        if conn: db_pool.putconn(conn)

@app.route('/login', methods=['POST'])
def login():
    dados = _json_requisicao()
    email = str(dados.get('email') or '').strip().lower()
    senha = str(dados.get('password') or '')
    if not email or not senha: return jsonify({"erro": "Missing fields"}), 400
    # Forca bruta: por IP e por conta.
    if _limite_excedido(('login_ip', _ip()), 20, 300) or _limite_excedido(('login_email', email), 10, 300):
        return _resposta_limite()
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("SELECT id, password FROM users WHERE email = %s", (email,))
        user = c.fetchone()
        if user and _conferir_senha(user[1], senha):
            return jsonify({"mensagem": "Login ok!", "user_id": user[0], "email": email,
                            "token": gerar_token(user[0], user[1])}), 200
        return jsonify({"erro": "Invalid credentials."}), 401
    except Exception as e: return _erro_interno(e)
    finally:
        if conn: db_pool.putconn(conn)

RESET_TENTATIVAS_MAX = 5  # codigo errado 5x: o codigo e' anulado (precisa pedir outro)
_reset_erros = {}

@app.route('/forgot-password', methods=['POST'])
def forgot_password():
    dados = _json_requisicao()
    email = str(dados.get('email') or '').strip().lower()
    if not email: return jsonify({"erro": "Missing fields"}), 400
    if _limite_excedido(('forgot_ip', _ip()), 10, 3600) or _limite_excedido(('forgot_email', email), 3, 900):
        return _resposta_limite()
    codigo = f"{secrets.randbelow(1_000_000):06d}"
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("UPDATE users SET reset_code = %s, reset_code_expires = %s WHERE email = %s RETURNING id",
                   (codigo, time.time() + RESET_CODE_TTL_SEG, email))
        user = c.fetchone()
        if not user:
            conn.rollback()
            return jsonify({"erro": "Email not found."}), 404
        conn.commit()
        _reset_erros.pop(email, None)
        enviar_codigo_reset(email, codigo)
        return jsonify({"mensagem": "Code sent."}), 200
    except Exception as e:
        if conn: conn.rollback()
        return _erro_interno(e)
    finally:
        if conn: db_pool.putconn(conn)

@app.route('/reset-password', methods=['POST'])
def reset_password():
    dados = _json_requisicao()
    email = str(dados.get('email') or '').strip().lower()
    code = str(dados.get('code') or '').strip()
    nova = str(dados.get('new_password') or '')
    if not email or not code or not nova:
        return jsonify({"erro": "Missing fields"}), 400
    if not (SENHA_MIN <= len(nova) <= SENHA_MAX): return jsonify({"erro": "Invalid password."}), 400
    if _limite_excedido(('reset_ip', _ip()), 20, 900): return _resposta_limite()
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("SELECT reset_code, reset_code_expires FROM users WHERE email = %s", (email,))
        user = c.fetchone()
        if not user or user[0] is None or user[1] is None or user[1] < time.time() \
                or not hmac.compare_digest(str(user[0]), code):
            # Codigo de 6 digitos: sem esse limite dava pra testar todos.
            erros = _reset_erros.get(email, 0) + 1
            _reset_erros[email] = erros
            if user and erros >= RESET_TENTATIVAS_MAX:
                c.execute("UPDATE users SET reset_code = NULL, reset_code_expires = NULL WHERE email = %s", (email,))
                conn.commit()
                _reset_erros.pop(email, None)
            return jsonify({"erro": "Invalid or expired code."}), 400
        c.execute("UPDATE users SET password = %s, reset_code = NULL, reset_code_expires = NULL WHERE email = %s",
                   (_hash_senha(nova), email))
        conn.commit()
        _reset_erros.pop(email, None)
        return jsonify({"mensagem": "Password updated."}), 200
    except Exception as e:
        if conn: conn.rollback()
        return _erro_interno(e)
    finally:
        if conn: db_pool.putconn(conn)

@app.route('/create_character', methods=['POST'])
def create_character():
    dados = _json_requisicao()
    user_id = dados.get('user_id')
    name = str(dados.get('name') or '').strip()
    class_name = str(dados.get('class_name') or '').strip()
    if not user_id or not name or not class_name: return jsonify({"erro": "Missing fields."}), 400
    if class_name not in CLASSES_VALIDAS: return jsonify({"erro": "Invalid class."}), 400
    if not RE_NOME_PERSONAGEM.match(name) or "  " in name: return jsonify({"erro": "Invalid name."}), 400
    if any(banned in name.lower() for banned in BANNED_NAMES): return jsonify({"erro": "Inappropriate name."}), 403
    if _limite_excedido(('create_char', _ip()), 20, 600): return _resposta_limite()
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        if not token_valido(c, user_id, dados.get('token')): return jsonify({"erro": "Session expired."}), 401
        c.execute("SELECT COUNT(*) FROM characters WHERE user_id = %s AND deleted_at IS NULL", (user_id,))
        if c.fetchone()[0] >= 4: return jsonify({"erro": "No character slots."}), 403
        inventory_inicial, equipped_inicial = montar_kit_inicial(class_name)

        query = """INSERT INTO characters (user_id, name, class_name, level, exp, pos_x, pos_y, direction, skins, floor, inventory, equipped_items, skills, current_hp, current_mp, currency)
                   VALUES (%s, %s, %s, 1, 0, -1, -1, 'down', '{}', 1, %s, %s, '{}', -1, -1, 0)"""
        c.execute(query, (user_id, name, class_name, json.dumps(inventory_inicial), json.dumps(equipped_inicial)))
        conn.commit()
        return jsonify({"mensagem": "Character created!"}), 201
    except psycopg2.IntegrityError:
        if conn: conn.rollback()
        return jsonify({"erro": "Name taken."}), 409
    except Exception as e: return _erro_interno(e)
    finally:
        if conn: db_pool.putconn(conn)

@app.route('/get_characters', methods=['POST'])
def get_characters():
    dados = _json_requisicao()
    user_id = dados.get('user_id')
    if not user_id: return jsonify({"erro": "Missing ID."}), 400
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        # Sem token valido, qualquer um via os personagens de qualquer conta.
        if not token_valido(c, user_id, dados.get('token')): return jsonify({"erro": "Session expired."}), 401
        c.execute("SELECT name, class_name, level, exp, pos_x, pos_y, direction, skins, floor, inventory, equipped_items, skills, time_played FROM characters WHERE user_id = %s AND deleted_at IS NULL ORDER BY id ASC", (user_id,))
        char_list = []
        for row in c.fetchall():
            char_list.append({
                "name": row[0], "class_name": row[1], "level": row[2], "exp": row[3] if row[3] else 0,
                "pos_x": row[4], "pos_y": row[5], "direction": row[6],
                # Mesma validacao do jogo (roupa padrao da classe etc), pro
                # preview da tela de slots bater com o que aparece no mundo.
                "skins": validar_skins(json.loads(row[7]) if row[7] else {}, row[1]),
                "floor": row[8] if row[8] else 1,
                "inventory": normalizar_inventario(json.loads(row[9]) if row[9] else []),
                "equipped_items": normalizar_equipados(json.loads(row[10]) if row[10] else {}),
                "skills": json.loads(row[11]) if row[11] else {},
                "time_played": row[12] if row[12] else 0
            })
        # Personagem com o corpo ainda em battle (deslogou no meio da luta):
        # o client entra direto nele, sem passar pela seleção.
        em_batalha = next((p.get('name') for p in list(online_players.values())
                           if p.get('corpo_ausente') and str(p.get('user_id')) == str(user_id)), None)
        return jsonify({"characters": char_list, "in_battle": em_batalha}), 200
    except Exception as e: return _erro_interno(e)
    finally:
        if conn: db_pool.putconn(conn)

@app.route('/delete_character', methods=['POST'])
def delete_character():
    dados = _json_requisicao()
    if not dados.get('user_id') or not dados.get('name') or not dados.get('password'): return jsonify({"erro": "Missing fields."}), 400
    if _limite_excedido(('delete_char', _ip()), 10, 600): return _resposta_limite()
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        if not token_valido(c, dados['user_id'], dados.get('token')): return jsonify({"erro": "Session expired."}), 401
        c.execute("SELECT password FROM users WHERE id = %s", (dados['user_id'],))
        user = c.fetchone()
        if not user or not _conferir_senha(user[0], str(dados['password'])): return jsonify({"erro": "Wrong pass."}), 401
        # Personagem online nao pode ser apagado (ficava "fantasma" no mundo).
        if str(dados['name']) in players_by_name: return jsonify({"erro": "Character is online."}), 409
        # Nao apaga de verdade: renomeia pra "<nome>#del#<aleatorio>" e marca
        # deleted_at. Some da selecao de personagens, do ranking e libera o
        # nome; da' pra recuperar no banco (ver RECUPERAR_PERSONAGEM.md).
        nome_antigo = str(dados['name'])
        nome_apagado = f"{nome_antigo}#del#{secrets.token_hex(6)}"
        c.execute("""UPDATE characters SET name = %s, deleted_at = %s
                     WHERE user_id = %s AND name = %s AND deleted_at IS NULL""",
                  (nome_apagado, time.time(), dados['user_id'], nome_antigo))
        conn.commit()
        if c.rowcount: _ranking_cache.clear()  # sai do ranking na proxima abertura
        return jsonify({"mensagem": "Deleted!"}), 200
    except Exception as e: return _erro_interno(e)
    finally:
        if conn: db_pool.putconn(conn)

# Conexoes simultaneas por IP (abrir centenas de sockets de um PC so' pra
# derrubar o servidor). Familia/lan house atras do mesmo IP cabe folgado.
CONEXOES_POR_IP_MAX = 8
_conexoes_por_ip = {}
_ip_da_conexao = {}

@socketio.on('connect')
def handle_connect():
    ip = request.remote_addr or '?'
    if _conexoes_por_ip.get(ip, 0) >= CONEXOES_POR_IP_MAX:
        return False  # recusa a conexao
    _conexoes_por_ip[ip] = _conexoes_por_ip.get(ip, 0) + 1
    _ip_da_conexao[request.sid] = ip

@socketio.on('disconnect')
def handle_disconnect():
    sid = request.sid
    _baldes_eventos.pop(sid, None)
    if sid not in online_players: _remover_da_visao(sid)
    ip = _ip_da_conexao.pop(sid, None)
    if ip is not None:
        restante = _conexoes_por_ip.get(ip, 1) - 1
        if restante > 0: _conexoes_por_ip[ip] = restante
        else: _conexoes_por_ip.pop(ip, None)
    if sid in online_players:
        player = online_players[sid]
        p_name = player.get('name', 'Desconhecido')
        room = player.get('room')

        _remover_do_party(sid, motivo="disconnected")
        _limpar_convites_de_party_pendentes(sid, p_name)
        _remover_do_trade(sid, motivo="disconnected")
        _sair_de_todos_os_canais_chat(sid)

        _queue_save(player)

        if room: leave_room(room)
        # Em battle: o corpo continua no jogo (visível, apanhando) até o
        # battle acabar - battle_loop tira ele depois (_remover_corpo_ausente).
        if player.get('em_batalha') and not player.get('is_dead'):
            player['corpo_ausente'] = True
            return
        _remover_da_visao(sid)
        emit('player_left', {"name": p_name}, broadcast=True, include_self=False)
        players_by_name.pop(p_name, None)
        del online_players[sid]

@socketio.on('get_bestiary')
def handle_get_bestiary(data=None):
    p = online_players.get(request.sid)
    if p: emit('bestiary', montar_bestiario(p), room=request.sid)

@socketio.on('save_position')
def handle_save_position(data):
    # Salva a posicao que o SERVIDOR conhece (validada passo a passo no 'm').
    # Antes gravava o x/y que o client mandasse: dava pra se teleportar pra
    # qualquer lugar do mapa saindo e entrando de novo.
    sid = request.sid
    player = online_players.get(sid)
    if not player: return
    _queue_save(player, position_ack_sid=sid)

# ---------------------------------------------------------------------------
# SKINS (aparencia). Catalogo do que cada categoria aceita e quais classes
# podem usar. "caminho" = res://<regiao do atlas>.png; o client recorta a
# regiao "sprites/..." do graphics.atlas tirando o res:// e a extensao.
# classes=None -> qualquer classe. Pra adicionar uma skin: PNG no atlas +
# uma linha aqui (o client monta a tela de Vanity a partir disso).
# ---------------------------------------------------------------------------
CATEGORIAS_SKIN = ('base', 'body', 'helm', 'acc')
# Categorias que podem ficar vazias (sem nada desenhado por cima). Pele e
# roupa sao obrigatorias - sem roupa o client cai na primeira opcao da classe.
CATEGORIAS_SKIN_OPCIONAIS = ('helm', 'acc')

def _skin(caminho, nome, classes=None):
    return {"caminho": "res://" + caminho + ".png", "nome": nome, "classes": classes}

SKIN_DB = {
    "base": [
        _skin("sprites/base/BaseSoul", "Soul"),
        _skin("sprites/base/Base", "Light"),
        _skin("sprites/base/DarkBase", "Dark"),
    ],
    "body": [
        _skin("sprites/body/Knight", "Knight Outfit", ["Knight"]),
        _skin("sprites/body/Mage", "Mage Outfit", ["Mage"]),
        _skin("sprites/body/Ranger", "Ranger Outfit", ["Ranger"]),
        _skin("sprites/body/Bard", "Bard Outfit", ["Bard"]),
        _skin("sprites/equip_previews/Bard-Manto", "Bard Mantle", ["Bard"]),
    ],
    "helm": [
        _skin("sprites/hair/MascHair1", "Hair 1"), _skin("sprites/hair/MascHair2", "Hair 2"),
        _skin("sprites/hair/MascHair3", "Hair 3"), _skin("sprites/hair/MascHair4", "Hair 4"),
        _skin("sprites/hair/MascHair5", "Hair 5"), _skin("sprites/hair/FemHair1", "Hair 6"),
        _skin("sprites/hair/FemHair2", "Hair 7"), _skin("sprites/hair/FemHair3", "Hair 8"),
        _skin("sprites/hair/FemHair4", "Hair 9"), _skin("sprites/hair/FemHair5", "Hair 10"),
        _skin("sprites/equip_previews/Knight_Inicial_Helm", "Knight Helm", ["Knight"]),
        _skin("sprites/equip_previews/Mage_Inicial_Helm", "Mage Hat", ["Mage"]),
        _skin("sprites/equip_previews/Ranger_Inicial_Helm", "Ranger Hood", ["Ranger"]),
        _skin("sprites/equip_previews/Bard_Inicial_Helm", "Bard Cap", ["Bard"]),
        _skin("sprites/equip_previews/Bard-Hat", "Bard Hat", ["Bard"]),
    ],
    "acc": [
        _skin("sprites/equip_previews/Bard-Alaude", "Lute", ["Bard"]),
    ],
}

def _skin_por_caminho(categoria, caminho):
    for s in SKIN_DB.get(categoria, []):
        if s["caminho"] == caminho: return s
    return None

def _cor_skin_valida(cor):
    cor = str(cor or '').lower().lstrip('#')
    if len(cor) == 6: cor += 'ff'
    if len(cor) != 8 or any(ch not in '0123456789abcdef' for ch in cor): return 'ffffffff'
    return cor

def validar_skins(skins, class_name):
    """So' deixa passar skins do catalogo, de categorias conhecidas, que a
    classe do player pode usar; cor vira hex rrggbbaa. Nome vem do catalogo."""
    if not isinstance(skins, dict): skins = {}
    limpo = {}
    for cat in CATEGORIAS_SKIN:
        item = skins.get(cat)
        if not isinstance(item, dict): continue
        s = _skin_por_caminho(cat, str(item.get('caminho', '')))
        if s is None: continue
        if s["classes"] is not None and class_name not in s["classes"]: continue
        # Pele nao tem cor: o tom vem do proprio modelo (Light/Dark/Soul).
        cor = 'ffffffff' if cat == 'base' else _cor_skin_valida(item.get('cor'))
        limpo[cat] = {'nome': s["nome"], 'caminho': s["caminho"], 'cor': cor}
    # Roupa nunca fica vazia: sem uma valida, veste a primeira da classe.
    if 'body' not in limpo:
        padrao = next((s for s in SKIN_DB.get('body', [])
                       if s["classes"] is None or class_name in s["classes"]), None)
        if padrao is not None:
            limpo['body'] = {'nome': padrao["nome"], 'caminho': padrao["caminho"], 'cor': 'ffffffff'}
    return limpo

def montar_skin_db_cliente(class_name):
    """Catalogo filtrado pra classe do player (o client so' mostra o que pode usar)."""
    return {cat: [{"caminho": s["caminho"], "nome": s["nome"]} for s in lista
                  if s["classes"] is None or class_name in s["classes"]]
            for cat, lista in SKIN_DB.items()}

def skins_do_join(skins_client, skins_banco):
    # A skin que o client está usando (vem no join_game) é a que os outros
    # têm que ver; a do banco pode estar vazia/velha. Só aceita o formato
    # {categoria: {nome, caminho, cor}} com texto.
    if not isinstance(skins_client, dict) or not skins_client:
        return skins_banco
    limpo = {}
    for cat in ('base', 'body', 'helm', 'acc'):
        item = skins_client.get(cat)
        if not isinstance(item, dict): continue
        caminho = str(item.get('caminho', ''))[:200]
        if caminho and not caminho.startswith('res://'): caminho = ''
        limpo[cat] = {'nome': str(item.get('nome', ''))[:60], 'caminho': caminho, 'cor': str(item.get('cor', 'ffffffff'))[:40]}
    return limpo or skins_banco

@socketio.on('join_game')
def handle_join_game(data):
    conn = None
    try:
        sid = request.sid
        user_id = data.get('user_id')
        p_name = str(data.get('name', '')).strip()
        if not p_name or not user_id or user_id == -1: return
        conn = db_pool.getconn()
        c = conn.cursor()
        # Prova de login: sem isso, mandar o user_id + nome de outra pessoa
        # entrava no personagem dela.
        if not token_valido(c, user_id, data.get('token')):
            emit('force_disconnect', {"reason": "Session expired. Please log in again."}, room=sid)
            return
        # So' guarda o que o servidor mesmo vai usar (o resto do payload do
        # client nao entra no estado do player).
        data = {'user_id': user_id, 'name': p_name, 'skins': data.get('skins')}
        c.execute("SELECT class_name, level, exp, pos_x, pos_y, direction, skins, floor, inventory, equipped_items, skills, kills, current_hp, current_mp, currency, time_played, npc_dialogue_state, hotbar FROM characters WHERE user_id = %s AND name = %s AND deleted_at IS NULL", (user_id, p_name))
        row = c.fetchone()

        if not row: return

        real_skins = json.loads(row[6]) if row[6] else {}
        real_inventory = normalizar_inventario(json.loads(row[8]) if row[8] else [])
        real_equipped = normalizar_equipados(json.loads(row[9]) if row[9] else {})
        real_skills = json.loads(row[10]) if row[10] else {}
        if not isinstance(real_skills, dict): real_skills = {}
        real_skills.setdefault('fullness', FULLNESS_MAX)  # personagem novo/antigo começa cheio

        data['name'] = p_name; data['class_name'] = row[0]; data['level'] = row[1]; data['exp'] = row[2] if row[2] is not None else 0
        data['pos_x'] = row[3] if row[3] is not None else -1; data['pos_y'] = row[4] if row[4] is not None else -1
        data['pos_x'], data['pos_y'] = encaixar_no_tile(data['pos_x'], data['pos_y'])
        data['direction'] = row[5]; data['skins'] = validar_skins(skins_do_join(data.get('skins'), real_skins), row[0]); data['floor'] = row[7] if row[7] is not None else 1
        data['inventory'] = ordenar_favoritos_primeiro(real_inventory); data['equipped_items'] = real_equipped
        data['skills'] = real_skills; data['kills'] = row[11] if row[11] is not None else 0
        data['current_hp'] = row[12] if row[12] is not None else -1; data['current_mp'] = row[13] if row[13] is not None else -1
        data['currency'] = row[14] if row[14] is not None else 0
        # Base carregada do banco + session_start: _queue_save soma o tempo
        # decorrido dessa sessão em cima disso, sem gravar nada extra sozinho.
        data['time_played_base'] = row[15] if row[15] is not None else 0
        data['npc_dialogue_state'] = json.loads(row[16]) if row[16] else {}
        try: hb_salva = json.loads(row[17]) if row[17] else {}
        except (TypeError, ValueError): hb_salva = {}
        # Nunca configurou a barra: começa com o cookie no 1o slot de item.
        if not hb_salva: hb_salva = {'slots': [COOKIE]}
        data['hotbar'] = normalizar_hotbar(hb_salva)
        data['session_start'] = time.time()
        # Bestiary da conta. O que ficou salvo no personagem (versao antiga,
        # skills['bestiary']) entra na conta (maior valor de cada mob).
        c.execute("SELECT bestiary FROM users WHERE id = %s", (user_id,))
        linha_b = c.fetchone()
        try: bestiario = json.loads(linha_b[0]) if linha_b and linha_b[0] else {}
        except (TypeError, ValueError): bestiario = {}
        if not isinstance(bestiario, dict): bestiario = {}
        antigo = real_skills.pop('bestiary', None)
        if isinstance(antigo, dict):
            for tipo_b, k_b in antigo.items():
                try: bestiario[tipo_b] = max(int(bestiario.get(tipo_b, 0)), int(k_b))
                except (TypeError, ValueError): pass
        data['bestiary'] = bestiario
        
        # Uma sessao por conta: quem ja estava logado nela e' derrubado (o
        # client dele mostra "Someone logged in your account." e volta pro
        # menu) e quem acabou de entrar fica.
        # Corpo de OUTRO personagem da conta ainda em battle (deslogou no meio
        # da luta): não deixa entrar com outro char pra "sumir" com ele.
        for existing_sid, player in online_players.items():
            if (player.get('corpo_ausente') and str(player.get('user_id')) == str(user_id)
                    and player.get('name') != p_name):
                emit('force_disconnect', {"reason": f"{player.get('name')} is still in battle. Try again in a few seconds."}, room=sid)
                return
        for existing_sid, player in list(online_players.items()):
            if str(player.get('user_id')) == str(user_id) and existing_sid != sid:
                _remover_do_party(existing_sid, motivo="relogged")
                _limpar_convites_de_party_pendentes(existing_sid, player.get('name', ''))
                _remover_do_trade(existing_sid, motivo="relogged")
                _sair_de_todos_os_canais_chat(existing_sid)
                _queue_save(player)
                players_by_name.pop(player.get('name'), None)
                # Bestiary em memoria e' o mais novo (o save pode estar na fila),
                # mesmo vindo de outro personagem da conta.
                if isinstance(player.get('bestiary'), dict): data['bestiary'] = dict(player['bestiary'])
                data['current_hp'] = player.get('current_hp')
                data['current_mp'] = player.get('current_mp')
                if player.get('pos_x') == -1 and player.get('pos_y') == -1: data['pos_x'], data['pos_y'] = -1, -1
                # Mesmo personagem: o estado em memoria e' mais novo que o do
                # banco (o save acima ainda esta na fila), entao continua dele
                # em vez de voltar posicao/itens/xp pro ultimo save.
                if player.get('name') == p_name:
                    for chave in ('pos_x', 'pos_y', 'direction', 'floor', 'inventory', 'equipped_items',
                                  'skills', 'level', 'exp', 'kills', 'currency', 'npc_dialogue_state',
                                  'battle_until', 'hotbar'):
                        if chave in player: data[chave] = player[chave]
                    # Voltou pro corpo (ou derrubou a outra sessão) no meio do
                    # battle: os mobs que miravam o sid antigo passam pro novo.
                    for m in active_mobs.values():
                        if m.get('target_sid') == existing_sid: m['target_sid'] = sid

                emit('force_disconnect', {"reason": "Someone logged in your account."}, room=existing_sid)
                room_to_leave = player.get('room')
                if room_to_leave: leave_room(room_to_leave, sid=existing_sid)
                emit('player_left', {"name": player.get('name')}, broadcast=True, include_self=False)
                del online_players[existing_sid]
                _remover_da_visao(existing_sid)
                # Derruba um pouco depois: da' tempo do force_disconnect chegar
                # antes do socket fechar (senao o client mostraria "Server shutdown").
                def _derrubar(s=existing_sid):
                    socketio.sleep(1.0)
                    try: socketio.server.disconnect(s, namespace='/')
                    except Exception: pass
                socketio.start_background_task(_derrubar)
                
        room = get_chunk(data['pos_x'], data['pos_y'], data.get('floor', 1))
        join_room(room)
        
        data['room'] = room
        data['last_move_time'] = time.time()
        data['last_attack_time'] = 0
        data['last_skill_hit'] = {}
        data['is_dead'] = False
        data['_floor_broadcast'] = data.get('floor', 1)
        online_players[sid] = data
        players_by_name[p_name] = sid
        marcar_movimento(sid)  # aparece pra quem esta perto e ve quem esta perto
        if data.get('battle_until', 0) > time.time():
            marcar_batalha(sid, data)  # relogou ainda em battle: icone já aparece

        # item_db vai so' no payload (nao fica guardado em online_players).
        max_hp_join, max_mp_join = calcular_max_vitais(data)
        emit('sync_local_player', {**data, 'item_db': montar_item_db_cliente(),
                                   'max_hp': max_hp_join, 'max_mp': max_mp_join,
                                   'skin_db': montar_skin_db_cliente(data.get('class_name'))}, room=sid)
        emit('bestiary', montar_bestiario(data), room=sid)
        emit('quest_state', {'done': quests_feitas(data)}, room=sid)

        rooms_area = set(salas_vizinhas(room))
        dead_mobs = [m_id for m_id, m_data in active_mobs.items() if m_data.get('hp', 1) <= 0 and m_data.get('room') in rooms_area]
        if dead_mobs:
            agora = time.time()
            dead_ages = {m_id: max(0.0, agora - active_mobs[m_id].get('died_at', agora)) for m_id in dead_mobs}
            # Onde cada um morreu: sem isso o cadáver aparecia no spawn do mob.
            dead_pos = {m_id: [active_mobs[m_id].get('pos_x', 0), active_mobs[m_id].get('pos_y', 0)] for m_id in dead_mobs}
            emit('sync_mobs', {'dead_mobs': dead_mobs, 'dead_ages': dead_ages, 'dead_pos': dead_pos}, room=sid)

        stats_payload = _montar_payload_sync_stats(data)
        stats_payload['leveled_up'] = False
        stats_payload['char_leveled_up'] = False
        emit('sync_stats', stats_payload, room=sid)
        # current_hp/max_hp já resolvidos (-1 = cheio) pra barra do remote nascer certa.
        # So' o resumo publico (nome/classe/posicao/skin/HP). Antes ia o dict
        # inteiro de cada player (inventario, skills, user_id...): pesado com
        # muita gente online e vazava dado privado pros outros clients.
        emit('current_players', {o_sid: resumo_player_area(o_p) for o_sid, o_p in online_players.items()}, room=sid)
        emit('player_joined', resumo_player_area(data), broadcast=True, include_self=False)

        emit('sync_area_data', montar_sync_area(sid, room), room=sid)
        
    except Exception: traceback.print_exc()
    finally:
        if conn: db_pool.putconn(conn)

# ---- Chat local ----
CHAT_MAX_CARACTERES = 200          # igual o maxLength do campo no client (ChatUI)
CHAT_SPAM_MAX_MSGS = 3             # mais que isso dentro da janela = spam
CHAT_SPAM_JANELA_SEG = 4.0
# Cada aviso de spam muta por mais tempo: 10s, 20s, 30s... 70s (linear, pra
# 8 avisos ainda caberem em ~20 min; dobrando passaria disso so' de mute).
CHAT_SPAM_MUTE_BASE_SEG = 10
CHAT_SPAM_AVISOS_JANELA_SEG = 20 * 60   # avisos contam por 20 min
CHAT_SPAM_AVISOS_MAX = 8                # 8o aviso nessa janela = mute de 1h
CHAT_TOXICO_MAX = 100                   # palavroes em...
CHAT_TOXICO_JANELA_SEG = 30 * 60        # ...30 min = mute de 1h
CHAT_MUTE_LONGO_SEG = 60 * 60
CHAT_MUTES_ARQUIVO = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'chat_mutes.json')

# Censura "engracada": troca o palavrao por algo que ainda da' pra entender,
# em vez de ****. Frases (com espaco) sao trocadas por regex no texto cru;
# palavras soltas passam por _normalizar_palavra antes (pega n!gger, sh1t,
# f*ck, fuuuck...).
TROCAS_FRASES = [
    # Portugues
    ("puta que pariu", "pudim que caiu"), ("filho da puta", "filho da fada"),
    ("filha da puta", "filha da fada"), ("vai tomar no cu", "vai tomar no colo"),
    ("tomar no cu", "tomar no colo"), ("vai se foder", "vai ser feliz"),
    ("vai se fuder", "vai ser feliz"), ("vai a merda", "vai a meleca"),
    # Ingles
    ("son of a bitch", "son of a biscuit"), ("shut the fuck up", "shush please"),
    ("what the fuck", "what the fudge"), ("go fuck yourself", "go hug yourself"),
    ("kill yourself", "have a nice day"),
    # Espanhol
    ("hijo de puta", "hijo de hada"), ("hija de puta", "hija de hada"),
    ("concha de tu madre", "concha de mar"), ("me cago en", "me cachis en"),
    # Russo
    ("ёб твою мать", "ёлки-палки"), ("еб твою мать", "ёлки-палки"),
    ("yob tvoyu mat", "yolki-palki"),
]
TROCAS_PALAVRAS = {
    # ---- Ingles ----
    "nigger": "hard worker", "niggers": "hard workers", "nigga": "hard worker",
    "niggas": "hard workers", "nigg": "hard worker",
    "ass": "butt", "asses": "butts", "asshole": "butt nugget", "assholes": "butt nuggets",
    "dumbass": "dummy butt", "jackass": "donkey", "fatass": "chubby butt",
    "fuck": "fudge", "fucks": "fudges", "fucking": "fudging", "fuckin": "fudging",
    "fucked": "fudged", "fucker": "fudger", "fuckers": "fudgers", "fck": "fudge",
    "fk": "fudge", "fuk": "fudge", "fking": "fudging", "fcking": "fudging",
    "motherfucker": "mother hugger", "motherfuckers": "mother huggers", "mf": "my friend",
    "wtf": "what the fudge", "stfu": "shush please", "gtfo": "go away please",
    "kys": "have a nice day", "shit": "poop", "shits": "poops", "shitty": "poopy",
    "bullshit": "bull poop", "bs": "bull poop", "bitch": "sweetie", "bitches": "sweeties",
    "cunt": "cupcake", "dick": "pickle", "dicks": "pickles", "dickhead": "pickle head",
    "cock": "rooster", "pussy": "kitty", "bastard": "rascal", "whore": "princess",
    "slut": "princess", "hoe": "garden tool", "hoes": "garden tools", "thot": "sweet potato",
    "faggot": "fabulous person", "fag": "fabulous person", "retard": "silly goose",
    "retarded": "silly goose", "damn": "dang", "piss": "pee", "pissed": "peeved",
    "prick": "cactus", "twat": "teapot", "wanker": "banker", "bollocks": "bubbles",
    "douche": "shower gel", "douchebag": "shower gel", "spic": "pal", "chink": "pal",
    "kike": "pal", "crap": "crud",
    # ---- Portugues ----
    "porra": "poxa", "prr": "poxa", "pqp": "pudim que caiu", "caralho": "caramba",
    "caralhos": "carambas", "krl": "caramba", "crl": "caramba", "karalho": "caramba",
    "merda": "meleca", "merdas": "melecas", "puta": "fada", "putas": "fadas",
    "puto": "pato", "putos": "patos", "fdp": "fofo de pijama", "vsf": "vá ser feliz",
    "vsfd": "vá ser feliz demais", "tnc": "tomar no colo", "vtnc": "vai tomar no colo",
    "foda-se": "tá né", "fodase": "tá né", "fds": "fim de semana", "foda": "fofa", "foder": "fofar",
    "fuder": "fofar", "fudido": "fofinho", "fodido": "fofinho", "cu": "bumbum",
    "cuzao": "bumbunzão", "cuzão": "bumbunzão", "buceta": "borboleta",
    "boceta": "borboleta", "bct": "borboleta", "pau": "pão", "pinto": "pudim",
    "rola": "rosquinha", "piroca": "pipoca", "caceta": "caneta", "cacete": "cacilda",
    "arrombado": "abençoado", "arrombada": "abençoada", "viado": "colega",
    "veado": "colega", "bicha": "colega", "macaco": "capivara", "macaca": "capivara",
    "desgraçado": "abençoado", "desgracado": "abençoado", "otario": "otimista",
    "otário": "otimista", "babaca": "batata", "corno": "cornetinha",
    "vagabundo": "vagalume", "vagabunda": "vagalume", "vadia": "fada",
    "piranha": "sardinha", "imbecil": "inocente", "idiota": "ídolo", "retardado": "sonolento",
    # ---- Espanhol ----
    "mierda": "miércoles", "joder": "jolines", "coño": "coco", "cono": "coco",
    "pendejo": "panqueque", "pendeja": "panqueque", "cabrón": "campeón",
    "cabron": "campeon", "gilipollas": "piruleta", "hdp": "hijo de hada",
    "ctm": "cuídate mucho", "pinche": "pinchito", "verga": "verdura",
    "chinga": "chancla", "chingada": "chancla", "chingar": "chanclear",
    "culero": "cuchara", "maricón": "marinero", "maricon": "marinero",
    "carajo": "caramba", "cojones": "cojines", "malparido": "bien parido",
    "pelotudo": "peludito", "boludo": "bombón", "mamón": "melón", "mamon": "melon",
    "zorra": "zorrita", "perra": "perrita", "imbécil": "inocente",
    # ---- Russo (cirilico + transliterado) ----
    "блять": "блин", "блядь": "блин", "бля": "блин", "сука": "сушка", "суки": "сушки",
    "сучка": "сушка", "хуй": "хобот", "хуйня": "фигня", "нахуй": "на хутор",
    "пиздец": "капец", "пизда": "пицца", "ебать": "ёлки", "ебаный": "ёлочный",
    "мудак": "чудак", "гандон": "гондола", "долбоёб": "дятел", "долбоеб": "дятел",
    "пидор": "помидор", "пидорас": "помидор", "сволочь": "солнышко",
    "blyat": "blin", "blyad": "blin", "blya": "blin", "suka": "sushka", "cyka": "sushka",
    "pizdec": "kapec", "pizdets": "kapec", "pizda": "pizza", "nahui": "na hutor",
    "nahuy": "na hutor", "huy": "hobot", "hui": "hobot", "mudak": "chudak",
    "pidor": "pomidor", "gandon": "gondola", "debil": "dobryak",
}
_RE_FRASES = re.compile(
    r"(?<!\w)(" + "|".join(re.escape(f) for f, _ in TROCAS_FRASES) + r")(?!\w)", re.IGNORECASE)
_MAPA_FRASES = {f: t for f, t in TROCAS_FRASES}
# Palavra = letras/numeros + simbolos usados pra disfarcar (n!gger, a$$, f*ck),
# podendo ter hifen no meio (foda-se).
_RE_PALAVRA = re.compile(r"[\w@$*!]+(?:-[\w@$*!]+)*")
_LEET = str.maketrans({'0': 'o', '1': 'i', '!': 'i', '3': 'e', '4': 'a', '@': 'a',
                       '$': 's', '5': 's', '7': 't'})

def _manter_caixa(original, troca):
    letras = [c for c in original if c.isalpha()]
    # PALAVRAO -> TROCA, Palavrao -> Troca.
    if len(letras) > 1 and all(c.isupper() for c in letras): return troca.upper()
    if letras and letras[0].isupper(): return troca[0].upper() + troca[1:]
    return troca

def _troca_da_palavra(nucleo):
    base = nucleo.lower().translate(_LEET)
    # Repeticao de letra pra fugir do filtro: "fuuuuck", "shiiit".
    candidatos = [base, re.sub(r"(.)\1+", r"\1\1", base), re.sub(r"(.)\1+", r"\1", base)]
    for c in candidatos:
        if c in TROCAS_PALAVRAS: return TROCAS_PALAVRAS[c]
    # f*ck, sh*t: '*' vale qualquer letra (exige 2+ letras de verdade, senao
    # "****" casaria com qualquer palavrao de 4 letras).
    if '*' in base and sum(ch.isalpha() for ch in base) >= 2:
        padrao = re.compile("^" + re.escape(base).replace(r"\*", ".") + "$")
        for palavra, troca in TROCAS_PALAVRAS.items():
            if padrao.match(palavra): return troca
    return None

def censurar(msg):
    """Devolve (texto censurado, quantos palavroes foram trocados)."""
    total = 0
    def trocar_frase(m):
        nonlocal total
        total += 1
        return _manter_caixa(m.group(0), _MAPA_FRASES[m.group(0).lower()])
    msg = _RE_FRASES.sub(trocar_frase, msg)

    def trocar_palavra(m):
        nonlocal total
        token = m.group(0)
        # "!" nas pontas e' pontuacao ("shit!"), so' no meio vira "i" (n!gger).
        nucleo = token.strip('!')
        if not nucleo: return token
        troca = _troca_da_palavra(nucleo)
        if troca is None: return token
        total += 1
        ini = token[:len(token) - len(token.lstrip('!'))]
        fim = token[len(token.rstrip('!')):]
        return ini + _manter_caixa(nucleo, troca) + fim
    return _RE_PALAVRA.sub(trocar_palavra, msg), total

# Mutes por NOME do player (sobrevive a relogar) e salvos em disco (sobrevive
# a reiniciar o servidor): nome -> timestamp de quando acaba.
chat_mutes = {}
# Historico por nome (memoria): avisos de spam e palavroes recentes.
chat_historico = {}

def carregar_mutes_chat():
    global chat_mutes
    try:
        with open(CHAT_MUTES_ARQUIVO, 'r') as f:
            agora = time.time()
            chat_mutes = {n: float(t) for n, t in json.load(f).items() if float(t) > agora}
    except FileNotFoundError:
        pass
    except Exception:
        traceback.print_exc()

def salvar_mutes_chat():
    try:
        agora = time.time()
        with open(CHAT_MUTES_ARQUIVO, 'w') as f:
            json.dump({n: t for n, t in chat_mutes.items() if t > agora}, f)
    except Exception:
        traceback.print_exc()

carregar_mutes_chat()

def chat_sistema(sid, texto, cor='yellow'):
    socketio.emit('chat_system', {'text': texto, 'color': cor}, room=sid)

# ---------------------------------------------------------------------------
# ADMIN. Decidido SO' no servidor pelo nome do personagem logado (que vem do
# banco, depois do token conferido) - o client nao manda "sou admin", entao
# nao da' pra burlar. Comandos vao pelo chat Local comecando com "/".
# ---------------------------------------------------------------------------
ADMIN_NOMES = {"labubus"}

def eh_admin(p):
    return str(p.get('name', '')).lower() in ADMIN_NOMES

def _avisar_admin_bloqueio(sid, p, motivo, tile):
    # So' pro admin: por que o SERVIDOR recusou o passo (o SQM no formato do
    # minimapa: X igual, Y de cima pra baixo = linha do Tiled).
    if not eh_admin(p): return
    agora = time.time()
    if agora - p.get('_ultimo_aviso_bloqueio', 0) < 1.0: return
    p['_ultimo_aviso_bloqueio'] = agora
    fonte = "World.tmx do servidor" if MAPA_ID_SERVIDOR in MAPAS_DO_SERVIDOR else "mapa enviado por um client (mapas_colisao.json)"
    chat_sistema(sid, f"[debug] servidor bloqueou: {motivo} x{tile[0]}, y{tile[1]} ({fonte})", 'red')

# /bestiary <mob> <0-3>: 0 = so' descoberto, 1 = cobre, 2 = prata, 3 = ouro.
ADMIN_BESTIARIO_KILLS = {0: 1, 1: 20, 2: 100, 3: 500}

def _todas_as_quests():
    # Quests dos NPCs (NPC_DB "quest") + as das areas do mapa (QuestAreas).
    nomes = {str(i['quest']).lower() for i in NPC_DB.values() if i.get('quest')}
    for areas in areas_de_quest.values():
        for a in areas: nomes.add(a['quest'])
    return sorted(nomes)

def _tipo_do_mob_pelo_nome(nome):
    nome = nome.lower().strip()
    for tipo, info in MOB_DB.items():
        if nome == tipo.lower() or nome == str(info.get('name', '')).lower():
            return tipo
    return None

def comando_admin(sid, p, texto):
    partes = texto[1:].split()
    if not partes: return
    cmd = partes[0].lower()
    if cmd in ('help', 'admin'):
        chat_sistema(sid, "Admin: /bestiary <mob> <0-3|clear>  (0 discovered, 1 copper, 2 silver, 3 gold)")
        chat_sistema(sid, "Admin: /quest <name> complete|reset   /quests   /mobs")
        chat_sistema(sid, "Admin: /tp x<X> y<Y>  (minimap coordinates, ex: /tp x56 y200)")
        return
    if cmd == 'tp':
        # /tp x56 y200 (ou /tp 56 200): mesmo X/Y do minimapa.
        try:
            nums = [int(re.sub(r'^[xXyY]', '', v).strip(',')) for v in partes[1:3]]
            if len(nums) != 2: raise ValueError
        except ValueError:
            chat_sistema(sid, "Usage: /tp x<X> y<Y>  (ex: /tp x56 y200)", 'red')
            return
        if p.get('is_dead'):
            chat_sistema(sid, "You are dead.", 'red')
            return
        dest = _teleportar(sid, p, (nums[0], nums[1]), p.get('direction', 'down'))
        chat_sistema(sid, f"Teleported to x{dest[0]}, y{dest[1]}.")
        return
    if cmd == 'mobs':
        chat_sistema(sid, "Mobs: " + (", ".join(f"{t} ({i.get('name', t.capitalize())})" for t, i in MOB_DB.items()) or "none"))
        return
    if cmd == 'quests':
        feitas = set(quests_feitas(p))
        nomes = _todas_as_quests()
        chat_sistema(sid, "Quests: " + (", ".join(f"{q} [{'done' if q in feitas else 'not done'}]" for q in nomes) or "none"))
        return
    if cmd == 'quest':
        if len(partes) < 3 or partes[-1].lower() not in ('complete', 'reset'):
            chat_sistema(sid, "Usage: /quest <name> complete|reset", 'red')
            return
        quest = ' '.join(partes[1:-1]).lower()
        if quest not in _todas_as_quests():
            chat_sistema(sid, "Unknown quest. Quests: " + ", ".join(_todas_as_quests()), 'red')
            return
        if not isinstance(p.get('npc_dialogue_state'), dict): p['npc_dialogue_state'] = {}
        if partes[-1].lower() == 'complete': p['npc_dialogue_state']['quest:' + quest] = True
        else: p['npc_dialogue_state'].pop('quest:' + quest, None)
        _queue_save(p)
        socketio.emit('quest_state', {'done': quests_feitas(p)}, room=sid)
        chat_sistema(sid, f"Quest {quest}: {partes[-1].lower()}.")
        return
    if cmd == 'bestiary':
        if len(partes) < 3:
            chat_sistema(sid, "Usage: /bestiary <mob> <0-3|clear>")
            return
        tipo = _tipo_do_mob_pelo_nome(' '.join(partes[1:-1]))
        if tipo is None:
            chat_sistema(sid, "Unknown mob. Mobs: " + ", ".join(MOB_DB.keys()), 'red')
            return
        b = p.get('bestiary')
        if not isinstance(b, dict):
            b = {}
            p['bestiary'] = b
        valor = partes[-1].lower()
        if valor == 'clear':
            b.pop(tipo, None)
        else:
            try: nivel = int(valor)
            except ValueError: nivel = -1
            if nivel not in ADMIN_BESTIARIO_KILLS:
                chat_sistema(sid, "Level must be 0, 1, 2, 3 or clear.", 'red')
                return
            b[tipo] = ADMIN_BESTIARIO_KILLS[nivel]
        _queue_save(p)
        socketio.emit('bestiary', montar_bestiario(p), room=sid)
        chat_sistema(sid, f"Bestiary: {tipo} set to {valor}.")
        return
    chat_sistema(sid, f"Unknown command /{cmd}. Try /help.", 'red')

def _formatar_tempo(seg):
    seg = int(math.ceil(seg))
    if seg >= 3600: return "1 hour" if seg <= 3600 else f"{math.ceil(seg / 3600)} hours"
    if seg >= 120:
        m = math.ceil(seg / 60)
        return f"{m} minute" + ("" if m == 1 else "s")
    return f"{seg} second" + ("" if seg == 1 else "s")

def _mutar(nome, seg):
    chat_mutes[nome] = max(chat_mutes.get(nome, 0), time.time() + seg)
    salvar_mutes_chat()

def _filtrar_mensagem_chat(sid, p, texto):
    """Regras comuns do chat (Local e idiomas): limite de caracteres, mute,
    anti-spam e censura. Devolve a mensagem pronta pra repassar, ou None se
    ela foi barrada (o aviso pro jogador ja' foi mandado aqui)."""
    nome = p.get('name', '')
    msg = str(texto).strip()[:CHAT_MAX_CARACTERES]
    if not msg: return None
    agora = time.time()
    hist = chat_historico.setdefault(nome, {'msgs': [], 'avisos': [], 'palavroes': []})

    # Mutado: nao conta como spam de novo, so' lembra quanto falta.
    fim_mute = chat_mutes.get(nome, 0)
    if fim_mute > agora:
        restante = fim_mute - agora
        cor = 'red'  # qualquer mute e' punicao: vermelho vivo no client
        chat_sistema(sid, f"You are muted. Wait {_formatar_tempo(restante)} to talk again.", cor)
        return None

    # Anti-spam: mais de CHAT_SPAM_MAX_MSGS na janela -> descarta, avisa e
    # muta por um tempo que cresce a cada aviso (nos ultimos 20 min). No 8o
    # aviso, mute de 1 hora. Conta Local e idiomas juntos.
    hist['msgs'] = [t for t in hist['msgs'] if agora - t < CHAT_SPAM_JANELA_SEG]
    if len(hist['msgs']) >= CHAT_SPAM_MAX_MSGS:
        hist['msgs'] = []
        hist['avisos'] = [t for t in hist['avisos'] if agora - t < CHAT_SPAM_AVISOS_JANELA_SEG]
        hist['avisos'].append(agora)
        n = len(hist['avisos'])
        if n >= CHAT_SPAM_AVISOS_MAX:
            hist['avisos'] = []
            _mutar(nome, CHAT_MUTE_LONGO_SEG)
            chat_sistema(sid, "You have been muted for 1 hour for spam.", 'red')
        else:
            seg = CHAT_SPAM_MUTE_BASE_SEG * n
            _mutar(nome, seg)
            chat_sistema(sid, f"You are sending messages too fast. You are muted for {_formatar_tempo(seg)}.", 'red')
        return None
    hist['msgs'].append(agora)

    msg, palavroes = censurar(msg)
    if palavroes:
        hist['palavroes'] = [t for t in hist['palavroes'] if agora - t < CHAT_TOXICO_JANELA_SEG]
        hist['palavroes'].extend([agora] * palavroes)
        if len(hist['palavroes']) >= CHAT_TOXICO_MAX:
            hist['palavroes'] = []
            _mutar(nome, CHAT_MUTE_LONGO_SEG)
            chat_sistema(sid, "You have been muted for 1 hour for toxic behavior.", 'red')
            return None
    return msg

@socketio.on('c')
def handle_c(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        if not isinstance(data, list) or len(data) == 0: return
        # Comando de admin ("/..."): so' de personagem admin, nunca vai pro chat.
        # Pra quem nao e' admin, "/algo" e' so' uma mensagem normal.
        if eh_admin(p) and str(data[0]).strip().startswith('/'):
            comando_admin(sid, p, str(data[0]).strip()[:200])
            return
        msg = _filtrar_mensagem_chat(sid, p, data[0])
        if msg is None: return

        room = p.get('room')
        payload = [p.get('name', ''), msg, p.get('class_name', 'Knight')]
        # Mesma área do movimento ('m'): o chunk do player + os 8 vizinhos.
        # Antes ia só pro chunk (800px) exato dele: quem estava do lado, mas
        # do outro lado da borda do chunk, via o player andar e nunca recebia
        # a mensagem nem o balão.
        # Inclui quem mandou: ele recebe de volta a versao ja censurada.
        # Vai pra quem tem o player no raio da tela (mesmo raio do movimento,
        # ver _flush_visao) - igual a lista de jogadores do chat Local.
        for destino in _visto_por.get(sid, set()) | {sid}:
            socketio.emit('c', payload, room=destino)
    except Exception: traceback.print_exc()

# Mensagem privada (botao de chat da janela do jogador): so' quem mandou e
# quem recebe. Mesmas regras do chat (limite, mute, anti-spam, censura).
@socketio.on('pm')
def handle_pm(data):
    try:
        sid = request.sid
        if sid not in online_players or not isinstance(data, dict): return
        p = online_players[sid]
        destino_nome = str(data.get('to', '')).strip()[:32]
        destino_sid = players_by_name.get(destino_nome)
        if not destino_nome or destino_sid == sid: return
        if not destino_sid or destino_sid not in online_players:
            chat_sistema(sid, "This player is offline.")
            return
        msg = _filtrar_mensagem_chat(sid, p, data.get('msg', ''))
        if msg is None: return
        payload = {'from': p.get('name', ''), 'to': destino_nome, 'msg': msg,
                   'class': p.get('class_name', 'Knight'),
                   'to_class': online_players[destino_sid].get('class_name', 'Knight')}
        socketio.emit('pm', payload, room=destino_sid)
        socketio.emit('pm', payload, room=sid)
    except Exception: traceback.print_exc()

# Chat da party (aba Party): vai pra todos os membros da party, em qualquer
# lugar do mapa. Quem estiver perto ve o balao de fala (amarelo) - isso e'
# decidido no client (so' desenha se o player estiver na tela dele). Nao
# aparece no chat Local de ninguem.
@socketio.on('pc')
def handle_pc(data):
    try:
        sid = request.sid
        if sid not in online_players or not isinstance(data, dict): return
        party, _pid = _get_party(sid)
        if not party:
            chat_sistema(sid, "You are not in a party.")
            return
        p = online_players[sid]
        msg = _filtrar_mensagem_chat(sid, p, data.get('msg', ''))
        if msg is None: return
        payload = {'name': p.get('name', ''), 'msg': msg, 'class': p.get('class_name', 'Knight')}
        for membro in list(party['members']):
            if membro in online_players:
                socketio.emit('pc', payload, room=membro)
    except Exception: traceback.print_exc()

# Chat de idioma (Portuguese/Spanish/...): vai pra todo mundo que tem o canal
# aberto, em qualquer lugar do mapa (igual grupo). So' quem esta no canal fala nele.
@socketio.on('cc')
def handle_cc(data):
    try:
        sid = request.sid
        if sid not in online_players or not isinstance(data, dict): return
        canal = data.get('channel')
        if canal not in chat_channels or sid not in chat_channels[canal]: return
        p = online_players[sid]
        msg = _filtrar_mensagem_chat(sid, p, data.get('msg', ''))
        if msg is None: return
        payload = {'channel': canal, 'name': p.get('name', ''), 'msg': msg,
                   'class': p.get('class_name', 'Knight')}
        for membro in list(chat_channels[canal]):
            socketio.emit('cc', payload, room=membro)
    except Exception: traceback.print_exc()

def _emit_membros_canal(canal):
    membros = chat_channels.get(canal, set())
    nomes = sorted(online_players[s].get('name', '') for s in membros if s in online_players)
    for s in membros:
        socketio.emit('chat_members', {'channel': canal, 'names': nomes}, room=s)

def _sair_de_todos_os_canais_chat(sid):
    for canal, membros in chat_channels.items():
        if sid in membros:
            membros.discard(sid)
            _emit_membros_canal(canal)

@socketio.on('chat_join')
def handle_chat_join(data):
    try:
        sid = request.sid
        if sid not in online_players or not isinstance(data, dict): return
        canal = data.get('channel')
        if canal not in chat_channels: return
        chat_channels[canal].add(sid)
        _emit_membros_canal(canal)
    except Exception: traceback.print_exc()

@socketio.on('chat_leave')
def handle_chat_leave(data):
    try:
        sid = request.sid
        if not isinstance(data, dict): return
        canal = data.get('channel')
        if canal not in chat_channels or sid not in chat_channels[canal]: return
        chat_channels[canal].discard(sid)
        _emit_membros_canal(canal)
    except Exception: traceback.print_exc()

@socketio.on('update_skins')
def handle_update_skins(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        
        # Valida contra SKIN_DB + classe (antes salvava/repassava qualquer coisa).
        skins = validar_skins(data.get('skins', {}) if isinstance(data, dict) else {}, p.get('class_name'))
        p['skins'] = skins
        _queue_save(p)
        # Confirma pro proprio player o que ficou valendo de verdade.
        emit('skins_synced', {"skins": skins}, room=sid)

        room = p.get('room')
        if room:
            emit_area('player_skins_updated', {"name": p.get('name'), "skins": skins}, room, skip_sid=sid)
    except Exception: 
        import traceback
        traceback.print_exc()   

@socketio.on('npc_pay')
def handle_npc_pay(data):
    # Botao "Pay" do dialogo: cobra o preco do NPC e libera a quest dele.
    try:
        sid = request.sid
        p = online_players.get(sid)
        if not p or not isinstance(data, dict): return
        npc_id = str(data.get('npc_id', '')).strip().lower()[:64]
        info = NPC_DB.get(npc_id)
        if not info or 'price' not in info: return
        # Tem que estar perto de um NPC desse tipo.
        tp = tile_de(p.get('pos_x', 0), p.get('pos_y', 0))
        perto = any(n.get('npc_id', '').lower() == npc_id and
                    max(abs(tile_de(n.get('pos_x', 0), n.get('pos_y', 0))[0] - tp[0]),
                        abs(tile_de(n.get('pos_x', 0), n.get('pos_y', 0))[1] - tp[1])) <= NPC_PAY_DISTANCIA_SQM
                    for n in active_npcs.values())
        if not perto: return
        quest = info.get('quest', npc_id)
        if quest in quests_feitas(p):
            emit('npc_pay_result', {'npc_id': npc_id, 'ok': True, 'text': NPC_TEXTO_PAGO,
                                    'currency': int(p.get('currency', 0))}, room=sid)
            return
        preco = int(info['price'])
        if int(p.get('currency', 0)) < preco:
            emit('npc_pay_result', {'npc_id': npc_id, 'ok': False, 'text': NPC_TEXTO_SEM_DINHEIRO}, room=sid)
            return
        p['currency'] = int(p.get('currency', 0)) - preco
        if not isinstance(p.get('npc_dialogue_state'), dict): p['npc_dialogue_state'] = {}
        p['npc_dialogue_state']['quest:' + quest] = True
        _queue_save(p)
        emit('npc_pay_result', {'npc_id': npc_id, 'ok': True, 'text': NPC_TEXTO_PAGO,
                                'currency': int(p['currency'])}, room=sid)
        emit('quest_state', {'done': quests_feitas(p)}, room=sid)
    except Exception:
        traceback.print_exc()

@socketio.on('npc_dialogue_seen')
def handle_npc_dialogue_seen(data):
    # Cliente avisa quando o jogador terminou de ler TODAS as paginas do
    # dialogo de 1a vez de um NPC - persistido pra nao repetir a fala
    # completa (nem o "pedido"/quest dela) numa proxima conversa, mesmo
    # depois de relogar (a pedido do usuario).
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]

        npc_id = str(data.get('npc_id', '') if isinstance(data, dict) else '').strip()[:64]
        if not npc_id: return
        # So' NPCs que existem (senao dava pra encher o banco com ids falsos).
        if npc_id.lower() not in NPC_DB: return

        if not isinstance(p.get('npc_dialogue_state'), dict):
            p['npc_dialogue_state'] = {}
        p['npc_dialogue_state'][npc_id] = True
        _queue_save(p)
    except Exception:
        traceback.print_exc()

# Troca de lugar: quem insiste em andar contra um player PARADO (afk num
# corredor estreito, por exemplo) troca de SQM com ele. Regras: os dois vivos,
# mesmo andar, colados (SQM do lado, sem diagonal), o alvo parado ha' pelo
# menos SWAP_ALVO_PARADO_SEG (nao da' pra empurrar quem esta andando) e no
# maximo 1 troca a cada SWAP_COOLDOWN_SEG por player.
SWAP_ALVO_PARADO_SEG = 2.0
SWAP_COOLDOWN_SEG = 1.5

@socketio.on('swap_req')
def handle_swap_req(data):
    try:
        sid = request.sid
        if sid not in online_players or not isinstance(data, list) or len(data) < 2: return
        p = online_players[sid]
        target_name = str(data[0])
        d_int = int(data[1])
        if d_int not in DIR_MAP: return

        now = time.time()
        if now - p.get('_ultimo_swap', 0) < SWAP_COOLDOWN_SEG: return
        target_sid = players_by_name.get(target_name)
        if not target_sid or target_sid == sid or target_sid not in online_players: return
        t_p = online_players[target_sid]
        if p.get('is_dead') or t_p.get('is_dead'): return
        if int(p.get('floor', 1) or 1) != int(t_p.get('floor', 1) or 1): return
        if now - t_p.get('last_move_time', 0) < SWAP_ALVO_PARADO_SEG: return

        tp = tile_de(p.get('pos_x', 0), p.get('pos_y', 0))
        tt = tile_de(t_p.get('pos_x', 0), t_p.get('pos_y', 0))
        # Do lado ou na diagonal (diagonal: sem cerca fina fechando os 2 caminhos).
        if max(abs(tp[0] - tt[0]), abs(tp[1] - tt[1])) != 1: return
        # Area de quest (ponte): ninguem entra nela trocando de lugar com quem
        # ja' fez a quest - cada um so' vai pro SQM do outro se puder pisar la'.
        if bloqueado_por_quest(p, tt) or bloqueado_por_quest(t_p, tp): return
        grade_s = mapas_colisao.get(p.get('mapa'))
        if grade_s is not None and int(p.get('floor', 1) or 1) == 1:
            if abs(tp[0] - tt[0]) + abs(tp[1] - tt[1]) == 1 and borda_bloqueada(grade_s, tp, tt): return
            if diagonal_bloqueada(grade_s, tp, tt): return
        p['_ultimo_swap'] = now

        px, py = centro_tile(tp)
        tx, ty = centro_tile(tt)
        opostos = {0: 1, 1: 0, 2: 3, 3: 2}
        t_dint = opostos[d_int]
        p['pos_x'], p['pos_y'], p['direction'] = tx, ty, DIR_MAP[d_int]
        t_p['pos_x'], t_p['pos_y'], t_p['direction'] = px, py, DIR_MAP[t_dint]
        # Conta como movimento dos dois (o balde de passos e o "parado ha'").
        p['last_move_time'] = now
        t_p['last_move_time'] = now

        payload = [p['name'], tx, ty, d_int, t_p['name'], px, py, t_dint]
        salas = set()
        for quem_sid, quem, (nx, ny) in ((sid, p, (tx, ty)), (target_sid, t_p, (px, py))):
            antiga = quem.get('room')
            nova = get_chunk(nx, ny, quem.get('floor', 1))
            if antiga: salas |= set(salas_vizinhas(antiga))
            salas |= set(salas_vizinhas(nova))
            if antiga != nova:
                if antiga: leave_room(antiga, sid=quem_sid)
                join_room(nova, sid=quem_sid)
                quem['room'] = nova
        for r in salas:
            socketio.emit('swap_exec', payload, room=r)
        # Com passo: quem ja' via os dois so' confirma o passo (o swap_exec ja'
        # animou); quem passa a ver recebe a posicao nova.
        marcar_movimento(sid, (tx, ty, d_int))
        marcar_movimento(target_sid, (px, py, t_dint))
    except Exception: traceback.print_exc()

def _calcular_xp_por_participante(mob_data, xp_total):
    # Substitui o cálculo antigo (proporcional ao dano de cada um, sem noção de
    # party) por um que agrupa os sids do dmg_tracker por party_id. Atacantes
    # solo (sem party, ou com party_id que já não existe mais) seguem exatos
    # a fórmula original, sem mudar em nada. Grupos de party somam o % de dano
    # de quem bateu, aplicam o bonus de classe da party (calculado com o roster
    # atual, não só quem bateu) e dividem o resultado igualmente só entre quem
    # bateu - quem não bateu nada não entra no dict, ou seja, não recebe XP.
    max_hp = float(mob_data.get('max_hp', 40))
    tracker = mob_data['dmg_tracker']

    grupos = {}
    for p_sid in tracker:
        if p_sid not in online_players:
            continue
        pid = online_players[p_sid].get('party_id')
        if pid and pid in parties:
            grupos.setdefault(pid, []).append(p_sid)
        else:
            grupos[("solo", p_sid)] = [p_sid]

    resultado = {}
    for chave, hitters in grupos.items():
        # Grupo solo, OU um membro de party que bateu nesse mob sozinho (só
        # ele da party estava online e batendo aqui): fórmula proporcional
        # normal, sem bonus nenhum. Sem essa checagem de len(hitters) >= 2,
        # bastaria encher a party de "alts" parados pra um único player ativo
        # farmar sozinho com o bonus de classe inteiro - o bonus só existe
        # quando 2+ membros da MESMA party estão online e batendo NESSE mob.
        if isinstance(chave, tuple) or len(hitters) < 2:
            for s_sid in hitters:
                pct_dano = tracker[s_sid] / max_hp
                resultado[s_sid] = max(1, int(xp_total * pct_dano))
            continue

        party = parties[chave]
        pct_combinado = sum(tracker[h] / max_hp for h in hitters)
        bonus_percent = _calcular_bonus_percentual(party)
        pool = xp_total * pct_combinado * (1.0 + bonus_percent / 100.0)
        share = max(1, int(pool / len(hitters)))
        for h in hitters:
            resultado[h] = share

    return resultado

@socketio.on('hit_mob')
def handle_hit_mob(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        
        p = online_players[sid]
        mob_id = str(data.get('mob_id', ''))
        mob_type_id = str(data.get('mob_type_id', '')).lower()
        hit_type = str(data.get('hit_type', 'physical_hit'))
        
        # hit_type pode ser um nome de animação ou o caminho de um recurso
        # .tres (o ItemData da arma ou um SpriteFrames) com o efeito de hit.
        if hit_type.startswith("res://") and not (hit_type.endswith((".tres", ".res")) and ".." not in hit_type and "::" not in hit_type):
            hit_type = 'physical_hit'
        if not mob_id or not mob_type_id: return
        
        w_type = 'Ranged' if data.get('w_type') == 'Ranged' else 'Melee'
        proj = str(data.get('proj', ''))
        if not (proj.startswith('res://') and proj.endswith(('.tres', '.res', '.png')) and '..' not in proj and '::' not in proj): proj = ''

        if p.get('is_dead'): return
        # Protection Zone e' 100% amigavel: dali de dentro ninguem ataca.
        if na_pz(p): return
        now = time.time()
        # Pequena folga (0.1s) pra latencia: o client manda a cada ~2.4s.
        if now - p.get('last_attack_time', 0) < ATAQUE_COOLDOWN_SEG - 0.1: return
        p['last_attack_time'] = now
        
        precisa_municao = p.get('class_name') == "Ranger"
        if precisa_municao and not tem_municao_equipada(p):
            emit('inventory_synced', {'inventory': ordenar_favoritos_primeiro(p.get('inventory', [])), 'equipped_items': p.get('equipped_items', {})}, room=sid)
            return
        
        # So' mobs que o servidor ja' conhece. Antes um id inventado criava um
        # mob novo (de qualquer tipo) do lado do player: XP/loot de graca.
        mob_data = active_mobs.get(mob_id)
        if mob_data is None or mob_data.get('hp', 1) <= 0: return
        if na_pz(mob_data): return  # mob parado dentro da PZ tambem nao apanha
        # Alcance: corpo a corpo so' do SQM do lado (diagonal vale); a
        # distancia ate ALCANCE_RANGED_SQM em linha reta (igual a deteccao do
        # mob). +1 SQM de folga pro passo em andamento. Mob sem posicao
        # conhecida nao pode ser validado - nega o golpe em vez de liberar
        # de qualquer distancia.
        if 'pos_x' not in mob_data: return
        if p.get('class_name') in CLASSES_RANGED:
            if _dist_px(mob_data, p) > (ALCANCE_RANGED_SQM + 1) * TILE: return
            # Parede no meio: a flecha/magia nao passa.
            if int(p.get('floor', 1) or 1) == 1 and not linha_de_visao(
                    mapas_colisao.get(p.get('mapa')), tile_de(p.get('pos_x', 0), p.get('pos_y', 0)), tile_do_mob(mob_data)):
                return
        else:
            tp = tile_de(p.get('pos_x', 0), p.get('pos_y', 0))
            tm = tile_do_mob(mob_data)
            if max(abs(tp[0] - tm[0]), abs(tp[1] - tm[1])) > 2: return
        # Mesma regra do mob pro player: andar diferente, sem golpe (nem aggro).
        if int(p.get('floor', 1) or 1) != MOB_FLOOR: return
        # Arma que gasta mana (varinha): sem mana suficiente, não ataca. Só
        # cobra depois de validar alcance/andar (golpe recusado não gasta).
        custo_mana = custo_mana_arma(p)
        if custo_mana > 0:
            max_hp_m, max_mp_m = calcular_max_vitais(p)
            try: mp_atual = float(p.get('current_mp', -1))
            except (TypeError, ValueError): mp_atual = -1.0
            if mp_atual < 0: mp_atual = max_mp_m
            if mp_atual < custo_mana:
                emit('sync_vitals', {'current_mp': mp_atual, 'max_mp': max_mp_m, 'no_mana': True}, room=sid)
                return
            p['current_mp'] = mp_atual - custo_mana
            emit('sync_vitals', {'current_mp': p['current_mp'], 'max_mp': max_mp_m}, room=sid)
        mob_data['last_activity'] = now
        p['last_successful_hit'] = now  # libera o treino da skill da classe
        marcar_batalha(sid, p, now)
        mob_focar_agressor(mob_id, mob_data, sid)
        # Ranged colado (SQM do lado) num mob que continua focando OUTRO
        # player (ex: lure do knight): metade do dano e número amarelo. Se o
        # mob é dele (ou acabou de virar, em mob_focar_agressor), sem corte.
        tp_r = tile_de(p.get('pos_x', 0), p.get('pos_y', 0))
        reduzido = (p.get('class_name') in CLASSES_RANGED and _adjacente(tile_do_mob(mob_data), tp_r)
                    and mob_data.get('target_sid') != sid)

        skills = p.get('skills', {})
        p_class = p.get('class_name', 'Knight')
        if len(_slots_com_classe_invalida(p)) > 0:
            base_dmg = PUNICAO_CLASSE_DANO
        else:
            bonus_itens = somar_bonus_combate_equipados(p.get('equipped_items', {}))
            base_dmg, _ = calc_player_stats(p_class, p.get('level', 1), skills, bonus_dmg=bonus_itens['bonus_damage'])
        if esta_com_fome(p): base_dmg = max(1, int(base_dmg * FOME_MULT_DANO))
        
        min_dano = max(1, int(base_dmg * 0.85))
        max_dano = max(min_dano, int(base_dmg * 1.15))
        dano_final = random.randint(min_dano, max_dano) if max_dano > min_dano else base_dmg
        
        is_crit = random.random() < CRIT_CHANCE
        if is_crit: dano_final *= 2
        if reduzido: dano_final = max(1, int(dano_final * DANO_RANGED_COLADO_MULT))
        # Medalha de ouro no bestiary desse mob: +5% de dano (arredonda no sorteio).
        if bestiario_ouro(p, mob_data.get('type_id', mob_type_id)):
            extra = dano_final * BESTIARIO_BONUS_OURO
            dano_final += int(extra) + (1 if random.random() < extra - int(extra) else 0)
        # Miss: o golpe sai (gasta mana/flecha, puxa aggro), mas não tira vida.
        is_miss = random.random() < MISS_CHANCE_PLAYER
        if is_miss: dano_final, is_crit, reduzido = 0, False, False
        
        if precisa_municao:
            restante_municao, esgotou_municao = consumir_municao(p)
            if esgotou_municao:
                _queue_save(p)
                emit('inventory_synced', {'inventory': ordenar_favoritos_primeiro(p.get('inventory', [])), 'equipped_items': p.get('equipped_items', {})}, room=sid)
                emit('sync_stats', _montar_payload_sync_stats(p), room=sid)
            else:
                emit('sync_vitals', {'ammo_qty': restante_municao, 'cap_atual': calcular_cap_usado(p), 'cap_maximo': calcular_cap_maximo(p.get('level', 1))}, room=sid)
        
        dano_efetivo = min(dano_final, mob_data['hp'])
        if 'dmg_tracker' not in mob_data: mob_data['dmg_tracker'] = {}
        mob_data['dmg_tracker'][sid] = mob_data['dmg_tracker'].get(sid, 0) + dano_efetivo

        mob_data['hp'] -= dano_final
        is_dead = mob_data['hp'] <= 0
        
        room = p.get('room')
        atacante_id = p.get('name', '')
        
        emit_area('mob_damaged', {'mob_id': mob_id, 'damage': dano_final, 'new_hp': mob_data['hp'], 'max_hp': mob_data.get('max_hp', mob_data['hp']), 'hit_type': hit_type, 'is_crit': is_crit, 'reduced': reduzido, 'is_miss': is_miss, 'attacker_id': atacante_id, 'w_type': w_type, 'proj': proj, 'owner': ''}, room)
        
        if is_dead:
            xp_total = MOB_DB.get(mob_type_id, {}).get("xp", 40)
            xp_por_participante = _calcular_xp_por_participante(mob_data, xp_total)

            for participant_sid, xp_gained in xp_por_participante.items():
                if participant_sid not in online_players: continue

                part_p = online_players[participant_sid]
                bestiario_registrar_kill(participant_sid, part_p, mob_data.get('type_id', mob_type_id))

                current_exp = part_p.get('exp', 0) + xp_gained
                current_level = part_p.get('level', 1)
                part_p['kills'] = part_p.get('kills', 0) + 1
                char_leveled_up = False
                
                while current_exp >= get_exp_for_level(current_level + 1):
                    current_level += 1
                    char_leveled_up = True
                    
                part_p['exp'] = current_exp
                part_p['level'] = current_level
                
                if char_leveled_up and not part_p.get('is_dead'):
                    max_hp_novo, max_mp_novo = calcular_max_vitais(part_p)
                    part_p['current_hp'] = max_hp_novo
                    part_p['current_mp'] = max_mp_novo
                    emit('sync_vitals', {'current_hp': part_p['current_hp'], 'current_mp': part_p['current_mp']}, room=participant_sid)
                    broadcast_hp(participant_sid, part_p)
                
                _queue_save(part_p) 
                emit('xp_gained', {'amount': xp_gained}, room=participant_sid)
                
                stats_payload = _montar_payload_sync_stats(part_p)
                stats_payload['leveled_up'] = False
                stats_payload['char_leveled_up'] = char_leveled_up
                stats_payload['new_char_level'] = current_level
                emit('sync_stats', stats_payload, room=participant_sid)
                
                if char_leveled_up:
                    # Área toda (chunks vizinhos) de quem upou; antes ia só pro
                    # chunk de quem bateu e os outros não viam o "Level X".
                    # Vai pro próprio player também: todo mundo perto (ele
                    # incluso) recebe o aviso "X has reached Level N!" no chat.
                    emit_area('player_leveled_up', {'name': part_p.get('name', ''), 'level': current_level}, part_p.get('room') or room)
            
            owner_sids = []
            for participant_sid in mob_data['dmg_tracker']:
                if participant_sid not in online_players: continue
                part_name = online_players[participant_sid].get('name', '')
                itens_rolados = rolar_loot(mob_type_id)
                currency_rolada = rolar_currency(mob_type_id)
                loot_id = f"loot_{mob_id}_{int(now * 1000)}_{len(owner_sids)}"
                # has_items só reflete itens de verdade (pra sprite dourada da bag);
                # bag só com currency usa a sprite comum mesmo assim.
                has_items = len(itens_rolados) > 0
                # pos/has_items guardados pra recriar a bag de quem relogar.
                ground_loot[loot_id] = {"items": itens_rolados, "currency": currency_rolada, "room": room, "criado_em": now, "owners": {part_name},
                                        "pos": [mob_data.get('pos_x', 0), mob_data.get('pos_y', 0)], "has_items": has_items}
                emit('mob_died', {'mob_id': mob_id, 'loot_id': loot_id, 'has_items': has_items,
                                  'pos_x': mob_data.get('pos_x', 0), 'pos_y': mob_data.get('pos_y', 0)}, room=participant_sid)
                owner_sids.append(participant_sid)
            emit_area('mob_died', {'mob_id': mob_id, 'loot_id': '', 'has_items': False,
                                   'pos_x': mob_data.get('pos_x', 0), 'pos_y': mob_data.get('pos_y', 0)}, room, skip_sid=owner_sids)
            mob_data['died_at'] = now
            mob_data['target_sid'] = None
            mob_data['returning'] = False
            mob_data['path'] = None
    except Exception: traceback.print_exc()

# Clients antigos ainda mandam mob_pos/mob_attack (IA no client). Agora a IA
# roda no servidor (mob_ai_loop), então isso é ignorado.
@socketio.on('mob_pos')
def handle_mob_pos(data): return

@socketio.on('mob_attack')
def handle_mob_attack(data): return

# O client manda a lista de mobs da cena (id/tipo/velocidade/cooldown/efeito)
# e a impressão digital do mapa. Se o servidor ainda não tem a grade de
# colisão desse mapa (ou o mapa mudou), pede pro client escanear e mandar.
# ---- Conteudo do mapa travado no servidor ----
# O servidor nao le o .tmx: quem manda a lista de mobs/NPCs, o spawn e a
# grade de colisao e' o client. Sem trava, um client modificado criava mobs
# falsos do lado dele, mudava respawn/velocidade dos mobs, apagava paredes da
# grade etc. Agora o 1o registro de cada mapa (ou um de conta admin, quando o
# mapa muda) fica salvo aqui e vale pra todo mundo; o que os outros clients
# mandarem e' ignorado.
#
# Pra atualizar o mapa depois de mudar o World.tmx: coloque o id da sua conta
# em ADMIN_USER_IDS no .env (ex: ADMIN_USER_IDS=1) e entre no jogo uma vez -
# OU apague conteudo_mapas.json + mapas_colisao.json antes de reiniciar.
CONTEUDO_MAPAS_ARQUIVO = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'conteudo_mapas.json')
ADMIN_USER_IDS = {x.strip() for x in os.getenv('ADMIN_USER_IDS', '').split(',') if x.strip()}
SPAWN_PADRAO_RAW = (304.0, 176.0)  # igual WorldScreen.SPAWN_RAW_X/Y
conteudo_mapas = {}

def _carregar_conteudo_mapas():
    global conteudo_mapas
    try:
        with open(CONTEUDO_MAPAS_ARQUIVO, 'r') as f:
            conteudo_mapas = json.load(f)
    except FileNotFoundError:
        pass
    except Exception:
        traceback.print_exc()

def _salvar_conteudo_mapas():
    try:
        with open(CONTEUDO_MAPAS_ARQUIVO, 'w') as f:
            json.dump(conteudo_mapas, f)
    except Exception:
        traceback.print_exc()

_carregar_conteudo_mapas()

def _eh_admin(p):
    return str(p.get('user_id')) in ADMIN_USER_IDS

def _spawn_do_player(p):
    """Onde o player (re)nasce: spawn salvo do mapa dele, ou o padrao."""
    conteudo = conteudo_mapas.get(p.get('mapa')) if p.get('mapa') else None
    if conteudo is None and len(conteudo_mapas) == 1:
        conteudo = next(iter(conteudo_mapas.values()))
    spawn = conteudo.get('spawn') if conteudo else None
    if isinstance(spawn, list) and len(spawn) == 2:
        return encaixar_no_tile(float(spawn[0]), float(spawn[1]))
    return encaixar_no_tile(*SPAWN_PADRAO_RAW)

_CAMPOS_MOB_MAPA = ('id', 'type', 'spawn_range', 'respawn_time', 'speed', 'cooldown', 'hit')
_CAMPOS_NPC_MAPA = ('id', 'npc_id', 'x', 'y', 'floor')

def _so_campos(lista, campos, limite):
    saida = []
    for item in (lista or [])[:limite] if isinstance(lista, list) else []:
        if isinstance(item, dict):
            saida.append({k: item[k] for k in campos if k in item and isinstance(item[k], (str, int, float))})
    return saida

@socketio.on('register_map')
def handle_register_map(data):
    try:
        sid = request.sid
        if sid not in online_players or not isinstance(data, dict): return
        map_id = str(data.get('map', ''))[:200]
        fp = str(data.get('fp', ''))[:64]
        if not map_id: return
        p_reg = online_players[sid]
        p_reg['mapa'] = map_id
        admin = _eh_admin(p_reg)

        guardado = conteudo_mapas.get(map_id)
        if map_id in MAPAS_DO_SERVIDOR:
            # Mapa lido do .tmx pelo servidor: o client nao muda nada. Se a
            # grade dele for diferente, o client esta com outro World.tmx.
            if fp != guardado.get('fp') and fp not in _fp_divergente_avisado:
                _fp_divergente_avisado.add(fp)
                print(f"[MAPA] AVISO: client com colisao diferente do servidor (fp {fp[:8]} != "
                      f"{guardado.get('fp', '')[:8]}). Atualize o World.tmx de um dos dois.")
        elif guardado is None or (admin and guardado.get('fp') != fp):
            spawn = data.get('spawn')
            try:
                spawn = [float(spawn[0]), float(spawn[1])] if isinstance(spawn, list) and len(spawn) == 2 \
                    and all(math.isfinite(float(v)) and abs(float(v)) < 100000000 for v in spawn) else None
            except (TypeError, ValueError):
                spawn = None
            guardado = {'fp': fp, 'mobs': _so_campos(data.get('mobs'), _CAMPOS_MOB_MAPA, 2000),
                        'npcs': _so_campos(data.get('npcs'), _CAMPOS_NPC_MAPA, 256), 'spawn': spawn}
            conteudo_mapas[map_id] = guardado
            _salvar_conteudo_mapas()
            print(f"[MAPA] Conteudo de '{map_id}' salvo ({'admin' if admin else 'primeiro registro'}): "
                  f"{len(guardado['mobs'])} mob(s), {len(guardado['npcs'])} NPC(s)")
        elif guardado.get('fp') != fp:
            print(f"[MAPA] Client com versao diferente de '{map_id}' ignorado (so' admin atualiza o mapa)")

        # Personagem novo (sem posicao salva): comeca no spawn do servidor.
        if p_reg.get('pos_x') == -1 and p_reg.get('pos_y') == -1:
            p_reg['pos_x'], p_reg['pos_y'] = _spawn_do_player(p_reg)
            marcar_movimento(sid)
            nova_sala = get_chunk(p_reg['pos_x'], p_reg['pos_y'], 1)
            if p_reg.get('room') != nova_sala:
                if p_reg.get('room'): leave_room(p_reg['room'])
                join_room(nova_sala)
                p_reg['room'] = nova_sala

        for info in guardado['mobs']:
            if not isinstance(info, dict): continue
            mob_id = str(info.get('id', ''))[:120]
            if not mob_id or spawn_do_mob_id(mob_id) is None: continue
            novo = mob_id not in active_mobs
            m = obter_ou_criar_mob(mob_id, str(info.get('type', '')) or None)
            m['mapa'] = map_id
            # Propriedades do ponto no Tiled (camada MobSpawns).
            try:
                m['spawn_range'] = min(10, max(0, int(info.get('spawn_range', m.get('spawn_range', 0)))))
                m['respawn_seg'] = min(86400.0, max(5.0, float(info.get('respawn_time', m.get('respawn_seg', MOB_RESPAWN_SEG)))))
            except (TypeError, ValueError): pass
            # Primeira vez que o mob aparece: ja nasce num SQM sorteado do raio.
            if novo and m.get('hp', 1) > 0:
                t = _tile_de_nascimento(mob_id, m)
                if t is not None:
                    m['spawn'] = t
                    m['pos_x'], m['pos_y'] = centro_tile(t)
                    m['room'] = get_chunk(m['pos_x'], m['pos_y'], MOB_FLOOR)
            try:
                m['speed'] = min(10.0, max(0.2, float(info.get('speed', m.get('speed', MOB_SPEED_PADRAO)))))
                m['cooldown'] = min(30.0, max(0.3, float(info.get('cooldown', m.get('cooldown', MOB_COOLDOWN_PADRAO)))))
            except (TypeError, ValueError): pass
            hit = str(info.get('hit', 'physical_hit'))[:200]
            if hit and not hit.startswith('res://'): m['hit_effect'] = hit
        print(f"[MOBS] register_map '{map_id}': {len(guardado['mobs'])} mob(s) no mapa, "
              f"{len(active_mobs)} ativo(s) no servidor")
        _registrar_npcs_do_mapa(map_id, guardado['npcs'], sid)
        grade = mapas_colisao.get(map_id)
        # Grade de colisao: so' pede se ainda nao tem nenhuma, ou se e' um
        # admin com versao nova do mapa (ver handle_map_grid).
        if map_id not in MAPAS_DO_SERVIDOR and (grade is None or (admin and grade.get('fp') != fp)):
            emit('need_map_grid', {'map': map_id}, room=sid)
        # Estado atual dos mobs (HP/posição/flag/morte) pra quem acabou de
        # entrar: no join eles ainda podiam não estar registrados.
        room = online_players[sid].get('room')
        if room:
            area = montar_sync_area(sid, room)
            print(f"[MOBS] sync_area_data pro player na sala {room}: {len(area['mobs'])} mob(s) perto")
            emit('sync_area_data', area, room=sid)
    except Exception: traceback.print_exc()

@socketio.on('map_grid')
def handle_map_grid(data):
    try:
        sid = request.sid
        if sid not in online_players or not isinstance(data, dict): return
        map_id = str(data.get('map', ''))[:200]
        w, h = int(data.get('w', 0)), int(data.get('h', 0))
        if not map_id or w <= 0 or h <= 0 or w * h > 4_000_000: return
        fp = str(data.get('fp', ''))[:64]
        if map_id in MAPAS_DO_SERVIDOR: return  # grade vem do .tmx do servidor
        atual = mapas_colisao.get(map_id)
        if atual is not None and atual.get('fp') == fp: return  # já tenho essa versão
        # Grade ja' existe: so' admin troca (senao um client modificado
        # apagava as paredes do mapa pros mobs/validacao de movimento).
        if atual is not None and not _eh_admin(online_players[sid]): return
        bits = base64.b64decode(str(data.get('bits', '')))
        if len(bits) < (w * h + 7) // 8: return
        # bits_leste/bits_baixo (bordas finas) sao opcionais - um client Godot
        # ainda nao atualizado manda so' 'bits' (celula inteira), e a grade
        # cai de volta pro comportamento antigo (nenhuma borda fina).
        n_bytes = (w * h + 7) // 8
        bits_leste = base64.b64decode(str(data['bits_leste'])) if data.get('bits_leste') else _bits_vazios(w, h)
        bits_baixo = base64.b64decode(str(data['bits_baixo'])) if data.get('bits_baixo') else _bits_vazios(w, h)
        if len(bits_leste) < n_bytes: bits_leste = _bits_vazios(w, h)
        if len(bits_baixo) < n_bytes: bits_baixo = _bits_vazios(w, h)
        mapas_colisao[map_id] = {'fp': fp, 'x0': int(data.get('x0', 0)), 'y0': int(data.get('y0', 0)),
                                 'w': w, 'h': h, 'bits': bits, 'bits_leste': bits_leste, 'bits_baixo': bits_baixo}
        salvar_mapas()
        for m in active_mobs.values():
            m['path'] = None
            m['alcance'] = {}
        for npc in active_npcs.values():
            if npc.get('mapa') == map_id:
                npc['path'] = None
        print(f"[MAPA] Grade de colisão de {map_id} recebida ({w}x{h}).")
    except Exception: traceback.print_exc()

@socketio.on('request_loot')
def handle_request_loot(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        loot_id = str(data.get('loot_id', ''))
        loot = ground_loot.get(loot_id)
        if loot is None or online_players[sid].get('name') not in loot.get('owners', ()):
            emit('loot_result', {'loot_id': loot_id, 'items': [], 'currency': 0, 'already_taken': True}, room=sid)
            return
        emit('loot_result', {'loot_id': loot_id, 'items': loot['items'], 'currency': loot['currency'], 'already_taken': False}, room=sid)
    except Exception: traceback.print_exc()

@socketio.on('collect_loot')
def handle_collect_loot(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]

        if p.get('is_dead'): return
        loot_id = str(data.get('loot_id', ''))
        loot = ground_loot.get(loot_id)

        if loot is None or p.get('name') not in loot.get('owners', ()):
            emit('loot_collected', {'loot_id': loot_id, 'items': [], 'currency_gained': 0, 'currency_total': int(p.get('currency', 0)), 'already_taken': True, 'cap_bloqueado': False, 'bag_esvaziada': True}, room=sid)
            return

        # Alcance pra pegar a bag: ate ALCANCE_LOOT_SQM SQMs (+1 de folga).
        if 'pos' in loot:
            tp = tile_de(p.get('pos_x', 0), p.get('pos_y', 0))
            tb = tile_de(loot['pos'][0], loot['pos'][1])
            if max(abs(tp[0] - tb[0]), abs(tp[1] - tb[1])) > ALCANCE_LOOT_SQM + 1: return

        itens = loot['items']
        currency_ganha = loot['currency']

        cap_maximo = calcular_cap_maximo(p.get('level', 1))
        cap_disponivel = max(0.0, cap_maximo - calcular_cap_usado(p))

        novas_instancias = []
        itens_restantes = []
        houve_municao = False
        cap_bloqueado = False

        for entrada in itens:
            item_path = validate_item(entrada.get("item"))
            if not item_path: continue
            qty = max(1, int(entrada.get("qty", 1)))
            cap_unit = obter_cap_unitario_item(item_path)

            if eh_empilhavel(item_path):
                if cap_unit <= 0: qty_pegar = qty
                else: qty_pegar = min(qty, int(math.floor((cap_disponivel + 1e-6) / cap_unit)))
                
                if qty_pegar > 0:
                    adicionar_municao_ao_jogador(p, item_path, qty_pegar)
                    cap_disponivel -= qty_pegar * cap_unit
                    houve_municao = True
                qty_sobrou = qty - qty_pegar
                if qty_sobrou > 0:
                    itens_restantes.append({"item": item_path, "qty": qty_sobrou})
                    cap_bloqueado = True
            else:
                for _ in range(qty):
                    if cap_unit <= 0 or cap_disponivel + 1e-6 >= cap_unit:
                        novas_instancias.append(criar_instancia_item(item_path))
                        cap_disponivel -= cap_unit
                    else:
                        itens_restantes.append({"item": item_path, "qty": 1})
                        cap_bloqueado = True

        inventario_atual = p.get('inventory', [])
        inventario_atual.extend(novas_instancias)
        p['inventory'] = inventario_atual
        novo_total = int(p.get('currency', 0)) + currency_ganha
        p['currency'] = novo_total

        bag_esvaziada = (len(itens_restantes) == 0)
        if bag_esvaziada:
            del ground_loot[loot_id]
        else:
            restante = dict(loot)
            restante.update({"items": itens_restantes, "currency": 0})
            ground_loot[loot_id] = restante

        _queue_save(p)
        
        emit('loot_collected', {
            'loot_id': loot_id, 'items': novas_instancias, 'currency_gained': currency_ganha,
            'currency_total': novo_total, 'already_taken': False,
            'cap_bloqueado': cap_bloqueado, 'bag_esvaziada': bag_esvaziada,
        }, room=sid)
        emit('sync_stats', _montar_payload_sync_stats(p), room=sid)
        if houve_municao:
            emit('inventory_synced', {'inventory': ordenar_favoritos_primeiro(p.get('inventory', [])), 'equipped_items': p.get('equipped_items', {})}, room=sid)
        if bag_esvaziada:
            for n in loot.get('owners', ()):
                s = players_by_name.get(n)
                if s and s != sid: emit('loot_taken', {'loot_id': loot_id}, room=s)
    except Exception: traceback.print_exc()

SKILL_DA_CLASSE = {'Knight': 'melee', 'Ranger': 'distance', 'Mage': 'magic', 'Bard': 'musicality'}
SKILL_JANELA_ACAO_SEG = 4.0

@socketio.on('register_skill_hit')
def handle_skill_hit(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        
        skill_name = data.get('skill') if isinstance(data, dict) else None
        p = online_players[sid]
        
        now = time.time()
        # So' treina skill de verdade: a da propria classe logo depois de um
        # golpe valido num mob, ou defense logo depois de apanhar de um mob.
        # Antes qualquer nome de skill era aceito a cada 1.8s sem fazer nada
        # (subia Magic de Knight parado, e enchia o banco de skills falsas).
        if skill_name == 'defense':
            if now - p.get('last_hit_by_mob', 0) > SKILL_JANELA_ACAO_SEG: return
        elif skill_name == SKILL_DA_CLASSE.get(p.get('class_name', 'Knight')):
            if now - p.get('last_successful_hit', 0) > SKILL_JANELA_ACAO_SEG: return
        else:
            return
        cooldown_time = 1.8
        last_hit = p['last_skill_hit'].get(skill_name, 0)
        
        if now - last_hit < cooldown_time: return
        p['last_skill_hit'][skill_name] = now

        if skill_name == 'defense' and now - p.get('last_attack_time', 0) > DEFENSE_AFK_WINDOW: return

        skills = p.get('skills', {})
        p_class = p.get('class_name', 'Knight')
        is_def = (skill_name == 'defense')
        
        mult = get_skill_multiplier(p_class, is_def)
        current_lvl = skills.get(skill_name, 10)
        hits_key = f"{skill_name}_hits"
        current_hits = skills.get(hits_key, 0) + 1
        
        hits_needed = get_hits_to_level(current_lvl + 1, mult)
        leveled_up = False
        
        if current_hits >= hits_needed:
            current_hits -= hits_needed; current_lvl += 1
            skills[skill_name] = current_lvl; leveled_up = True
            
        skills[hits_key] = current_hits; p['skills'] = skills
        stats_payload = _montar_payload_sync_stats(p)
        stats_payload['leveled_up'] = leveled_up; stats_payload['skill_name'] = skill_name; stats_payload['new_level'] = current_lvl
        emit('sync_stats', stats_payload, room=sid)

        if leveled_up:
            room = p.get('room')
            p_name = p.get('name', '')
            if room and p_name:
                # Inclusive pro próprio player (aviso "X has reached Magic N!" no chat).
                emit_area('player_skill_leveled_up', {'name': p_name, 'skill_name': skill_name, 'new_level': current_lvl}, room)
    except Exception: pass

# =========================================================================
# MOVIMENTO EM PACOTES, SO' NO RAIO DA TELA
# =========================================================================
# Antes cada passo ia na hora pra area inteira (3x3 chunks de 800px, bem
# maior que a tela). Com muita gente junta isso virava dezenas de milhares
# de mensagens por segundo. Agora:
#  - o passo so' entra numa fila (marcar_movimento);
#  - VISAO_FLUSH_SEG em VISAO_FLUSH_SEG o loop manda, pra cada jogador, UM
#    pacote ('mb') com os passos de quem esta no raio da tela dele;
#  - quem entra no raio chega ja' na posicao certa (flag 1 = "aparecer
#    aqui"), quem sai recebe 'mh' e some da tela do outro.
# O raio cobre a tela no zoom mais afastado (PC ate' ~2560px de largura, e
# o celular) com folga; pra sair do raio tem uma margem a mais (senao quem
# fica na borda pisca aparecendo/sumindo).
VISAO_X = 40 * TILE
VISAO_Y = 24 * TILE
VISAO_MARGEM_SAIR = 4 * TILE
VISAO_FLUSH_SEG = 0.1
_CELULA_VISAO = 16 * TILE  # grade espacial so' pra achar quem esta perto rapido

_grade_visao = {}        # celula -> set(sid)
_celula_de = {}          # sid -> celula
_vendo = {}              # sid -> set(sids que ele ve)
_visto_por = {}          # sid -> set(sids que veem ele)
_movidos = set()
_passos_pendentes = {}   # sid -> [(x, y, d_int)] desde o ultimo pacote

def marcar_movimento(sid, passo=None):
    _movidos.add(sid)
    if passo is not None:
        _passos_pendentes.setdefault(sid, []).append(passo)

def _celula(x, y):
    return (int(float(x) // _CELULA_VISAO), int(float(y) // _CELULA_VISAO))

def _atualizar_celula(sid, p):
    nova = _celula(p.get('pos_x', 0), p.get('pos_y', 0))
    antiga = _celula_de.get(sid)
    if antiga == nova: return
    if antiga is not None:
        conj = _grade_visao.get(antiga)
        if conj:
            conj.discard(sid)
            if not conj: _grade_visao.pop(antiga, None)
    _grade_visao.setdefault(nova, set()).add(sid)
    _celula_de[sid] = nova

def _candidatos_visao(x, y):
    alcance_x = int((VISAO_X + VISAO_MARGEM_SAIR) // _CELULA_VISAO) + 1
    alcance_y = int((VISAO_Y + VISAO_MARGEM_SAIR) // _CELULA_VISAO) + 1
    cx, cy = _celula(x, y)
    for dx in range(-alcance_x, alcance_x + 1):
        for dy in range(-alcance_y, alcance_y + 1):
            conj = _grade_visao.get((cx + dx, cy + dy))
            if conj: yield from conj

def _no_raio(p1, p2, ja_via):
    dx = abs(float(p1.get('pos_x', 0)) - float(p2.get('pos_x', 0)))
    dy = abs(float(p1.get('pos_y', 0)) - float(p2.get('pos_y', 0)))
    margem = VISAO_MARGEM_SAIR if ja_via else 0
    return dx <= VISAO_X + margem and dy <= VISAO_Y + margem

def _foto(p):
    """Entrada de 'aparecer aqui': [nome, x, y, dir, flags] (1=posicionar, 2=morto)."""
    return [p.get('name', ''), p.get('pos_x', 0), p.get('pos_y', 0),
            DIR_TO_INT.get(p.get('direction', 'down'), 0), 1 | (2 if p.get('is_dead') else 0)]

def _remover_da_visao(sid):
    _movidos.discard(sid)
    _passos_pendentes.pop(sid, None)
    cel = _celula_de.pop(sid, None)
    if cel is not None:
        conj = _grade_visao.get(cel)
        if conj:
            conj.discard(sid)
            if not conj: _grade_visao.pop(cel, None)
    for v in _visto_por.pop(sid, set()):
        _vendo.get(v, set()).discard(sid)
    for o in _vendo.pop(sid, set()):
        _visto_por.get(o, set()).discard(sid)

def _flush_visao():
    global _movidos, _passos_pendentes
    if not _movidos: return
    movidos, passos = _movidos, _passos_pendentes
    _movidos, _passos_pendentes = set(), {}
    op = online_players
    for m in movidos:
        if m in op: _atualizar_celula(m, op[m])

    vx, vy = float(VISAO_X), float(VISAO_Y)
    vxm, vym = vx + VISAO_MARGEM_SAIR, vy + VISAO_MARGEM_SAIR
    pacotes = {}   # sid -> [entradas]
    sumiram = {}   # sid -> [nomes]
    fotos = set()  # (destino, quem) ja' mandados nesse pacote

    def mandar_foto(dest, quem_sid, quem_p):
        if (dest, quem_sid) in fotos: return
        fotos.add((dest, quem_sid))
        pacotes.setdefault(dest, []).append(_foto(quem_p))

    for m in movidos:
        pm = op.get(m)
        if pm is None: continue
        nome_m = pm.get('name', '')
        mx, my = float(pm.get('pos_x', 0)), float(pm.get('pos_y', 0))
        vendo_m = _vendo.setdefault(m, set())
        visto_m = _visto_por.setdefault(m, set())
        # Passos de m montados UMA vez e reaproveitados pra todo mundo que ja' via.
        lista_passos = passos.get(m)
        entradas_m = [[nome_m, x, y, d, 0] for (x, y, d) in lista_passos] if lista_passos else None
        candidatos = set(_candidatos_visao(mx, my))
        candidatos |= vendo_m
        candidatos |= visto_m
        candidatos.discard(m)
        for v in candidatos:
            pv = op.get(v)
            if pv is None:
                visto_m.discard(v); vendo_m.discard(v)
                continue
            dx = abs(mx - float(pv.get('pos_x', 0)))
            dy = abs(my - float(pv.get('pos_y', 0)))
            dentro = dx <= vx and dy <= vy
            dentro_margem = dentro or (dx <= vxm and dy <= vym)
            # -- quem ve m --
            if v in visto_m:
                if dentro_margem:
                    if entradas_m is not None:
                        pacotes.setdefault(v, []).extend(entradas_m)
                    else:
                        # Mudou de lugar sem "passo" (respawn, teleporte): reposiciona.
                        mandar_foto(v, m, pm)
                else:
                    visto_m.discard(v); _vendo.get(v, set()).discard(m)
                    sumiram.setdefault(v, []).append(nome_m)
            elif dentro:
                visto_m.add(v); _vendo.setdefault(v, set()).add(m)
                mandar_foto(v, m, pm)
            # -- o que m ve (m andou: quem esta parado pode entrar/sair) --
            if v in vendo_m:
                if not dentro_margem:
                    vendo_m.discard(v); _visto_por.get(v, set()).discard(m)
                    sumiram.setdefault(m, []).append(pv.get('name', ''))
            elif dentro:
                vendo_m.add(v); _visto_por.setdefault(v, set()).add(m)
                mandar_foto(m, v, pv)

    for sid_dest, lista in pacotes.items():
        socketio.emit('mb', lista, room=sid_dest)
    for sid_dest, nomes in sumiram.items():
        socketio.emit('mh', nomes, room=sid_dest)

def loop_visao_movimento():
    while True:
        socketio.sleep(VISAO_FLUSH_SEG)
        try:
            _flush_visao()
        except Exception:
            traceback.print_exc()

MOVE_BALDE_MAX = 4.0

def _corrigir_posicao(sid, p):
    # Puxa o client de volta pra posicao que o servidor considera valida.
    emit('sync_local_player', {
        'pos_x': p.get('pos_x', -1), 'pos_y': p.get('pos_y', -1),
        'direction': p.get('direction', 'down')
    }, room=sid)
# O balde recarrega na velocidade em que o client anda de verdade
# (Jogador.WALKSPEED_SQM_SEC) vezes a fome e o speed_modifier do SQM, com
# MOVE_FOLGA de margem pra rede. Mais rapido que isso (speed hack, ou
# ignorar a fome/lama) esvazia o balde e o passo volta.
MOVE_SQM_POR_SEG = 2.2
CUSTO_DIAGONAL = math.sqrt(2.0)
MOVE_FOLGA = 1.05
# {map_id: {(tx, ty): mult}} - so' os SQMs com speed_modifier != 1
# (mapa_tiled.py). Consulta O(1) por passo.
velocidade_tiles = {}

def mult_velocidade_player(p):
    mult = FOME_MULT_VELOCIDADE if esta_com_fome(p) else 1.0
    if not (p.get('pos_x') == -1 and p.get('pos_y') == -1):
        mult *= mult_tile(p, tile_de(p.get('pos_x', 0), p.get('pos_y', 0)))
    return mult

def mult_tile(p, tile):
    # speed_modifier do SQM (so' no andar 1, onde o mapa do servidor vale).
    if int(p.get('floor', 1) or 1) != 1: return 1.0
    vel = velocidade_tiles.get(p.get('mapa'))
    return vel.get(tile, 1.0) if vel else 1.0

def fator_tempo_passo(p, de, para):
    # Quanto um passo de "de" pra "para" demora em relacao a um passo normal:
    # a 1a metade anda na velocidade do SQM de saida e a 2a na do de chegada
    # (o speed_modifier entra/sai no meio do passo - igual o client,
    # Jogador.iniciarPasso). Inclui a fome.
    fome = FOME_MULT_VELOCIDADE if esta_com_fome(p) else 1.0
    return (0.5 / max(0.05, mult_tile(p, de)) + 0.5 / max(0.05, mult_tile(p, para))) / fome

@socketio.on('m')
def handle_m(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        p_name = p.get('name', '')
        if not p_name: return
        
        # Limite de passos por "balde": até MOVE_BALDE_MAX passos de rajada,
        # recarregando na velocidade do player (MOVE_SQM_POR_SEG). Antes era "no mínimo
        # 0.28s entre passos": quando a rede entregava dois passos juntos o
        # segundo era jogado fora e a posição do player no servidor (e na tela
        # dos outros, e pra IA dos mobs) ficava 1 SQM atrás.
        if isinstance(data[0], str): x, y, d_int = float(data[1]), float(data[2]), int(data[3])
        else: x, y, d_int = float(data[0]), float(data[1]), int(data[2])
        if not (math.isfinite(x) and math.isfinite(y)): return
        destino_tile = tile_de(x, y)

        # Passo na diagonal custa √2 do balde (a diagonal leva √2 vezes mais
        # tempo no client): andar na diagonal não deixa ninguém mais rápido.
        custo = 1.0
        if not (p.get('pos_x') == -1 and p.get('pos_y') == -1):
            o_b = tile_de(p.get('pos_x', 0), p.get('pos_y', 0))
            if abs(destino_tile[0] - o_b[0]) == 1 and abs(destino_tile[1] - o_b[1]) == 1: custo = CUSTO_DIAGONAL
        now = time.time()
        # O balde recarrega no ritmo do ULTIMO passo dado (o tempo desde ele
        # e' a duracao dele, que depende dos 2 SQMs - ver fator_tempo_passo).
        fator = p.get('_fator_passo') or (1.0 / max(0.05, mult_velocidade_player(p)))
        recarga = MOVE_SQM_POR_SEG * MOVE_FOLGA / fator
        balde = min(MOVE_BALDE_MAX, p.get('_move_balde', MOVE_BALDE_MAX) + (now - p.get('last_move_time', now)) * recarga)
        p['last_move_time'] = now
        if balde < custo:
            # Andando rapido demais (speed hack): o passo nao vale e o client
            # e' puxado de volta pra posicao do servidor.
            p['_move_balde'] = balde
            _corrigir_posicao(sid, p)
            return
        p['_move_balde'] = balde - custo
        # Morto nao anda; passo tem que ser pro SQM do lado (folga de 1 SQM a
        # mais pra latencia) e nao pode ser parede. Antes o servidor aceitava
        # qualquer x/y: teleporte pra qualquer lugar do mapa.
        if p.get('is_dead'):
            _corrigir_posicao(sid, p)
            return
        if not (p.get('pos_x') == -1 and p.get('pos_y') == -1):
            origem_p = tile_de(p.get('pos_x', 0), p.get('pos_y', 0))
            if max(abs(destino_tile[0] - origem_p[0]), abs(destino_tile[1] - origem_p[1])) > 2:
                _corrigir_posicao(sid, p)
                return
        # Area de quest (ponte do Kharon...) sem a quest feita: nao passa.
        if bloqueado_por_quest(p, destino_tile):
            _avisar_admin_bloqueio(sid, p, "area de quest", destino_tile)
            _corrigir_posicao(sid, p)
            return
        grade_p = mapas_colisao.get(p.get('mapa'))
        if grade_p is not None and int(p.get('floor', 1) or 1) == 1:
            if eh_parede(grade_p, destino_tile):
                _avisar_admin_bloqueio(sid, p, "parede (SQM inteiro)", destino_tile)
                _corrigir_posicao(sid, p)
                return
            # Cerca/corrimao (borda fina entre 2 SQMs): so' da' pra checar num
            # passo de 1 SQM reto, que e' o normal.
            if not (p.get('pos_x') == -1 and p.get('pos_y') == -1):
                o = tile_de(p.get('pos_x', 0), p.get('pos_y', 0))
                if abs(destino_tile[0] - o[0]) + abs(destino_tile[1] - o[1]) == 1 and borda_bloqueada(grade_p, o, destino_tile):
                    _avisar_admin_bloqueio(sid, p, f"borda fina entre {o} e", destino_tile)
                    _corrigir_posicao(sid, p)
                    return
                if diagonal_bloqueada(grade_p, o, destino_tile):
                    _avisar_admin_bloqueio(sid, p, "diagonal fechada", destino_tile)
                    _corrigir_posicao(sid, p)
                    return
        x, y = centro_tile(destino_tile)
        andar_p = int(p.get('floor', 1) or 1)
        npc_no_caminho = any(int(npc.get('floor', 1)) == andar_p
                             and tile_de(npc.get('pos_x', 0), npc.get('pos_y', 0)) == destino_tile
                             for npc in active_npcs.values())
        # Mob vivo tambem bloqueia o SQM (nao da pra atravessar).
        mob_no_caminho = andar_p == MOB_FLOOR and any(
            o.get('hp', 1) > 0 and 'pos_x' in o and tile_do_mob(o) == destino_tile
            for _, o in (_mobs_perto(p['room']) if p.get('room') else active_mobs.items()))
        # Outro player vivo no mesmo andar tambem bloqueia (pra passar por quem
        # esta parado no caminho, o client pede troca de lugar: swap_req).
        # Na Protection Zone players se atravessam.
        player_no_caminho = not na_pz(p, destino_tile) and any(
            o_sid != sid and not online_players[o_sid].get('is_dead')
            and int(online_players[o_sid].get('floor', 1) or 1) == andar_p
            and tile_de(online_players[o_sid].get('pos_x', 0), online_players[o_sid].get('pos_y', 0)) == destino_tile
            for o_sid in (_sids_perto(p['room']) if p.get('room') else list(online_players.keys())))
        if npc_no_caminho or mob_no_caminho or player_no_caminho:
            _corrigir_posicao(sid, p)
            return
        
        if not (p.get('pos_x') == -1 and p.get('pos_y') == -1):
            p['_fator_passo'] = fator_tempo_passo(p, tile_de(p.get('pos_x', 0), p.get('pos_y', 0)), destino_tile)
        p['pos_x'], p['pos_y'], p['direction'] = x, y, DIR_MAP.get(d_int, 'down')
        
        old_room = p.get('room')
        floor = p.get('floor', 1)
        new_room = get_chunk(x, y, floor)
        if old_room != new_room:
            if old_room: leave_room(old_room)
            join_room(new_room)
            p['room'] = new_room
            # Entrou num chunk novo: manda o estado da area nova (mobs parados
            # nao mandam mob_pos, entao sem isso o client nunca sabia onde
            # estavam os mobs dos chunks que acabaram de ficar perto).
            emit('sync_area_data', montar_sync_area(sid, new_room), room=sid)
            
        # Nao manda mais na hora pra area inteira: entra no pacote de
        # movimento (ver loop_visao_movimento), que so' vai pra quem tem esse
        # player no raio da tela.
        marcar_movimento(sid, (x, y, d_int))
    except Exception: pass

def _destino_livre(p, dest):
    # Destino do TP ocupado (player/mob) ou parede: o SQM livre mais perto
    # (ate' 2 SQMs). Nada livre: vai pro proprio destino mesmo.
    grade = mapas_colisao.get(p.get('mapa'))
    ocupados = _tiles_ocupados(excluir_sid=request.sid, sala=get_chunk(*centro_tile(dest), 1))
    for raio in range(0, 3):
        for dx in range(-raio, raio + 1):
            for dy in range(-raio, raio + 1):
                if max(abs(dx), abs(dy)) != raio: continue
                t = (dest[0] + dx, dest[1] + dy)
                if t in ocupados or (grade is not None and eh_parede(grade, t)): continue
                if t in teleportes.get(p.get('mapa'), {}) or bloqueado_por_quest(p, t): continue
                return t
    return dest

@socketio.on('tp')
def handle_tp(data):
    # Teleport (igual Tibia): o SQM do TP tem colisao; o client "anda" pra
    # dentro dele e pede o teleporte. So' vale andando NA DIRECAO do TP, do
    # SQM do lado (reto) - nada de ser empurrado/trocado pra dentro dele.
    try:
        sid = request.sid
        p = online_players.get(sid)
        if not p or not isinstance(data, dict): return
        direcao = str(data.get('direction', ''))
        delta = _DELTA_DIRECAO.get(direcao)
        now = time.time()

        def recusar():
            emit('tp_result', {'ok': False, 'pos_x': p.get('pos_x', -1), 'pos_y': p.get('pos_y', -1),
                               'direction': p.get('direction', 'down')}, room=sid)

        if delta is None or p.get('is_dead') or int(p.get('floor', 1) or 1) != 1 \
                or now - p.get('_ultimo_tp', 0) < TP_COOLDOWN_SEG:
            return recusar()
        origem = tile_de(p.get('pos_x', 0), p.get('pos_y', 0))
        sqm_tp = (origem[0] + delta[0], origem[1] + delta[1])
        dest = teleportes.get(p.get('mapa'), {}).get(sqm_tp)
        if dest is None or bloqueado_por_quest(p, sqm_tp):
            _avisar_admin_bloqueio(sid, p, "teleport invalido (nao tem TP na direcao)", sqm_tp)
            return recusar()
        p['_ultimo_tp'] = now
        _teleportar(sid, p, dest, direcao)
    except Exception:
        traceback.print_exc()

def _teleportar(sid, p, dest, direcao):
    # Leva o player pro SQM dest (ou o livre mais perto). Usado pelo TP do
    # mapa e pelo /tp do admin.
    dest = _destino_livre(p, dest)
    x, y = centro_tile(dest)
    new_room = get_chunk(x, y, 1)
    old_room = p.get('room')
    if old_room != new_room:
        if old_room: leave_room(old_room)
        join_room(new_room)
    p['room'], p['pos_x'], p['pos_y'], p['direction'], p['floor'] = new_room, x, y, direcao, 1
    p['_move_balde'] = MOVE_BALDE_MAX
    p['last_move_time'] = time.time()
    p.pop('_fator_passo', None)
    # Sem "passo": quem ve o player recebe ele reposicionado direto (foto),
    # em vez de ve-lo andando ate' o destino.
    marcar_movimento(sid)
    emit('tp_result', {'ok': True, 'pos_x': x, 'pos_y': y, 'direction': direcao}, room=sid)
    emit('sync_area_data', montar_sync_area(sid, new_room), room=sid)
    return dest

@socketio.on('l')
def handle_l(data):
    # Virou pro lado sem andar (ex: encostou numa parede/player). So' muda a
    # direcao; a posicao do client e' ignorada (antes isso teleportava).
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        if not p.get('name') or not isinstance(data, list) or len(data) < 2: return
        if isinstance(data[0], str): d_int = int(float(data[1]))
        else: d_int = int(data[0])
        if d_int not in DIR_MAP or p.get('is_dead'): return
        if p.get('direction') == DIR_MAP[d_int]: return
        p['direction'] = DIR_MAP[d_int]
        if os.getenv('DEBUG_VIRADA') == '1':
            print(f"[VIRADA] {p.get('name')} -> {DIR_MAP[d_int]} (vistos por {len(_visto_por.get(sid, ()))})")
        # Vai no pacote de movimento como um "passo" pro mesmo SQM: quem ve o
        # player so' vira ele pro lado.
        marcar_movimento(sid, (p.get('pos_x', 0), p.get('pos_y', 0), d_int))
    except Exception: pass

@socketio.on('request_area_sync')
def handle_request_area_sync(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        
        # Antes o client escolhia qualquer x/y/andar (teleporte livre). Agora:
        # morto -> vai pro spawn do mapa (respawn); vivo -> so' aceita um
        # ajuste pequeno (ate' 2 SQMs) da posicao que o servidor ja' conhece.
        if p.get('is_dead'):
            x, y = _spawn_do_player(p)
            floor = 1
        else:
            try:
                x = float(data.get('x', p.get('pos_x', 0)))
                y = float(data.get('y', p.get('pos_y', 0)))
            except (TypeError, ValueError):
                return
            atual = tile_de(p.get('pos_x', 0), p.get('pos_y', 0))
            pedido = tile_de(x, y)
            if not (math.isfinite(x) and math.isfinite(y)) or \
                    max(abs(pedido[0] - atual[0]), abs(pedido[1] - atual[1])) > 2:
                x, y = p.get('pos_x', 0), p.get('pos_y', 0)
            else:
                x, y = centro_tile(pedido)
            floor = int(p.get('floor', 1) or 1)
        
        new_room = get_chunk(x, y, floor)
        old_room = p.get('room')
        if old_room != new_room:
            if old_room: leave_room(old_room)
            join_room(new_room)

        # Teleporte (escada) ou troca de andar: avisa a área ANTIGA e a nova.
        # A escada leva pra outro chunk bem longe; o 'm' seguinte só chega na
        # área nova, então quem ficou perto da escada via o player parado em
        # cima dela pra sempre (ocupando o SQM). Agora eles recebem a posição
        # nova e o player "some" dali.
        old_x = float(p.get('pos_x', x)); old_y = float(p.get('pos_y', y))
        floor_anterior = int(p.get('_floor_broadcast', p.get('floor', floor)) or 1)
        teleportou = math.hypot(x - old_x, y - old_y) > TILE * 3
        if teleportou or floor_anterior != floor:
            p_name = p.get('name', '')
            sala_antiga = get_chunk(old_x, old_y, floor_anterior)
            destinos = set(salas_vizinhas(sala_antiga)) | set(salas_vizinhas(new_room))
            d_int = DIR_TO_INT.get(str(data.get('direction', p.get('direction', 'down'))), 0)
            for r in destinos:
                # (a posicao nova vai pelo pacote de movimento: quem nao ve
                # mais o player recebe "sumiu", quem passa a ver recebe ele ja'
                # no lugar novo)
                if floor_anterior != floor:
                    socketio.emit('player_status_updated', {"name": p_name, "floor": floor, "pos_x": x, "pos_y": y}, room=r, skip_sid=sid)
            p['_floor_broadcast'] = floor
            p['_move_balde'] = MOVE_BALDE_MAX  # o send_move logo depois do teleporte não pode cair no limite
            
        p['room'], p['pos_x'], p['pos_y'], p['floor'] = new_room, x, y, floor
        room = new_room
        marcar_movimento(sid)
        
        if not room: return

        emit('sync_area_data', montar_sync_area(sid, room), room=sid)
    except Exception:
        import traceback
        traceback.print_exc()

@socketio.on('equip_item')
def handle_equip_item(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        instance_id = str(data.get('instance_id', ''))
        slot = str(data.get('slot', ''))
        if slot not in SLOTS_VALIDOS: return

        inventario = p.get('inventory', [])
        alvo = encontrar_instancia(inventario, instance_id)
        if alvo is None: return  
        # Item so' entra no slot dele (ex: arma nao vai no Helm).
        if slot_do_item(alvo.get('item')) != slot: return
        # Item de outra classe: recusa (o client ja' trava o botao; a punicao
        # de _slots_com_classe_invalida fica pra quem ja' tinha equipado).
        req_class = obter_req_class_item(alvo.get('item'))
        if req_class != "All" and req_class != p.get('class_name', 'Knight'): return
        # Level minimo do item (antes nao era checado no servidor).
        try:
            if int(obter_dados_item(alvo.get('item')).get('req_level', 0) or 0) > int(p.get('level', 1) or 1): return
        except (TypeError, ValueError): return
        equipados = p.get('equipped_items', {})
        anterior = equipados.get(slot)

        if eh_municao(alvo.get('item')):
            if slot != SLOT_MUNICAO: return
            if isinstance(anterior, dict) and anterior.get('item') == alvo.get('item'):
                espaco = obter_max_stack(alvo.get('item')) - int(anterior.get('qty', 1))
                mover = min(espaco, int(alvo.get('qty', 1)))
                if mover <= 0: return
                anterior['qty'] = int(anterior.get('qty', 1)) + mover
                alvo['qty'] = int(alvo.get('qty', 1)) - mover
                if alvo['qty'] <= 0: inventario.remove(alvo)
                p['inventory'] = inventario; p['equipped_items'] = equipados
                _queue_save(p)
                emit('inventory_synced', {'inventory': ordenar_favoritos_primeiro(inventario), 'equipped_items': equipados}, room=sid)
                return

        inventario.remove(alvo)
        if anterior is not None: inventario.append(anterior)
        equipados[slot] = alvo

        p['inventory'] = inventario; p['equipped_items'] = equipados
        _queue_save(p)
        emit('inventory_synced', {'inventory': ordenar_favoritos_primeiro(inventario), 'equipped_items': equipados}, room=sid)
        emit('sync_stats', _montar_payload_sync_stats(p), room=sid)
    except Exception: traceback.print_exc()

@socketio.on('unequip_item')
def handle_unequip_item(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        slot = str(data.get('slot', ''))
        equipados = p.get('equipped_items', {})
        alvo = equipados.get(slot)
        if alvo is None: return

        inventario = p.get('inventory', [])
        inventario.append(alvo)
        del equipados[slot]

        p['inventory'] = inventario; p['equipped_items'] = equipados
        _queue_save(p)
        emit('inventory_synced', {'inventory': ordenar_favoritos_primeiro(inventario), 'equipped_items': equipados}, room=sid)
        emit('sync_stats', _montar_payload_sync_stats(p), room=sid)
    except Exception: traceback.print_exc()

@socketio.on('toggle_favorite_item')
def handle_toggle_favorite_item(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        instance_id = str(data.get('instance_id', ''))
        inventario = p.get('inventory', [])
        alvo = encontrar_instancia(inventario, instance_id)
        if alvo is None: return
        alvo['favorite'] = not bool(alvo.get('favorite', False))
        p['inventory'] = inventario
        _queue_save(p)
        emit('inventory_synced', {'inventory': ordenar_favoritos_primeiro(inventario), 'equipped_items': p.get('equipped_items', {})}, room=sid)
    except Exception: traceback.print_exc()

@socketio.on('delete_items')
def handle_delete_items(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        instance_ids = data.get('instance_ids', [])
        if not isinstance(instance_ids, list): return
        instance_ids = instance_ids[:500]
        inventario = p.get('inventory', [])
        ids_validos = set()
        for raw_id in instance_ids:
            instance_id = str(raw_id)
            alvo = encontrar_instancia(inventario, instance_id)
            if alvo is not None and not alvo.get('favorite', False): ids_validos.add(instance_id)

        if not ids_validos: return
        p['inventory'] = [inst for inst in inventario if inst.get('id') not in ids_validos]
        _queue_save(p)
        emit('inventory_synced', {'inventory': ordenar_favoritos_primeiro(p['inventory']), 'equipped_items': p.get('equipped_items', {})}, room=sid)
        emit('sync_stats', _montar_payload_sync_stats(p), room=sid)
    except Exception: traceback.print_exc()

# 'floor' saiu: trocar de andar pelo client deixava o player "invisivel" pros
# mobs (eles so' atacam no andar 1). O client atual nao troca de andar.
CHAVES_UPDATE_STATUS_PERMITIDAS = {'is_typing', 'is_in_settings', 'is_in_skins', 'current_hp', 'current_mp', 'custom_z', 'is_dead'}

@socketio.on('update_status')
def handle_update_status(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        key = data.get('key')
        value = data.get('value')
        if key not in CHAVES_UPDATE_STATUS_PERMITIDAS: return
        
        p = online_players[sid]

        if key in ('current_hp', 'current_mp'):
            try: value = float(value)
            except (TypeError, ValueError): return
            max_hp, max_mp = calcular_max_vitais(p)
            limite = max_hp if key == 'current_hp' else max_mp
            value = max(0.0, min(value, limite))
            # Enquanto está morto o HP fica travado em 0 (o client manda
            # is_dead=false ANTES do HP cheio no respawn, ver _renascer()).
            if key == 'current_hp' and p.get('is_dead'): value = 0.0
            # O HP de verdade é o do servidor (dano de mob e regen são
            # calculados aqui). O client reporta o HP dele a cada sync_stats
            # (ex: todo hit de defense), e esse valor chega atrasado: se o mob
            # bateu no meio tempo, o valor velho (maior) sobrescrevia o do
            # servidor e a barra do remote "voltava". Então o client só pode
            # BAIXAR o HP por aqui; cura vem do regen/level up/respawn do servidor.
            if key == 'current_hp':
                try: hp_servidor = float(p.get('current_hp', -1))
                except (TypeError, ValueError): hp_servidor = -1.0
                if hp_servidor >= 0: value = min(value, hp_servidor)
            else:
                # Mesma regra pro MP: o client so' pode BAIXAR.
                try: mp_servidor = float(p.get('current_mp', -1))
                except (TypeError, ValueError): mp_servidor = -1.0
                if mp_servidor >= 0: value = min(value, mp_servidor)
        elif key in ('is_typing', 'is_in_settings', 'is_in_skins'):
            value = bool(value)
        elif key == 'custom_z':
            try: value = max(-100.0, min(100.0, float(value)))
            except (TypeError, ValueError): return

        if key == 'is_dead':
            value = bool(value)
            # Quem decide a morte e' o servidor. Antes o client podia mandar
            # is_dead=true e depois false: renascia com HP/MP cheios na hora,
            # em qualquer lugar (cura infinita).
            if value: return
            if not p.get('is_dead'): return
            if value:
                p['current_hp'] = 0.0
            else:
                # Respawn: servidor devolve o HP/MP cheio (o client não pode
                # mais subir o HP via update_status).
                max_hp_r, max_mp_r = calcular_max_vitais(p)
                p['current_hp'] = max_hp_r
                p['current_mp'] = max_mp_r
                # Renasce SEMPRE no spawn (normalmente o request_area_sync ja'
                # levou pra la'; isso cobre um client que pulou essa etapa).
                sx, sy = _spawn_do_player(p)
                if (p.get('pos_x'), p.get('pos_y')) != (sx, sy):
                    p['pos_x'], p['pos_y'], p['floor'] = sx, sy, 1
                    marcar_movimento(sid)
                    nova_sala = get_chunk(sx, sy, 1)
                    if p.get('room') != nova_sala:
                        if p.get('room'): leave_room(p['room'])
                        join_room(nova_sala)
                        p['room'] = nova_sala
                    _corrigir_posicao(sid, p)

        p[key] = value
        if key == 'floor':
            try: p['_floor_broadcast'] = int(value)
            except (TypeError, ValueError): pass
        p_name = p.get('name')
        room = p.get('room')
        if key == 'current_hp':
            broadcast_hp(sid, p)
        elif key != 'current_mp':
            if room and p_name: emit_area('player_status_updated', {"name": p_name, key: value}, room, skip_sid=sid)
            if key == 'is_dead' and value:
                p['_ultimo_hp_broadcast'] = [0.0, calcular_max_vitais(p)[0]]
            elif key == 'is_dead':
                emit('sync_vitals', {'current_hp': p['current_hp'], 'current_mp': p['current_mp']}, room=sid)
                broadcast_hp(sid, p)
    except Exception: pass

@socketio.on('save_floor')
def handle_save_floor(data):
    try:
        sid = request.sid
        # Ignorado (ver CHAVES_UPDATE_STATUS_PERMITIDAS): o andar nao pode vir
        # do client, senao da' pra ficar fora do alcance dos mobs.
        return
    except Exception: pass

# --- PARTY SYSTEM ---
# Estado 100% em memória (nunca persistido) - ver comentário junto de `parties`
# lá em cima. party_id = sid do líder no momento da criação (chave estável;
# não muda com o failover, só o 'leader_sid' de dentro do dict muda).

def _get_party(sid):
    pid = online_players.get(sid, {}).get('party_id')
    return parties.get(pid), pid

def _party_public_member_dict(sid):
    p = online_players.get(sid)
    if not p: return None
    return {
        'name': p.get('name', ''),
        'class_name': p.get('class_name', 'Knight'),
        'level': p.get('level', 1),
    }

def _calcular_bonus_percentual(party):
    membros = [s for s in party['members'] if s in online_players]
    if len(membros) <= 1:
        return 0
    classes_distintas = len({online_players[s].get('class_name', 'Knight') for s in membros})
    return CLASS_EXP_BONUS_PER_DISTINCT_CLASS * classes_distintas

def _montar_payload_party_update(party_id):
    party = parties.get(party_id)
    if not party: return None
    leader_p = online_players.get(party['leader_sid'])
    membros = [_party_public_member_dict(s) for s in party['members'] if s in online_players]
    return {
        'party_id': party_id,
        'leader_name': leader_p.get('name', '') if leader_p else '',
        'members': [m for m in membros if m],
        'bonus_percent': _calcular_bonus_percentual(party),
    }

def _emit_party_update(party_id):
    payload = _montar_payload_party_update(party_id)
    if not payload: return
    party = parties.get(party_id)
    for m_sid in party['members']:
        if m_sid in online_players:
            emit('party_update', payload, room=m_sid)

# Ponto único de saída de uma party: kick, self-kick (sair), desconexão e
# limpeza de sessão antiga no relogin passam todos por aqui. Isso já resolve
# sozinho o failover de líder (promove o próximo por ordem de entrada) e o
# auto-disband (sobrou 1 só) sem duplicar a lógica em 3 lugares diferentes.
def _remover_do_party(sid, motivo="left"):
    party, party_id = _get_party(sid)
    if not party:
        return False

    era_lider = (party['leader_sid'] == sid)
    if sid in party['members']:
        party['members'].remove(sid)
    if sid in online_players:
        online_players[sid]['party_id'] = None

    emit('party_disbanded', {'reason': motivo}, room=sid)

    if len(party['members']) <= 1:
        for remanescente_sid in party['members']:
            if remanescente_sid in online_players:
                online_players[remanescente_sid]['party_id'] = None
            emit('party_disbanded', {'reason': 'auto_disband'}, room=remanescente_sid)
        del parties[party_id]
        return True

    if era_lider:
        party['leader_sid'] = party['members'][0]

    _emit_party_update(party_id)
    return True

# Bug: se o ALVO de um convite de party desconecta antes de aceitar/recusar,
# o `pending_invites` de quem convidou nunca era limpo (só existia
# tratamento pra recusa manual) - o convite ficava "pendurado" pra sempre e
# `sent_party_invites` no client de quem convidou nunca era apagado, travando
# o botão de convite mesmo depois do alvo relogar (novo sid, mesmo nome).
# Roda pra QUALQUER desconexão, cobrindo tanto "sid era alvo de um convite"
# (avisa quem convidou, reaproveitando o mesmo evento da recusa manual) quanto
# "sid era quem convidou" (só limpa o convite órfão, sem ninguém pra avisar).
def _limpar_convites_de_party_pendentes(sid, nome_sid):
    for party in list(parties.values()):
        pending = party.get('pending_invites', {})
        if sid in pending:
            inviter_sid = pending.pop(sid)
            if inviter_sid in online_players:
                emit('party_invite_declined', {'target_name': nome_sid}, room=inviter_sid)
        for alvo_sid in [a for a, inv in pending.items() if inv == sid]:
            pending.pop(alvo_sid, None)

@socketio.on('create_party')
def handle_create_party(data):
    sid = request.sid
    if sid not in online_players: return
    if online_players[sid].get('party_id'): return
    party_id = sid
    parties[party_id] = {'leader_sid': sid, 'members': [sid], 'pending_invites': {}}
    online_players[sid]['party_id'] = party_id
    _emit_party_update(party_id)

@socketio.on('invite_party')
def handle_invite_party(data):
    conn = None
    try:
        sid = request.sid
        if sid not in online_players: return
        # Ele ja' tinha me convidado pra party dele: convidar de volta = aceitar
        # (em vez de criar uma party nova).
        alvo_nome = str(data.get('target_name', '') if isinstance(data, dict) else '').strip()
        alvo_sid = players_by_name.get(alvo_nome)
        if alvo_sid and alvo_sid in online_players and alvo_sid != sid:
            party_dele, _pid_dele = _get_party(alvo_sid)
            if party_dele and party_dele.get('pending_invites', {}).get(sid) == alvo_sid:
                handle_accept_party_invite({'inviter_name': alvo_nome})
                return
        party, party_id = _get_party(sid)
        if not party:
            # Convidar sem ter party (botao Party da janela do jogador): cria
            # uma com quem convidou de lider, igual o create_party.
            party_id = sid
            parties[party_id] = {'leader_sid': sid, 'members': [sid], 'pending_invites': {}}
            online_players[sid]['party_id'] = party_id
            party = parties[party_id]
            _emit_party_update(party_id)
        if party['leader_sid'] != sid:
            emit('party_invite_result', {'success': False, 'reason': 'not_leader', 'target_name': ''}, room=sid)
            return

        target_name = str(data.get('target_name', '') if isinstance(data, dict) else '').strip()

        if len(party['members']) >= PARTY_MAX_SIZE:
            emit('party_invite_result', {'success': False, 'reason': 'party_full', 'target_name': target_name}, room=sid)
            return

        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("SELECT 1 FROM characters WHERE name = %s", (target_name,))
        existe = c.fetchone() is not None
        c.close()

        if not existe:
            emit('party_invite_result', {'success': False, 'reason': 'not_found', 'target_name': target_name}, room=sid)
            return

        target_sid = players_by_name.get(target_name)
        if not target_sid or target_sid not in online_players:
            emit('party_invite_result', {'success': False, 'reason': 'offline', 'target_name': target_name}, room=sid)
            return

        target_party_id = online_players[target_sid].get('party_id')
        if target_party_id == party_id:
            emit('party_invite_result', {'success': False, 'reason': 'already_in_your_party', 'target_name': target_name}, room=sid)
            return
        if target_party_id:
            emit('party_invite_result', {'success': False, 'reason': 'already_in_party', 'target_name': target_name}, room=sid)
            return

        party['pending_invites'][target_sid] = sid
        emit('party_invite_result', {'success': True, 'reason': '', 'target_name': target_name}, room=sid)
        inviter_p = online_players[sid]
        emit('party_invite_received', {
            'inviter_name': inviter_p.get('name', ''),
            'inviter_level': inviter_p.get('level', 1),
        }, room=target_sid)
    except Exception: traceback.print_exc()
    finally:
        if conn: db_pool.putconn(conn)

@socketio.on('accept_party_invite')
def handle_accept_party_invite(data):
    sid = request.sid
    if sid not in online_players: return
    inviter_name = str(data.get('inviter_name', '')).strip()
    inviter_sid = players_by_name.get(inviter_name)
    if not inviter_sid or inviter_sid not in online_players: return
    party, party_id = _get_party(inviter_sid)
    if not party: return
    if party['pending_invites'].get(sid) != inviter_sid: return
    del party['pending_invites'][sid]
    if len(party['members']) >= PARTY_MAX_SIZE: return

    # Quem aceita já sendo líder (ou membro) de OUTRA party não pode ficar
    # "nas duas" - cada lado achava que era líder da própria (bug reportado).
    # Sai da party antiga primeiro (isso já cuida de promover o próximo líder
    # ou desfazer a party antiga sozinho, ver _remover_do_party) e só depois
    # entra na nova, como convidado.
    party_atual = online_players[sid].get('party_id')
    if party_atual and party_atual != party_id:
        _remover_do_party(sid, motivo="switched")

    party['members'].append(sid)
    online_players[sid]['party_id'] = party_id
    _emit_party_update(party_id)

@socketio.on('decline_party_invite')
def handle_decline_party_invite(data):
    sid = request.sid
    if sid not in online_players: return
    inviter_name = str(data.get('inviter_name', '')).strip()
    inviter_sid = players_by_name.get(inviter_name)
    if not inviter_sid: return
    party, _party_id = _get_party(inviter_sid)
    if not party: return
    if party['pending_invites'].pop(sid, None) is None: return
    # Avisa quem convidou: sem isso o botão de convite dele (ActionsWindow)
    # ficava azul (convite pendente) pra sempre, já que ele nunca saberia que
    # o convite foi recusado.
    if inviter_sid in online_players:
        emit('party_invite_declined', {'target_name': online_players[sid].get('name', '')}, room=inviter_sid)

@socketio.on('kick_party_member')
def handle_kick_party_member(data):
    # Também serve de self-kick/sair: o client manda isso com target_name ==
    # o próprio nome quando um membro não-líder clica no KickButton da própria linha.
    sid = request.sid
    if sid not in online_players: return
    party, party_id = _get_party(sid)
    if not party: return
    target_name = str(data.get('target_name', '')).strip()
    target_sid = players_by_name.get(target_name)
    if not target_sid or target_sid not in party['members']: return

    eh_self = (target_sid == sid)
    if not eh_self and party['leader_sid'] != sid:
        return
    # Líder saindo com gente na party: _remover_do_party passa a liderança
    # pro próximo por ordem de entrada (X da própria linha na aba Party).

    _remover_do_party(target_sid, motivo="kicked" if not eh_self else "left")

@socketio.on('get_party_status')
def handle_get_party_status(data):
    sid = request.sid
    if sid not in online_players: return
    party, party_id = _get_party(sid)
    if party:
        emit('party_update', _montar_payload_party_update(party_id), room=sid)
    else:
        emit('party_disbanded', {'reason': 'none'}, room=sid)

# --- TRADE (troca de itens/moeda entre 2 players, validado 100% no servidor) ---
# Mesmo espírito do sistema de party: tudo em memória (trade_pending_invites,
# trades), nunca vai pro banco, não sobrevive a desconexão. Um trade_id só
# existe depois do accept; antes disso é só um convite pendente (trade_pending_invites).
TRADE_INVITE_TIMEOUT_SEG = 60
# Distancia maxima (SQMs) pra mandar/aceitar trade. +1 de folga no servidor
# pro atraso do movimento (o client ja' esconde o botao acima de 5).
TRADE_DISTANCIA_SQM = 5
# Um player pode convidar varios ao mesmo tempo; o primeiro que aceitar
# cancela os outros convites (dos dois lados).
trade_pending_invites = {}  # (inviter_sid, target_sid) -> criado_em
trades = {}  # trade_id -> {'a_sid','b_sid','offers':{sid:{'items':[{'instance_id','qty'}],'currency':int}},'ready':{sid:bool},'locked':{sid:bool}}

def _get_trade(sid):
    trade_id = online_players.get(sid, {}).get('trade_id')
    return trades.get(trade_id), trade_id

def _outro_lado_trade(trade, sid):
    return trade['b_sid'] if trade['a_sid'] == sid else trade['a_sid']

def _distancia_sqm_players(a_sid, b_sid):
    pa, pb = online_players.get(a_sid), online_players.get(b_sid)
    if not pa or not pb: return 9999
    try:
        ax, ay = tile_de(float(pa.get('pos_x', 0)), float(pa.get('pos_y', 0)))
        bx, by = tile_de(float(pb.get('pos_x', 0)), float(pb.get('pos_y', 0)))
    except (TypeError, ValueError):
        return 9999
    return max(abs(ax - bx), abs(ay - by))

def _cancelar_convite_trade(inviter_sid, target_sid, motivo=None):
    """Tira um convite pendente: some o balao do alvo e (com motivo) avisa quem convidou."""
    if trade_pending_invites.pop((inviter_sid, target_sid), None) is None: return
    inviter_name = online_players.get(inviter_sid, {}).get('name', '')
    if target_sid in online_players:
        socketio.emit('trade_pending_status', {'pending': False, 'inviter_name': inviter_name}, room=target_sid)
    if motivo and inviter_sid in online_players:
        socketio.emit('trade_invite_result', {'success': False, 'reason': motivo,
                      'target_name': online_players.get(target_sid, {}).get('name', '')}, room=inviter_sid)

def _iniciar_trade(inviter_sid, sid):
    trade_pending_invites.pop((inviter_sid, sid), None)
    # Primeiro que aceitou: cancela todos os outros convites desses dois.
    for (inv, alvo) in list(trade_pending_invites):
        if inv in (inviter_sid, sid) or alvo in (inviter_sid, sid):
            _cancelar_convite_trade(inv, alvo, 'target_busy' if alvo in (inviter_sid, sid) else None)
    trade_id = uuid.uuid4().hex
    trades[trade_id] = {
        'a_sid': inviter_sid, 'b_sid': sid,
        'offers': {inviter_sid: {'items': [], 'currency': 0}, sid: {'items': [], 'currency': 0}},
        'ready': {inviter_sid: False, sid: False},
        'locked': {inviter_sid: False, sid: False},
    }
    online_players[inviter_sid]['trade_id'] = trade_id
    online_players[sid]['trade_id'] = trade_id
    socketio.emit('trade_started', {'trade_id': trade_id, 'self_name': online_players[inviter_sid].get('name', ''), 'other_name': online_players[sid].get('name', '')}, room=inviter_sid)
    socketio.emit('trade_started', {'trade_id': trade_id, 'self_name': online_players[sid].get('name', ''), 'other_name': online_players[inviter_sid].get('name', '')}, room=sid)

def _validar_oferta(sid, offer):
    # Nunca confia no que o client mandou: reconstrói a oferta do zero a
    # partir do inventário REAL do player nesse exato instante. Usado tanto
    # no offer_update quanto (de novo, do zero) no lock_in/execução final.
    p = online_players.get(sid)
    if not p: return None
    inventario = p.get('inventory', [])
    vistos = set()
    itens_validados = []
    itens_oferta = offer.get('items', [])
    if not isinstance(itens_oferta, list) or len(itens_oferta) > 50: return None
    for entrada in itens_oferta:
        if not isinstance(entrada, dict): continue
        instance_id = str(entrada.get('instance_id', ''))
        if not instance_id or instance_id in vistos: return None
        inst = encontrar_instancia(inventario, instance_id)
        if inst is None: return None
        item_path = inst.get('item')
        if eh_empilhavel(item_path):
            try: qty = int(entrada.get('qty', 1))
            except (TypeError, ValueError): return None
            if qty <= 0 or qty > int(inst.get('qty', 1)): return None
        else:
            qty = 1
        vistos.add(instance_id)
        itens_validados.append({'instance_id': instance_id, 'item': item_path, 'qty': qty})
    try:
        currency = int(offer.get('currency', 0))
    except (TypeError, ValueError):
        return None
    if currency < 0 or currency > int(p.get('currency', 0)): return None
    return {'items': itens_validados, 'currency': currency}

def _montar_payload_offer(offer):
    return {'items': offer.get('items', []), 'currency': offer.get('currency', 0)}

def _emit_trade_offer_updated(trade_id):
    trade = trades.get(trade_id)
    if not trade: return
    a_sid, b_sid = trade['a_sid'], trade['b_sid']
    a_offer = _montar_payload_offer(trade['offers'].get(a_sid, {}))
    b_offer = _montar_payload_offer(trade['offers'].get(b_sid, {}))
    if a_sid in online_players:
        emit('trade_offer_updated', {'self_offer': a_offer, 'other_offer': b_offer}, room=a_sid)
    if b_sid in online_players:
        emit('trade_offer_updated', {'self_offer': b_offer, 'other_offer': a_offer}, room=b_sid)

def _emit_trade_lock_state(trade_id):
    trade = trades.get(trade_id)
    if not trade: return
    a_sid, b_sid = trade['a_sid'], trade['b_sid']
    if a_sid in online_players:
        emit('trade_lock_state_updated', {'self_locked': trade['locked'][a_sid], 'other_locked': trade['locked'][b_sid]}, room=a_sid)
    if b_sid in online_players:
        emit('trade_lock_state_updated', {'self_locked': trade['locked'][b_sid], 'other_locked': trade['locked'][a_sid]}, room=b_sid)

# Ponto único de saída de um trade: cancelamento explícito, falha de
# revalidação e desconexão de qualquer um dos dois lados passam todos por
# aqui - garante que o lado que continua online NUNCA fica esperando pra
# sempre (motivo de CancelBtn/CloseBtn terem que cancelar pros dois, não só
# esconder a janela local).
def _remover_do_trade(sid, motivo="cancelled"):
    trade, trade_id = _get_trade(sid)
    # Mesmo sem trade ativo, pode haver convites pendentes envolvendo sid.
    for (inv_sid, alvo_sid) in list(trade_pending_invites):
        if sid in (inv_sid, alvo_sid):
            _cancelar_convite_trade(inv_sid, alvo_sid, 'offline' if alvo_sid == sid else None)

    if not trade:
        return False

    a_sid, b_sid = trade['a_sid'], trade['b_sid']
    for lado_sid in (a_sid, b_sid):
        if lado_sid in online_players:
            online_players[lado_sid]['trade_id'] = None
            emit('trade_cancelled', {'reason': motivo}, room=lado_sid)
            emit('trade_pending_status', {'pending': False, 'inviter_name': online_players.get(a_sid, {}).get('name', '')}, room=lado_sid)
    del trades[trade_id]
    return True

@socketio.on('trade_invite')
def handle_trade_invite(data):
    sid = request.sid
    if sid not in online_players or not isinstance(data, dict): return
    if online_players[sid].get('trade_id'): return

    target_name = str(data.get('target_name', '')).strip()
    target_sid = players_by_name.get(target_name)
    if not target_sid or target_sid not in online_players:
        emit('trade_invite_result', {'success': False, 'reason': 'offline', 'target_name': target_name}, room=sid)
        return
    if target_sid == sid:
        emit('trade_invite_result', {'success': False, 'reason': 'self', 'target_name': target_name}, room=sid)
        return
    if online_players[target_sid].get('trade_id'):
        emit('trade_invite_result', {'success': False, 'reason': 'target_busy', 'target_name': target_name}, room=sid)
        return
    if _distancia_sqm_players(sid, target_sid) > TRADE_DISTANCIA_SQM + 1:
        emit('trade_invite_result', {'success': False, 'reason': 'too_far', 'target_name': target_name}, room=sid)
        return
    # Ele ja' tinha me convidado: clicar em Trade nele = aceitar.
    if (target_sid, sid) in trade_pending_invites:
        _iniciar_trade(target_sid, sid)
        return
    if (sid, target_sid) in trade_pending_invites:
        emit('trade_invite_result', {'success': False, 'reason': 'already_pending', 'target_name': target_name}, room=sid)
        return

    trade_pending_invites[(sid, target_sid)] = time.time()
    emit('trade_invite_result', {'success': True, 'reason': '', 'target_name': target_name}, room=sid)
    inviter_name = online_players[sid].get('name', '')
    emit('trade_invite_received', {'inviter_name': inviter_name}, room=target_sid)
    emit('trade_pending_status', {'pending': True, 'inviter_name': inviter_name}, room=target_sid)

@socketio.on('accept_trade_invite')
def handle_accept_trade_invite(data):
    sid = request.sid
    if sid not in online_players or not isinstance(data, dict): return
    inviter_name = str(data.get('inviter_name', '')).strip()
    inviter_sid = players_by_name.get(inviter_name)
    if not inviter_sid or inviter_sid not in online_players: return
    if (inviter_sid, sid) not in trade_pending_invites: return
    if online_players[sid].get('trade_id') or online_players[inviter_sid].get('trade_id'):
        _cancelar_convite_trade(inviter_sid, sid, 'target_busy')
        return
    if _distancia_sqm_players(sid, inviter_sid) > TRADE_DISTANCIA_SQM + 1:
        emit('trade_invite_result', {'success': False, 'reason': 'too_far', 'target_name': inviter_name}, room=sid)
        return
    _iniciar_trade(inviter_sid, sid)

@socketio.on('trade_offer_update')
def handle_trade_offer_update(data):
    sid = request.sid
    if sid not in online_players: return
    trade, trade_id = _get_trade(sid)
    if not trade: return

    # Depois que o player já mandou "ready" (avançou pra tela de
    # confirmação), a oferta fica congelada - nenhuma mudança é aceita.
    # Isso é o que garante que não dá pra trocar item de última hora depois
    # que os dois já viram a oferta um do outro (a TradingMenu só abre
    # quando os DOIS já estão ready, ver handle_trade_ready).
    if trade['ready'].get(sid):
        emit('trade_offer_updated', {
            'self_offer': _montar_payload_offer(trade['offers'].get(sid, {})),
            'other_offer': _montar_payload_offer(trade['offers'].get(_outro_lado_trade(trade, sid), {})),
        }, room=sid)
        return

    validado = _validar_oferta(sid, data if isinstance(data, dict) else {})
    if validado is None:
        # Oferta inválida (item que não tem mais, qty maior que o estoque,
        # moeda além do saldo...): ignora e reemite a última oferta válida
        # pro próprio jogador se auto-corrigir, sem afetar o outro lado.
        emit('trade_offer_updated', {
            'self_offer': _montar_payload_offer(trade['offers'].get(sid, {})),
            'other_offer': _montar_payload_offer(trade['offers'].get(_outro_lado_trade(trade, sid), {})),
        }, room=sid)
        return

    trade['offers'][sid] = validado
    _emit_trade_offer_updated(trade_id)

@socketio.on('trade_ready')
def handle_trade_ready(data):
    sid = request.sid
    if sid not in online_players: return
    trade, trade_id = _get_trade(sid)
    if not trade: return
    trade['ready'][sid] = True
    # Só abre a tela de confirmação pros DOIS ao mesmo tempo, quando os DOIS
    # já travaram a própria oferta (ver guarda acima) - ninguém vê a oferta
    # do outro antes de já ter a própria congelada, o que fecha a brecha de
    # golpe de troca de item de última hora.
    if trade['ready'][trade['a_sid']] and trade['ready'][trade['b_sid']]:
        for lado_sid in (trade['a_sid'], trade['b_sid']):
            if lado_sid in online_players:
                emit('trade_advanced_to_confirm', {}, room=lado_sid)

def _executar_trade(trade_id):
    trade = trades.get(trade_id)
    if not trade: return
    a_sid, b_sid = trade['a_sid'], trade['b_sid']
    a_p, b_p = online_players.get(a_sid), online_players.get(b_sid)
    if not a_p or not b_p:
        _remover_do_trade(a_sid if a_p else b_sid, motivo="disconnected")
        return

    # Revalida os dois lados do zero contra o inventário ATUAL (pode ter
    # mudado desde o último offer_update) antes de mutar qualquer coisa.
    a_offer = _validar_oferta(a_sid, trade['offers'].get(a_sid, {}))
    b_offer = _validar_oferta(b_sid, trade['offers'].get(b_sid, {}))
    if a_offer is None or b_offer is None:
        _remover_do_trade(a_sid, motivo="invalid_offer")
        return

    a_inv, b_inv = a_p.setdefault('inventory', []), b_p.setdefault('inventory', [])
    a_insts = [encontrar_instancia(a_inv, e['instance_id']) for e in a_offer['items']]
    b_insts = [encontrar_instancia(b_inv, e['instance_id']) for e in b_offer['items']]
    if any(i is None for i in a_insts) or any(i is None for i in b_insts):
        _remover_do_trade(a_sid, motivo="invalid_offer")
        return

    # Checagem de capacidade: simula o resultado final antes de aplicar de
    # verdade, pra não deixar ninguém estourar o cap ao receber os itens do
    # outro lado (nunca descarta item silenciosamente - aborta o trade inteiro).
    def _cap_apos_troca(p, insts_saindo, offer_saindo_items, offer_entrando_items):
        cap = calcular_cap_usado(p)
        for inst, entrada in zip(insts_saindo, offer_saindo_items):
            cap -= obter_cap_instancia(inst) if not eh_empilhavel(inst.get('item')) else obter_cap_unitario_item(inst.get('item')) * entrada['qty']
        for entrada in offer_entrando_items:
            cap += obter_cap_unitario_item(entrada['item']) * entrada['qty']
        return cap

    cap_a = _cap_apos_troca(a_p, a_insts, a_offer['items'], b_offer['items'])
    cap_b = _cap_apos_troca(b_p, b_insts, b_offer['items'], a_offer['items'])
    if cap_a > calcular_cap_maximo(a_p.get('level', 1)) or cap_b > calcular_cap_maximo(b_p.get('level', 1)):
        _remover_do_trade(a_sid, motivo="invalid_offer")
        return

    # Move as instâncias de verdade (preserva id/favorite, nunca recria via
    # criar_instancia_item) e aplica as duas moedas juntas, só depois de
    # tudo já ter sido validado acima - nada disso pode falhar a partir daqui.
    def _transferir(origem_inv, destino_p, insts, offer_items):
        for inst, entrada in zip(insts, offer_items):
            if eh_empilhavel(inst.get('item')) and entrada['qty'] < int(inst.get('qty', 1)):
                inst['qty'] = int(inst.get('qty', 1)) - entrada['qty']
                adicionar_municao_ao_jogador(destino_p, inst.get('item'), entrada['qty'])
            else:
                origem_inv.remove(inst)
                if eh_empilhavel(inst.get('item')):
                    adicionar_municao_ao_jogador(destino_p, inst.get('item'), entrada['qty'])
                else:
                    # "favorite" é preferência de quem marcou, não do item em
                    # si - o novo dono nunca marcou isso, começa limpo.
                    inst['favorite'] = False
                    destino_p.setdefault('inventory', []).append(inst)

    _transferir(a_inv, b_p, a_insts, a_offer['items'])
    _transferir(b_inv, a_p, b_insts, b_offer['items'])
    a_p['currency'] = int(a_p.get('currency', 0)) - a_offer['currency'] + b_offer['currency']
    b_p['currency'] = int(b_p.get('currency', 0)) - b_offer['currency'] + a_offer['currency']

    _queue_save(a_p)
    _queue_save(b_p)
    for lado_sid, lado_p in ((a_sid, a_p), (b_sid, b_p)):
        emit('inventory_synced', {'inventory': ordenar_favoritos_primeiro(lado_p.get('inventory', [])), 'equipped_items': lado_p.get('equipped_items', {})}, room=lado_sid)
        emit('sync_stats', _montar_payload_sync_stats(lado_p), room=lado_sid)
        online_players[lado_sid]['trade_id'] = None
        emit('trade_pending_status', {'pending': False, 'inviter_name': online_players.get(a_sid, {}).get('name', '')}, room=lado_sid)
        emit('trade_executed', {'new_currency': int(lado_p.get('currency', 0))}, room=lado_sid)
    del trades[trade_id]

@socketio.on('trade_lock_in')
def handle_trade_lock_in(data):
    sid = request.sid
    if sid not in online_players: return
    trade, trade_id = _get_trade(sid)
    if not trade or not trade['ready'].get(sid): return

    # Revalida a própria oferta antes de travar - pode ter mudado desde o
    # último offer_update (deletou item, gastou moeda em outro lugar, etc).
    if _validar_oferta(sid, trade['offers'].get(sid, {})) is None:
        _remover_do_trade(sid, motivo="invalid_offer")
        return

    trade['locked'][sid] = True
    _emit_trade_lock_state(trade_id)
    if trade['locked'][trade['a_sid']] and trade['locked'][trade['b_sid']]:
        _executar_trade(trade_id)

@socketio.on('trade_cancel')
def handle_trade_cancel(data):
    sid = request.sid
    if sid not in online_players: return
    _remover_do_trade(sid, motivo="cancelled_by_peer")

ICONES_AMIZADE_VALIDOS = {'pk': 'icon_pk', 'guild': 'icon_guild', 'seller': 'icon_seller'}

@socketio.on('toggle_friend')
def handle_toggle_friend(data):
    conn = None
    try:
        sid = request.sid
        if sid not in online_players: return
        owner_name = online_players[sid].get('name', '')
        friend_name = str(data.get('friend_name', '')).strip()
        if not friend_name or friend_name == owner_name: return

        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("SELECT 1 FROM characters WHERE name = %s", (friend_name,))
        if not c.fetchone(): return

        c.execute("SELECT 1 FROM friendships WHERE owner_name = %s AND friend_name = %s", (owner_name, friend_name))
        ja_amigo = c.fetchone() is not None

        if ja_amigo:
            c.execute("DELETE FROM friendships WHERE owner_name = %s AND friend_name = %s", (owner_name, friend_name))
        else:
            c.execute("INSERT INTO friendships (owner_name, friend_name) VALUES (%s, %s)", (owner_name, friend_name))
        conn.commit()
        c.close()

        emit('friend_status', {'friend_name': friend_name, 'is_friend': not ja_amigo}, room=sid)
    except Exception: traceback.print_exc()
    finally:
        if conn: db_pool.putconn(conn)

@socketio.on('set_friend_icon')
def handle_set_friend_icon(data):
    conn = None
    try:
        sid = request.sid
        if sid not in online_players: return
        owner_name = online_players[sid].get('name', '')
        friend_name = str(data.get('friend_name', '')).strip()
        icon = str(data.get('icon', ''))
        coluna = ICONES_AMIZADE_VALIDOS.get(icon)
        if not friend_name or not coluna: return

        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute(f"SELECT {coluna} FROM friendships WHERE owner_name = %s AND friend_name = %s", (owner_name, friend_name))
        row = c.fetchone()
        if not row: return

        novo_valor = not bool(row[0])
        c.execute(f"UPDATE friendships SET {coluna} = %s WHERE owner_name = %s AND friend_name = %s", (novo_valor, owner_name, friend_name))
        conn.commit()
        c.close()

        emit('friend_icon_updated', {'friend_name': friend_name, 'icon': icon, 'value': novo_valor}, room=sid)
    except Exception: traceback.print_exc()
    finally:
        if conn: db_pool.putconn(conn)

@socketio.on('add_friend_by_name')
def handle_add_friend_by_name(data):
    conn = None
    try:
        sid = request.sid
        if sid not in online_players: return
        owner_name = online_players[sid].get('name', '')
        friend_name = str(data.get('friend_name', '')).strip()

        if not friend_name:
            emit('friend_add_result', {'success': False, 'friend_name': friend_name, 'reason': 'not_found'}, room=sid)
            return
        if friend_name == owner_name:
            emit('friend_add_result', {'success': False, 'friend_name': friend_name, 'reason': 'self'}, room=sid)
            return

        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("SELECT 1 FROM characters WHERE name = %s", (friend_name,))
        if not c.fetchone():
            c.close()
            emit('friend_add_result', {'success': False, 'friend_name': friend_name, 'reason': 'not_found'}, room=sid)
            return

        c.execute("SELECT 1 FROM friendships WHERE owner_name = %s AND friend_name = %s", (owner_name, friend_name))
        if not c.fetchone():
            c.execute("INSERT INTO friendships (owner_name, friend_name) VALUES (%s, %s)", (owner_name, friend_name))
            conn.commit()
        c.close()

        emit('friend_add_result', {'success': True, 'friend_name': friend_name, 'reason': ''}, room=sid)
        emit('friend_status', {'friend_name': friend_name, 'is_friend': True}, room=sid)
    except Exception: traceback.print_exc()
    finally:
        if conn: db_pool.putconn(conn)

# ---------------------------------------------------------------------------
# RANKING (aba Rank do livro). Top RANKING_TOP de cada categoria, montado do
# banco + o estado em memoria de quem esta online (mais novo que o save). Fica
# em cache RANKING_CACHE_SEG (1 hora) pra 300 players abrindo a aba nao
# virarem 300 consultas ao banco.
# ---------------------------------------------------------------------------
RANKING_TOP = 50
RANKING_CACHE_SEG = 3600.0  # atualiza 1x por hora (alivia o servidor/banco)
# categoria -> chave em skills (None = level/exp)
RANKING_CATEGORIAS = {"level": None, "defense": "defense", "magic": "magic",
                      "focus": "distance", "musicality": "musicality", "melee": "melee"}
_ranking_cache = {}  # categoria -> (quando, lista)
# Skill de classe: so' entra quem e' dessa classe (Level e Defense: todos).
RANKING_CLASSE = {"magic": "Mage", "focus": "Ranger", "musicality": "Bard", "melee": "Knight"}
# Personagens de teste (scripts de party/stress etc.) que nao entram no
# ranking: PTMember..., PTLeader..., PTSolo..., SwA/SwB seguidos de numeros.
# Sem diferenciar maiuscula/minuscula.
RE_RANKING_IGNORAR = re.compile(r"^(ptmember|ptleader|ptsolo|sw[ab]\s*\d)", re.IGNORECASE)

def _fora_do_ranking(nome):
    return bool(RE_RANKING_IGNORAR.match(str(nome)))

def _skill_nivel(skills, chave):
    try: return int((skills or {}).get(chave, 10))
    except (TypeError, ValueError): return 10

def _montar_ranking(categoria):
    chave = RANKING_CATEGORIAS[categoria]
    conn = None
    por_nome = {}
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("SELECT name, class_name, level, exp, skills FROM characters WHERE deleted_at IS NULL")
        for nome, classe, level, exp, skills_txt in c.fetchall():
            try: skills = json.loads(skills_txt) if skills_txt else {}
            except (TypeError, ValueError): skills = {}
            por_nome[nome] = {"name": nome, "class": classe or "Knight", "level": level or 1,
                              "exp": exp or 0, "skills": skills if isinstance(skills, dict) else {}}
        c.close()
    finally:
        if conn: db_pool.putconn(conn)
    # Quem esta online: valores da memoria (o save pode estar na fila).
    for p in list(online_players.values()):
        nome = p.get('name')
        if not nome: continue
        por_nome[nome] = {"name": nome, "class": p.get('class_name', 'Knight'), "level": p.get('level', 1),
                          "exp": p.get('exp', 0), "skills": p.get('skills') or {}}
    lista = []
    so_classe = RANKING_CLASSE.get(categoria)
    for d in por_nome.values():
        if _fora_do_ranking(d["name"]): continue
        if so_classe and str(d["class"] or "Knight").strip().lower() != so_classe.lower(): continue
        e = {"name": d["name"], "class": d["class"], "level": int(d["level"] or 1), "exp": int(d["exp"] or 0)}
        if chave: e["value"] = _skill_nivel(d["skills"], chave)
        lista.append(e)
    if chave: lista.sort(key=lambda e: (-e["value"], -e["level"], e["name"].lower()))
    else: lista.sort(key=lambda e: (-e["level"], -e["exp"], e["name"].lower()))
    return lista[:RANKING_TOP]

@socketio.on('get_ranking')
def handle_get_ranking(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        categoria = str((data or {}).get('category', 'level')).lower() if isinstance(data, dict) else 'level'
        if categoria not in RANKING_CATEGORIAS: categoria = 'level'
        agora = time.time()
        quando, lista = _ranking_cache.get(categoria, (0.0, None))
        if lista is None or agora - quando > RANKING_CACHE_SEG:
            lista = _montar_ranking(categoria)
            _ranking_cache[categoria] = (agora, lista)
        emit('ranking', {"category": categoria, "entries": lista, "top": RANKING_TOP}, room=sid)
    except Exception: traceback.print_exc()

@socketio.on('get_friends_list')
def handle_get_friends_list(data):
    conn = None
    try:
        sid = request.sid
        if sid not in online_players: return
        owner_name = online_players[sid].get('name', '')

        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("""SELECT f.friend_name, f.icon_pk, f.icon_guild, f.icon_seller, ch.class_name, ch.skins
                     FROM friendships f LEFT JOIN characters ch ON ch.name = f.friend_name
                     WHERE f.owner_name = %s""", (owner_name,))
        rows = c.fetchall()
        c.close()

        friends = [{
            'name': row[0],
            'icon_pk': bool(row[1]),
            'icon_guild': bool(row[2]),
            'icon_seller': bool(row[3]),
            'class_name': row[4] or 'Knight',
            # Skin (aparencia) pra mostrar o amigo mesmo offline (janela do
            # jogador / aba de PV). Mesma validacao do jogo.
            'skins': validar_skins(json.loads(row[5]) if row[5] else {}, row[4] or 'Knight'),
            'online': row[0] in players_by_name,
        } for row in rows]

        emit('friends_list', {'friends': friends}, room=sid)
    except Exception: traceback.print_exc()
    finally:
        if conn: db_pool.putconn(conn)

# --- BACKGROUND TASKS ---
# Regen a cada 5s: 1 ponto por REGEN_POR_PONTOS de vida/mana MÁXIMA (mínimo
# 1). Assim quem tem mais HP (knight) recupera mais HP e quem tem mais mana
# (mage) mais mana - antes era igual pra todo mundo (só pelo level) e o mage,
# com pouco HP, recuperava proporcionalmente demais e tankava muito.
REGEN_POR_PONTOS = 50.0

def get_regen_amount(maximo):
    return max(1.0, math.floor(float(maximo) / REGEN_POR_PONTOS))

# =========================================================================
# FOME (Fullness) e BATTLE
# =========================================================================
# Fullness fica em skills['fullness'] (0..FULLNESS_MAX, salvo junto com as
# skills; personagem sem o campo começa cheio). Comer um item com "fullness"
# no ITEM_DB enche a barra; ela cai FULLNESS_DECAI_POR a cada
# FULLNESS_DECAI_SEG online. Abaixo de FOME_LIMITE o player está com fome:
# ícone do lado do HP, sem regen de HP/MP, FOME_MULT_DANO no dano e
# FOME_MULT_VELOCIDADE na velocidade (aplicada no client).
FULLNESS_MAX = 50
FULLNESS_DECAI_SEG = 60.0
FULLNESS_DECAI_POR = 1
FOME_LIMITE = 10
FOME_MULT_DANO = 0.9
FOME_MULT_VELOCIDADE = 0.9

def esta_com_fome(p):
    return obter_fullness(p) < FOME_LIMITE

def obter_fullness(p):
    try: return max(0, min(FULLNESS_MAX, int(p.get('skills', {}).get('fullness', FULLNESS_MAX))))
    except (TypeError, ValueError): return 0

def _definir_fullness(p, valor):
    skills = p.get('skills')
    if not isinstance(skills, dict): skills = {}; p['skills'] = skills
    skills['fullness'] = max(0, min(FULLNESS_MAX, int(valor)))

@socketio.on('eat_food')
def handle_eat_food(data):
    try:
        sid = request.sid
        p = online_players.get(sid)
        if p is None or p.get('is_dead') or not isinstance(data, dict): return
        inst = encontrar_instancia(p.get('inventory', []), str(data.get('instance_id', '')))
        if inst is not None: _comer(sid, p, inst)
    except Exception: traceback.print_exc()

# Textinho de acao em cima da cabeca do player (igual OT de Tibia), pra todo
# mundo que esta perto: comer ("Om Noom"), e depois magias, pocoes, quests...
# cor = hex RGB (laranja por padrao).
def texto_de_acao(p, texto, cor="ff9a1f"):
    if not p or not p.get('room'): return
    emit_area('action_text', {'name': p.get('name', ''), 'text': str(texto)[:40], 'color': cor}, p.get('room'))

def _comer(sid, p, inst):
    # Come 1 unidade da instância (botão Eat na bag ou atalho da hotbar).
    inventario = p.get('inventory', [])
    try: enche = int(ITEM_DB.get(inst.get('item'), {}).get('fullness', 0))
    except (TypeError, ValueError): enche = 0
    if enche <= 0: return
    atual = obter_fullness(p)
    # So' recusa com a barra cheia; perto de cheia come e enche so' o que
    # falta (ex: 49/50 + cookie de 5 = 50), sem nunca passar de FULLNESS_MAX.
    if atual >= FULLNESS_MAX:
        emit('food_result', {'ok': False, 'reason': 'full'}, room=sid)
        return
    qty = int(inst.get('qty', 1))
    if qty > 1: inst['qty'] = qty - 1
    else: inventario.remove(inst)
    _definir_fullness(p, min(FULLNESS_MAX, atual + enche))
    p['_fullness_acc'] = 0.0  # acabou de comer: o proximo "tique" de fome recomeça
    _queue_save(p)
    emit('food_result', {'ok': True, 'fullness': obter_fullness(p)}, room=sid)
    texto_de_acao(p, "*Om Noom*")  # barulhos entre asteriscos
    emit('inventory_synced', {'inventory': ordenar_favoritos_primeiro(inventario), 'equipped_items': p.get('equipped_items', {})}, room=sid)
    emit('sync_stats', _montar_payload_sync_stats(p), room=sid)

# ---- Barra de atalhos (hotbar) ----
# 9 slots livres: cada um guarda o CAMINHO de um item usável (comida; poção
# quando existir) ou, no futuro, o id de uma magia. Usar um item pega a 1a
# unidade dele na bag. Formato salvo: {"slots": [9 strings]}.
# No celular: 1-6 nos 3 dragkeys (2 cada), 7-9 fixos no canto (comida/poção).
HOTBAR_SLOTS = 9

def item_usavel_no_atalho(item_path):
    # Por enquanto só comida; poção (e magia) entram aqui quando existirem.
    dados = ITEM_DB.get(item_path, {})
    try: return int(dados.get('fullness', 0)) > 0
    except (TypeError, ValueError): return False

def normalizar_hotbar(hb):
    hb = hb if isinstance(hb, dict) else {}
    lista = hb.get('slots')
    if not isinstance(lista, list):
        # Formato antigo (4 magias + 4 itens): vira os 8 slots em sequência.
        antigas = hb.get('spells') if isinstance(hb.get('spells'), list) else []
        itens = hb.get('items') if isinstance(hb.get('items'), list) else []
        lista = (antigas + [''] * 4)[:4] + (itens + [''] * 4)[:4] if (antigas or itens) else []
    lista = [(v if isinstance(v, str) and item_usavel_no_atalho(v) else '') for v in lista[:HOTBAR_SLOTS]]
    return {'slots': lista + [''] * (HOTBAR_SLOTS - len(lista))}

def _indice_hotbar(data):
    try: i = int(data.get('index', -1))
    except (TypeError, ValueError): return None
    return i if 0 <= i < HOTBAR_SLOTS else None

@socketio.on('set_hotbar')
def handle_set_hotbar(data):
    try:
        sid = request.sid
        p = online_players.get(sid)
        if p is None or not isinstance(data, dict): return
        i = _indice_hotbar(data)
        if i is None: return
        valor = data.get('value') if isinstance(data.get('value'), str) else ''
        hb = normalizar_hotbar(p.get('hotbar'))
        hb['slots'][i] = valor
        p['hotbar'] = normalizar_hotbar(hb)  # descarta o que não pode ir ali
        _queue_save(p)
        emit('hotbar_synced', {'hotbar': p['hotbar']}, room=sid)
    except Exception: traceback.print_exc()

@socketio.on('use_hotbar')
def handle_use_hotbar(data):
    try:
        sid = request.sid
        p = online_players.get(sid)
        if p is None or p.get('is_dead') or not isinstance(data, dict): return
        i = _indice_hotbar(data)
        if i is None: return
        caminho = normalizar_hotbar(p.get('hotbar'))['slots'][i]
        if not caminho: return
        inst = next((it for it in p.get('inventory', []) if it.get('item') == caminho), None)
        if inst is None:
            emit('food_result', {'ok': False, 'reason': 'none'}, room=sid)
            return
        _comer(sid, p, inst)
    except Exception: traceback.print_exc()

# Battle: entra quando um mob mira o player (ou o player bate num mob) e sai
# BATTLE_SEG depois da última vez. Desconectar em battle deixa o corpo no
# jogo (continua apanhando, pode morrer) até o battle acabar - não dá pra
# fugir de luta/PvP fechando o jogo.
BATTLE_SEG = 30.0
# Sem renovar o battle por esse tempo = parou de lutar. Aí a contagem começa
# do 30 cheio (o battle acaba 30s depois disso, não do último golpe).
BATTLE_PARADO_SEG = 3.0  # > ATAQUE_COOLDOWN_SEG (o auto-ataque do player renova a cada ~2.4s)

def marcar_batalha(sid, p, now=None):
    if p is None or p.get('is_dead'): return
    now = time.time() if now is None else now
    p['battle_until'] = now + BATTLE_SEG
    # Avisa só quando muda: entrou em battle, ou voltou a lutar no meio da
    # contagem. Enquanto luta o client fica travado em 30 sem aviso nenhum;
    # a contagem regressiva começa quando battle_loop vê que parou.
    avisar = not p.get('em_batalha') or p.get('_battle_contando')
    p['em_batalha'] = True
    p['_battle_contando'] = False
    if avisar and not p.get('corpo_ausente'):
        socketio.emit('battle_state', {'in_battle': True, 'counting': False, 'seconds': BATTLE_SEG}, room=sid)

def sair_da_batalha(sid, p):
    # Morreu: o battle acaba na hora (o corpo de quem deslogou sai do jogo).
    if p is None: return
    estava = p.get('em_batalha')
    p['em_batalha'] = False
    p['_battle_contando'] = False
    p['battle_until'] = 0
    if p.get('corpo_ausente'):
        _remover_corpo_ausente(sid, p)
    elif estava:
        socketio.emit('battle_state', {'in_battle': False}, room=sid)

def _remover_corpo_ausente(sid, p):
    # Battle acabou pro corpo de quem já desconectou: agora sai de verdade.
    _remover_da_visao(sid)
    _queue_save(p)
    p_name = p.get('name', '')
    socketio.emit('player_left', {"name": p_name})
    if players_by_name.get(p_name) == sid: players_by_name.pop(p_name, None)
    online_players.pop(sid, None)

def battle_loop():
    while True:
        socketio.sleep(0.5)
        now = time.time()
        for sid, p in list(online_players.items()):
            try:
                if not p.get('em_batalha'): continue
                restante = p.get('battle_until', 0) - now
                if restante > 0:
                    # Nada renovou o battle por BATTLE_PARADO_SEG (sem mob
                    # focando, sem atacar): começa a contagem no client.
                    if (not p.get('_battle_contando') and restante < BATTLE_SEG - BATTLE_PARADO_SEG
                            and not p.get('corpo_ausente')):
                        p['_battle_contando'] = True
                        p['battle_until'] = now + BATTLE_SEG
                        socketio.emit('battle_state', {'in_battle': True, 'counting': True, 'seconds': BATTLE_SEG}, room=sid)
                    continue
                p['em_batalha'] = False
                p['_battle_contando'] = False
                if p.get('corpo_ausente'): _remover_corpo_ausente(sid, p)
                else: socketio.emit('battle_state', {'in_battle': False}, room=sid)
            except Exception:
                traceback.print_exc()

def regen_loop():
    while True:
        socketio.sleep(5.0) 
        for sid, p in list(online_players.items()):
            # try por player: uma exceção aqui não pode matar o loop de todo mundo.
            try:
                if p.get('is_dead'): continue
                max_hp, max_mp = calcular_max_vitais(p)
                current_hp = float(p.get('current_hp', -1))
                current_mp = float(p.get('current_mp', -1))

                if current_hp < 0: current_hp = max_hp; p['current_hp'] = current_hp
                if current_mp < 0: current_mp = max_mp; p['current_mp'] = current_mp
                if current_hp <= 0: continue

                # Fome: cai FULLNESS_DECAI_POR a cada FULLNESS_DECAI_SEG.
                if obter_fullness(p) > 0:
                    p['_fullness_acc'] = p.get('_fullness_acc', 0.0) + 5.0
                    if p['_fullness_acc'] >= FULLNESS_DECAI_SEG:
                        p['_fullness_acc'] = 0.0
                        _definir_fullness(p, obter_fullness(p) - FULLNESS_DECAI_POR)
                        if not p.get('corpo_ausente'):
                            socketio.emit('sync_stats', _montar_payload_sync_stats(p), room=sid)

                # Com fome: sem regen de HP/MP.
                if esta_com_fome(p): continue
                curou = False

                if current_hp < max_hp: current_hp = min(max_hp, current_hp + get_regen_amount(max_hp)); p['current_hp'] = current_hp; curou = True
                if current_mp < max_mp: current_mp = min(max_mp, current_mp + get_regen_amount(max_mp)); p['current_mp'] = current_mp; curou = True

                if curou:
                    socketio.emit('sync_vitals', {'current_hp': current_hp, 'current_mp': current_mp,
                                                  'max_hp': max_hp, 'max_mp': max_mp}, room=sid)
                    broadcast_hp(sid, p)
            except Exception:
                traceback.print_exc()
                    
def autosave_loop():
    while True:
        socketio.sleep(120.0) 
        for sid, p in list(online_players.items()):
            _queue_save(p)

def loot_cleanup_loop():
    while True:
        socketio.sleep(60.0)
        now = time.time()
        stale = [l_id for l_id, l_data in list(ground_loot.items()) if now - l_data.get('criado_em', now) > LOOT_EXPIRA_SEG]
        for l_id in stale:
            ground_loot.pop(l_id, None)

# Convite de trade expira sozinho se o alvo não aceitar em TRADE_INVITE_TIMEOUT_SEG -
# tick de 1s (igual mob_cleanup_loop) pra não deixar o convite "pendurado"
# por muito mais tempo que o prometido.
def trade_invite_cleanup_loop():
    while True:
        socketio.sleep(1.0)
        now = time.time()
        stale = [par for par, criado in list(trade_pending_invites.items()) if now - criado > TRADE_INVITE_TIMEOUT_SEG]
        for (inviter_sid, alvo_sid) in stale:
            _cancelar_convite_trade(inviter_sid, alvo_sid, 'timeout')

MOB_AVISO_SPAWN_SEG = 6.0  # SpawnWarning no client antes do mob renascer

def mob_cleanup_loop():
    while True:
        # Tick curto: com 60s o respawn de 120s acontecia entre 120 e 180s.
        socketio.sleep(1.0)
        now = time.time()
        for m_id, m_data in list(active_mobs.items()):
            if m_data.get('hp', 1) > 0: continue
            if 'died_at' not in m_data:
                m_data['died_at'] = now
            falta = m_data.get('respawn_seg', MOB_RESPAWN_SEG) - (now - m_data['died_at'])
            # Aviso de spawn: ~6s antes, ja' sorteia o SQM onde ele vai nascer e
            # avisa a area (o client toca o SpawnWarning nesse SQM).
            if falta <= MOB_AVISO_SPAWN_SEG and 'nascimento_reservado' not in m_data:
                reservado = _tile_de_nascimento(m_id, m_data) or m_data.get('spawn')
                if reservado is not None:
                    m_data['nascimento_reservado'] = reservado
                    ax, ay = centro_tile(reservado)
                    emit_area('mob_spawn_warning', {'mob_id': m_id, 'pos_x': ax, 'pos_y': ay,
                                                    'seconds': max(0.0, falta)},
                              get_chunk(ax, ay, MOB_FLOOR))
            if falta > 0: continue
            # Renasce no spawn com tudo zerado.
            m_data['hp'] = m_data.get('max_hp', 40)
            m_data.pop('died_at', None)
            m_data['returning'] = False
            m_data['target_sid'] = None
            m_data['path'] = None
            m_data['alcance'] = {}
            m_data['sem_caminho'] = 0.0
            m_data['move_until'] = 0.0
            m_data['next_attack'] = 0.0
            m_data['direction'] = 'down'
            m_data['dmg_tracker'] = {}
            # Renasce num SQM sorteado dentro do spawn_range do ponto do Tiled;
            # esse SQM vira a "casa" dele nessa vida (pra onde volta/leash).
            # Usa o SQM ja' avisado (se ninguem parou em cima dele nesse meio tempo).
            nascimento = m_data.pop('nascimento_reservado', None)
            if nascimento is None or nascimento in _tiles_ocupados(excluir_mob=m_id, sala=m_data.get('room')):
                nascimento = _tile_de_nascimento(m_id, m_data)
            if nascimento is not None:
                m_data['spawn'] = nascimento
            if m_data.get('spawn') is not None:
                m_data['pos_x'], m_data['pos_y'] = centro_tile(m_data['spawn'])
                m_data['room'] = get_chunk(m_data['pos_x'], m_data['pos_y'], MOB_FLOOR)
            m_room = m_data.get('room')
            if m_room:
                emit_area('mob_respawn', {'mob_id': m_id}, m_room)
                emit_area('mob_pos', payload_mob_pos(m_id, m_data), m_room)
            else:
                socketio.emit('mob_respawn', {'mob_id': m_id})

carregar_mapas()

# ---- Mapa lido pelo proprio servidor (World.tmx) ----
# Com o .tmx (e os .tsx) na pasta do servidor, a colisao, os mobs, os NPCs e
# o spawn saem DAQUI - o que o client manda no register_map/map_grid e'
# ignorado (so' serve pra conferir se a versao do mapa bate). Sem o arquivo,
# cai no modo antigo (1o registro travado, ver handle_register_map).
MAPA_TMX = os.getenv('MAPA_TMX', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'maps', 'World.tmx'))
MAPA_ID_SERVIDOR = os.getenv('MAPA_ID', 'res://Inverted Realms.scn')  # igual WorldScreen.MAP_ID_SERVIDOR
MAPAS_DO_SERVIDOR = set()
_fp_divergente_avisado = set()

def carregar_mapa_do_servidor():
    if not os.path.exists(MAPA_TMX):
        print(f"[MAPA] {MAPA_TMX} nao encontrado: usando o mapa enviado pelo 1o client (modo antigo).")
        return
    try:
        import mapa_tiled
        dados = mapa_tiled.carregar_mapa(MAPA_TMX)
    except Exception:
        traceback.print_exc()
        print("[MAPA] Falha ao ler o .tmx: usando o mapa enviado pelo 1o client (modo antigo).")
        return
    grade = dados['grade']
    mapas_colisao[MAPA_ID_SERVIDOR] = grade
    conteudo_mapas[MAPA_ID_SERVIDOR] = {'fp': grade['fp'], 'mobs': dados['mobs'],
                                        'npcs': dados['npcs'], 'spawn': dados['spawn']}
    velocidade_tiles[MAPA_ID_SERVIDOR] = dados.get('velocidades', {})
    areas_de_quest[MAPA_ID_SERVIDOR] = dados.get('quest_areas', [])
    zonas_protegidas[MAPA_ID_SERVIDOR] = dados.get('protection_zone', set())
    teleportes[MAPA_ID_SERVIDOR] = dados.get('teleports', {})
    MAPAS_DO_SERVIDOR.add(MAPA_ID_SERVIDOR)
    for m in active_mobs.values():
        m['path'] = None
        m['alcance'] = {}
    print(f"[MAPA] {os.path.basename(MAPA_TMX)} lido pelo servidor: grade {grade['w']}x{grade['h']} "
          f"(fp {grade['fp'][:8]}), {len(dados['mobs'])} mob(s), {len(dados['npcs'])} NPC(s), spawn {dados['spawn']}, "
          f"{len(velocidade_tiles[MAPA_ID_SERVIDOR])} SQM(s) com speed_modifier, "
          f"{len(zonas_protegidas[MAPA_ID_SERVIDOR])} SQM(s) de Protection Zone, "
          f"{len(teleportes[MAPA_ID_SERVIDOR])} SQM(s) de teleport")

carregar_mapa_do_servidor()

# Muda a cada atualizacao do servidor - aparece no console ao iniciar, pra
# confirmar qual versao esta rodando de verdade.
VERSAO_SERVIDOR = "2026-10-08 teleports"
print(f"[SERVIDOR] Versao {VERSAO_SERVIDOR} (client exigido: {SERVER_VERSION})")
socketio.start_background_task(regen_loop)
socketio.start_background_task(battle_loop)
socketio.start_background_task(autosave_loop)
socketio.start_background_task(loot_cleanup_loop)
socketio.start_background_task(trade_invite_cleanup_loop)
socketio.start_background_task(mob_cleanup_loop)
socketio.start_background_task(mob_ai_loop)
socketio.start_background_task(npc_ai_loop)
socketio.start_background_task(loop_visao_movimento)
eventlet.spawn(db_writer_worker)

if __name__ == '__main__':
    init_db()
    # debug=False em producao (o debug deixa tudo mais lento e expoe detalhes
    # de erro). Pra desenvolver: DEBUG_SERVIDOR=1 python3 servidor.py
    socketio.run(app, host='0.0.0.0', port=3000, debug=os.getenv('DEBUG_SERVIDOR') == '1')