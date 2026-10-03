import eventlet
eventlet.monkey_patch()

import os
import json
import psycopg2
from psycopg2 import pool
import smtplib
import random
import traceback
import time
import math
import uuid
import base64
import heapq
from email.mime.text import MIMEText
from flask import Flask, request, jsonify
from flask_cors import CORS
from flask_socketio import SocketIO, emit, join_room, leave_room, disconnect
from werkzeug.security import generate_password_hash, check_password_hash
from dotenv import load_dotenv
from eventlet.queue import Queue

load_dotenv()
GMAIL_SENDER = os.getenv("GMAIL_SENDER")
GMAIL_APP_PASSWORD = os.getenv("GMAIL_APP_PASSWORD")
SERVER_VERSION = "v0.1"
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
    "rotworm": {"hp": 50, "xp": 20, "attack": 3.0, "drops": [], "currency": {"min": 5, "max": 10}},
}

NPC_DB = {
    "kharon": {"name": "Kharon"},
}
NPC_WANDER_RADIUS_SQM = 5
NPC_WANDER_MIN_WAIT = 5.0
NPC_WANDER_MAX_WAIT = 10.0
NPC_STEP_SECONDS = 1.0 / 2.2
NPC_TICK_SECONDS = 0.1

ground_loot = {}
LOOT_EXPIRA_SEG = 300  
# Quanto tempo a bag fica visível no chão (TEMPO_DESPAWN_SEG do loot_bag.gd).
LOOT_BAG_VISIVEL_SEG = 120.0

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
BLOCK_CHANCE = 0.10
DEFENSE_AFK_WINDOW = 12.0
PUNICAO_CLASSE_HP = 1.0
PUNICAO_CLASSE_MP = 1.0
PUNICAO_CLASSE_DANO = 1
PUNICAO_CLASSE_SPEED = 0.01

online_players = {}
players_by_name = {} # O(1) Lookup Table (Otimização CPU)
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
    "res://sprites/items/Mage/Weapons/StarterStaff.tres": {"name": "Apprentice Staff", "type": "Staff", "req_level": 0, "req_class": "Mage", "bonus_damage": 1, "defense": 0, "stamina": 0, "mana": 10, "fourth_stat_type": "Magic", "fourth_stat_value": 2, "cap": 5.0},
    "res://sprites/items/Mage/SecondHand/StarterBook.tres": {"name": "Apprentice Book", "type": "Book", "req_level": 0, "req_class": "Mage", "bonus_damage": 0, "defense": 0, "stamina": 0, "mana": 10, "fourth_stat_type": "Magic", "fourth_stat_value": 5, "cap": 5.0},
    "res://sprites/items/Ranger/Weapons/StarterBow.tres": {"name": "Wooden Bow", "type": "Bow", "req_level": 0, "req_class": "Ranger", "bonus_damage": 1, "defense": 0, "stamina": 5, "mana": 0, "fourth_stat_type": "Focus", "fourth_stat_value": 5, "cap": 5.0},
    "res://sprites/items/Ranger/SecondHand/StarterArrow.tres": {"name": "Wooden Arrow", "type": "Arrow", "req_level": 0, "req_class": "Ranger", "bonus_damage": 0, "defense": 0, "stamina": 0, "mana": 0, "fourth_stat_type": "Focus", "fourth_stat_value": 5, "ammo": True, "max_stack": 9999, "cap": 0.1},
}

SLOT_MUNICAO = "Hand"

# Campos do ITEM_DB que o client usa pra exibir os itens (nome, tipo, level,
# stats e slot) - mandado no sync_local_player, assim o client nao precisa
# de uma copia propria e o que aparece na tela e' sempre o valor real.
CAMPOS_ITEM_CLIENTE = ("name", "type", "req_level", "req_class", "bonus_damage", "defense",
                       "stamina", "mana", "fourth_stat_type", "fourth_stat_value", "ammo")

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
STARTING_INVENTORY = {"Knight": [], "Ranger": [], "Mage": [], "Bard": []}

def montar_kit_inicial(class_name):
    equipped = {}
    for entrada in STARTING_EQUIPMENT.get(class_name, []):
        slot = entrada.get("slot")
        item_path = validate_item(entrada.get("item"))
        if item_path and slot in SLOTS_VALIDOS: equipped[slot] = criar_instancia_item(item_path, entrada.get("qty", 1))
    inventory = []
    for item_path in STARTING_INVENTORY.get(class_name, []):
        item_path = validate_item(item_path)
        if item_path: inventory.append(criar_instancia_item(item_path))
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

def obter_max_stack(item_path):
    if not eh_municao(item_path): return 1
    return max(1, int(ITEM_DB.get(item_path, {}).get("max_stack", MAX_STACK_MUNICAO_PADRAO)))

def obter_cap_unitario_item(item_path):
    return float(ITEM_DB.get(item_path, {}).get("cap", 0.0))

def obter_cap_instancia(inst):
    if not isinstance(inst, dict): return 0.0
    item_path = inst.get('item')
    cap_unit = obter_cap_unitario_item(item_path)
    if cap_unit <= 0: return 0.0
    if eh_municao(item_path):
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
    if eh_municao(item_path): inst["qty"] = max(1, min(int(qty), obter_max_stack(item_path)))
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
        if eh_municao(item_path):
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
    
    if player_class == "Knight":
        dmg += skills.get("melee", 10) * 0.5
        defense += skills.get("defense", 10) * 1.0
    elif player_class == "Ranger":
        dmg += skills.get("distance", 10) * 0.75
        defense += skills.get("defense", 10) * 0.75
    elif player_class in ["Mage", "Bard"]:
        main_skill = skills.get("magic", 10) if player_class == "Mage" else skills.get("musicality", 10)
        dmg += main_skill * 1.0
        defense += skills.get("defense", 10) * 0.5
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

    return {
        'skills': skills, 'base_damage': new_dmg, 'defense_value': new_def,
        'level': level, 'exp': p.get('exp', 0), 'kills': p.get('kills', 0),
        'hp_bonus': hp_bonus, 'mana_bonus': mp_bonus, 'speed_multiplier': speed_multiplier,
        'classe_penalizada': punido,
        'cap_atual': calcular_cap_usado(p), 'cap_maximo': calcular_cap_maximo(level),
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
MOB_COOLDOWN_PADRAO = 3.0
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
        for npc_id, npc in list(active_npcs.items()):
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

def _tiles_ocupados(excluir_mob=None, excluir_sid=None):
    ocupados = set()
    for o_id, o in active_mobs.items():
        if o_id == excluir_mob or o.get('hp', 1) <= 0 or 'pos_x' not in o: continue
        ocupados.add(tile_do_mob(o))
    for o_sid, p in online_players.items():
        if o_sid == excluir_sid or p.get('is_dead'): continue
        if int(p.get('floor', 1) or 1) != MOB_FLOOR: continue
        ocupados.add(tile_de(p.get('pos_x', 0), p.get('pos_y', 0)))
    return ocupados

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

def _mob_dar_passo(mob_id, m, destino, now, ate_adjacente=False):
    # ate_adjacente: perseguindo um player (para em volta dele, ver
    # astar_ate_adjacente). Sem ele vai até o próprio destino (volta pro spawn).
    grade = mapas_colisao.get(m.get('mapa'))
    if grade is None: return False
    origem = tile_do_mob(m)
    ocupados = _tiles_ocupados(excluir_mob=mob_id)
    cache = m.get('path')
    caminho = None
    if cache and cache['fim'] == destino and cache.get('adj') == ate_adjacente and cache['caminho'] and cache['caminho'][0] == origem and now - cache['t'] < PATH_RECALC_SEG:
        caminho = cache['caminho']
    else:
        if ate_adjacente:
            caminho = astar_ate_adjacente(grade, origem, destino, ocupados)
        else:
            caminho = astar(grade, origem, destino, ocupados - {destino})
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
    passo = 1.0 / max(0.1, float(m.get('speed', MOB_SPEED_PADRAO)))
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
    mob_attack_damage = MOB_DB.get(m.get('type_id'), {}).get("attack", 3.0)
    p_class = target_player.get('class_name', 'Knight')
    bonus_itens = somar_bonus_combate_equipados(target_player.get('equipped_items', {}))
    _, def_value = calc_player_stats(p_class, target_player.get('level', 1), target_player.get('skills', {}), bonus_def=bonus_itens['bonus_defense'])
    dano_final = 0 if random.random() < BLOCK_CHANCE else max(1.0, mob_attack_damage - def_value)

    max_hp_alvo, _ = calcular_max_vitais(target_player)
    hp_atual = float(target_player.get('current_hp', -1))
    if hp_atual < 0: hp_atual = max_hp_alvo
    hp_atual = max(0.0, hp_atual - dano_final)
    target_player['current_hp'] = hp_atual
    target_player['_ultimo_hp_broadcast'] = [hp_atual, max_hp_alvo]

    emit_area('player_damaged', {'target_player': target_name, 'damage': dano_final, 'new_hp': hp_atual,
                                 'max_hp': max_hp_alvo, 'hit_type': m.get('hit_effect', 'physical_hit'),
                                 'attacker_mob_id': mob_id}, target_player.get('room'))
    if hp_atual <= 0:
        target_player['is_dead'] = True
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
        if alvo is None:  # morreu, saiu, desconectou
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
            _mob_encarar(mob_id, m, origem, t_alvo)
            return
        if _alcanca(m, alvo_sid, origem, t_alvo, now):
            m['sem_caminho'] = 0.0
            if not _mob_dar_passo(mob_id, m, t_alvo, now, ate_adjacente=True):
                _mob_encarar(mob_id, m, origem, t_alvo)  # bloqueado por criatura: espera olhando pro alvo
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
    for sid in list(online_players.keys()):
        p = _player_alvo_valido(sid, m)
        if p is None: continue
        d = _dist_px(m, p)
        if d > melhor_dist: continue
        t_p = tile_de(p.get('pos_x', 0), p.get('pos_y', 0))
        if _adjacente(origem, t_p) or _alcanca(m, sid, origem, t_p, now):
            melhor, melhor_dist = sid, d
    if melhor is not None:
        m['target_sid'] = melhor
        m['sem_caminho'] = 0.0

def mob_focar_agressor(mob_id, m, sid):
    # Quem bate no mob vira o alvo se ele estava sem alvo ou voltando pra casa.
    if m.get('hp', 1) <= 0: return
    if m.get('target_sid') is not None and not m.get('returning'): return
    p = _player_alvo_valido(sid, m)
    if p is None or _dist_px(m, p) > (DETECCAO_SQM + PERSISTE_SQM) * TILE: return
    m['target_sid'] = sid
    m['sem_caminho'] = 0.0
    if m.get('returning'):
        m['returning'] = False
        _mob_emitir_estado(mob_id, m)

def mob_ai_loop():
    while True:
        socketio.sleep(MOB_TICK_SEG)
        now = time.time()
        for mob_id, m in list(active_mobs.items()):
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
        'time_played': tempo_jogado,
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
                    time_played = %s, npc_dialogue_state = %s
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
                player_data.get('user_id'), player_data.get('name')
            )
            c.execute(query, params)
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
    dados = request.get_json()
    if dados.get('version', '') == SERVER_VERSION: return jsonify({"valid": True, "mensagem": "Version OK."}), 200
    return jsonify({"valid": False, "erro": "Client out of date."}), 426

@app.route('/register', methods=['POST'])
def register():
    dados = request.get_json()
    if not dados or not dados.get('email') or not dados.get('password'): return jsonify({"erro": "Missing fields"}), 400
    email = dados.get('email').strip().lower()
    hashed_password = generate_password_hash(dados.get('password'))
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("INSERT INTO users (email, password) VALUES (%s, %s) RETURNING id", (email, hashed_password))
        new_user_id = c.fetchone()[0]
        conn.commit()
        return jsonify({"mensagem": "Account created!", "user_id": new_user_id, "email": email}), 201
    except psycopg2.IntegrityError: 
        if conn: conn.rollback()
        return jsonify({"erro": "Email in use."}), 409
    except Exception as e: return jsonify({"erro": str(e)}), 500
    finally:
        if conn: db_pool.putconn(conn)

@app.route('/login', methods=['POST'])
def login():
    dados = request.get_json()
    if not dados or not dados.get('email') or not dados.get('password'): return jsonify({"erro": "Missing fields"}), 400
    email = dados.get('email').strip().lower()
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("SELECT id, password FROM users WHERE email = %s", (email,))
        user = c.fetchone()
        if user and check_password_hash(user[1], dados.get('password')):
            return jsonify({"mensagem": "Login ok!", "user_id": user[0], "email": email}), 200
        return jsonify({"erro": "Invalid credentials."}), 401
    except Exception as e: return jsonify({"erro": str(e)}), 500
    finally:
        if conn: db_pool.putconn(conn)

@app.route('/forgot-password', methods=['POST'])
def forgot_password():
    dados = request.get_json()
    if not dados or not dados.get('email'): return jsonify({"erro": "Missing fields"}), 400
    email = dados.get('email').strip().lower()
    codigo = f"{random.randint(0, 999999):06d}"
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
        enviar_codigo_reset(email, codigo)
        return jsonify({"mensagem": "Code sent."}), 200
    except Exception as e:
        if conn: conn.rollback()
        return jsonify({"erro": str(e)}), 500
    finally:
        if conn: db_pool.putconn(conn)

@app.route('/reset-password', methods=['POST'])
def reset_password():
    dados = request.get_json()
    if not dados or not dados.get('email') or not dados.get('code') or not dados.get('new_password'):
        return jsonify({"erro": "Missing fields"}), 400
    email = dados.get('email').strip().lower()
    code = dados.get('code').strip()
    hashed_password = generate_password_hash(dados.get('new_password'))
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("SELECT reset_code, reset_code_expires FROM users WHERE email = %s", (email,))
        user = c.fetchone()
        if not user or user[0] != code or user[1] is None or user[1] < time.time():
            return jsonify({"erro": "Invalid or expired code."}), 400
        c.execute("UPDATE users SET password = %s, reset_code = NULL, reset_code_expires = NULL WHERE email = %s",
                   (hashed_password, email))
        conn.commit()
        return jsonify({"mensagem": "Password updated."}), 200
    except Exception as e:
        if conn: conn.rollback()
        return jsonify({"erro": str(e)}), 500
    finally:
        if conn: db_pool.putconn(conn)

@app.route('/create_character', methods=['POST'])
def create_character():
    dados = request.get_json()
    user_id = dados.get('user_id')
    name = dados.get('name', '').strip()
    class_name = dados.get('class_name', '').strip()
    if not user_id or not name or not class_name: return jsonify({"erro": "Missing fields."}), 400
    if any(banned in name.lower() for banned in BANNED_NAMES): return jsonify({"erro": "Inappropriate name."}), 403
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("SELECT COUNT(*) FROM characters WHERE user_id = %s", (user_id,))
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
    except Exception as e: return jsonify({"erro": str(e)}), 500
    finally:
        if conn: db_pool.putconn(conn)

@app.route('/get_characters', methods=['POST'])
def get_characters():
    user_id = request.get_json().get('user_id')
    if not user_id: return jsonify({"erro": "Missing ID."}), 400
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("SELECT name, class_name, level, exp, pos_x, pos_y, direction, skins, floor, inventory, equipped_items, skills, time_played FROM characters WHERE user_id = %s ORDER BY id ASC", (user_id,))
        char_list = []
        for row in c.fetchall():
            char_list.append({
                "name": row[0], "class_name": row[1], "level": row[2], "exp": row[3] if row[3] else 0,
                "pos_x": row[4], "pos_y": row[5], "direction": row[6],
                "skins": json.loads(row[7]) if row[7] else {},
                "floor": row[8] if row[8] else 1,
                "inventory": normalizar_inventario(json.loads(row[9]) if row[9] else []),
                "equipped_items": normalizar_equipados(json.loads(row[10]) if row[10] else {}),
                "skills": json.loads(row[11]) if row[11] else {},
                "time_played": row[12] if row[12] else 0
            })
        return jsonify({"characters": char_list}), 200
    except Exception as e: return jsonify({"erro": str(e)}), 500
    finally:
        if conn: db_pool.putconn(conn)

@app.route('/delete_character', methods=['POST'])
def delete_character():
    dados = request.get_json()
    if not dados.get('user_id') or not dados.get('name') or not dados.get('password'): return jsonify({"erro": "Missing fields."}), 400
    conn = None
    try:
        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("SELECT password FROM users WHERE id = %s", (dados['user_id'],))
        user = c.fetchone()
        if not user or not check_password_hash(user[0], dados['password']): return jsonify({"erro": "Wrong pass."}), 401
        c.execute("DELETE FROM characters WHERE user_id = %s AND name = %s", (dados['user_id'], dados['name']))
        conn.commit()
        return jsonify({"mensagem": "Deleted!"}), 200
    except Exception as e: return jsonify({"erro": str(e)}), 500
    finally:
        if conn: db_pool.putconn(conn)

@socketio.on('connect')
def handle_connect(): pass

@socketio.on('disconnect')
def handle_disconnect():
    sid = request.sid
    if sid in online_players:
        player = online_players[sid]
        p_name = player.get('name', 'Desconhecido')
        room = player.get('room')

        _remover_do_party(sid, motivo="disconnected")
        _limpar_convites_de_party_pendentes(sid, p_name)
        _remover_do_trade(sid, motivo="disconnected")

        _queue_save(player)

        if room: leave_room(room)
        emit('player_left', {"name": p_name}, broadcast=True, include_self=False)
        players_by_name.pop(p_name, None)
        del online_players[sid]

@socketio.on('save_position')
def handle_save_position(data):
    sid = request.sid
    player = online_players.get(sid)
    if not player or not isinstance(data, dict): return
    try:
        pos_x = float(data['x'])
        pos_y = float(data['y'])
    except (KeyError, TypeError, ValueError):
        return
    if not (abs(pos_x) < 100000000 and abs(pos_y) < 100000000): return
    pos_x, pos_y = encaixar_no_tile(pos_x, pos_y)
    direction = data.get('direction', player.get('direction', 'down'))
    if direction not in ('up', 'down', 'left', 'right'):
        direction = player.get('direction', 'down')
    player['pos_x'], player['pos_y'], player['direction'] = pos_x, pos_y, direction
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
        c.execute("SELECT class_name, level, exp, pos_x, pos_y, direction, skins, floor, inventory, equipped_items, skills, kills, current_hp, current_mp, currency, time_played, npc_dialogue_state FROM characters WHERE user_id = %s AND name = %s", (user_id, p_name))
        row = c.fetchone()

        if not row: return

        real_skins = json.loads(row[6]) if row[6] else {}
        real_inventory = normalizar_inventario(json.loads(row[8]) if row[8] else [])
        real_equipped = normalizar_equipados(json.loads(row[9]) if row[9] else {})
        real_skills = json.loads(row[10]) if row[10] else {}

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
        data['session_start'] = time.time()
        
        for existing_sid, player in list(online_players.items()):
            if player.get('user_id') == user_id and existing_sid != sid:
                _remover_do_party(existing_sid, motivo="relogged")
                _queue_save(player)
                players_by_name.pop(player.get('name'), None)
                data['current_hp'] = player.get('current_hp')
                data['current_mp'] = player.get('current_mp')
                if player.get('pos_x') == -1 and player.get('pos_y') == -1: data['pos_x'], data['pos_y'] = -1, -1
                
                emit('force_disconnect', {"reason": "Alguem entrou na sua conta."}, room=existing_sid)
                room_to_leave = player.get('room')
                if room_to_leave: leave_room(room_to_leave, sid=existing_sid)
                emit('player_left', {"name": player.get('name')}, broadcast=True, include_self=False)
                del online_players[existing_sid]
                try: disconnect(sid=existing_sid)
                except: pass
                
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

        # item_db vai so' no payload (nao fica guardado em online_players).
        emit('sync_local_player', {**data, 'item_db': montar_item_db_cliente(),
                                   'skin_db': montar_skin_db_cliente(data.get('class_name'))}, room=sid)

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
        emit('current_players', {o_sid: dados_publicos_player(o_p) for o_sid, o_p in online_players.items()}, room=sid)
        emit('player_joined', dados_publicos_player(data), broadcast=True, include_self=False)

        emit('sync_area_data', montar_sync_area(sid, room), room=sid)
        
    except Exception: traceback.print_exc()
    finally:
        if conn: db_pool.putconn(conn)

@socketio.on('c')
def handle_c(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        if not isinstance(data, list) or len(data) == 0: return
        msg = str(data[0]).strip()
        if not msg: return
        
        room = p.get('room')
        payload = [p.get('name', ''), msg, p.get('class_name', 'Knight')]
        # Mesma área do movimento ('m'): o chunk do player + os 8 vizinhos.
        # Antes ia só pro chunk (800px) exato dele: quem estava do lado, mas
        # do outro lado da borda do chunk, via o player andar e nunca recebia
        # a mensagem nem o balão.
        if room:
            emit_area('c', payload, room, skip_sid=sid)
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

        npc_id = str(data.get('npc_id', '')).strip()
        if not npc_id: return

        if not isinstance(p.get('npc_dialogue_state'), dict):
            p['npc_dialogue_state'] = {}
        p['npc_dialogue_state'][npc_id] = True
        _queue_save(p)
    except Exception:
        traceback.print_exc()

@socketio.on('swap_req')
def handle_swap_req(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        
        target_name = str(data[0]) if len(data) > 0 else ""
        d_int = int(data[1]) if len(data) > 1 else 0
        
        target_sid = players_by_name.get(target_name)
        if not target_sid or target_sid not in online_players: return
        t_p = online_players[target_sid]
        
        px, py = float(p.get('pos_x', 0)), float(p.get('pos_y', 0))
        tx, ty = float(t_p.get('pos_x', 0)), float(t_p.get('pos_y', 0))
        if abs(px - tx) > 64 or abs(py - ty) > 64: return
        
        p['pos_x'], p['pos_y'] = tx, ty
        t_p['pos_x'], t_p['pos_y'] = px, py
        p['direction'] = DIR_MAP.get(d_int, 'down')
        
        opostos = {0: 1, 1: 0, 2: 3, 3: 2}
        t_dir = DIR_MAP.get(opostos.get(d_int, 0), 'up')
        t_p['direction'] = t_dir
        
        room_p = p.get('room')
        room_t = t_p.get('room')
        
        payload = [
            p['name'], tx, ty, d_int,
            t_p['name'], px, py, opostos.get(d_int, 0)
        ]
        
        emit('swap_exec', payload, room=room_p)
        if room_t and room_t != room_p:
            emit('swap_exec', payload, room=room_t)
            
        new_room_p = get_chunk(tx, ty, p.get('floor', 1))
        if room_p != new_room_p:
            leave_room(room_p, sid=sid)
            join_room(new_room_p, sid=sid)
            p['room'] = new_room_p
            
        new_room_t = get_chunk(px, py, t_p.get('floor', 1))
        if room_t != new_room_t:
            leave_room(room_t, sid=target_sid)
            join_room(new_room_t, sid=target_sid)
            t_p['room'] = new_room_t
            
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

        now = time.time()
        if now - p.get('last_attack_time', 0) < 1.0: return
        p['last_attack_time'] = now
        
        precisa_municao = p.get('class_name') == "Ranger"
        if precisa_municao and not tem_municao_equipada(p):
            emit('inventory_synced', {'inventory': ordenar_favoritos_primeiro(p.get('inventory', [])), 'equipped_items': p.get('equipped_items', {})}, room=sid)
            return
        
        mob_data = obter_ou_criar_mob(mob_id, mob_type_id, p.get('room'))
        if mob_data.get('hp', 1) <= 0: return
        # Mesma regra do mob pro player: andar diferente, sem golpe (nem aggro).
        if int(p.get('floor', 1) or 1) != MOB_FLOOR: return
        mob_data['last_activity'] = now
        mob_focar_agressor(mob_id, mob_data, sid)

        skills = p.get('skills', {})
        p_class = p.get('class_name', 'Knight')
        if len(_slots_com_classe_invalida(p)) > 0:
            base_dmg = PUNICAO_CLASSE_DANO
        else:
            bonus_itens = somar_bonus_combate_equipados(p.get('equipped_items', {}))
            base_dmg, _ = calc_player_stats(p_class, p.get('level', 1), skills, bonus_dmg=bonus_itens['bonus_damage'])
        
        min_dano = max(1, int(base_dmg * 0.85))
        max_dano = max(min_dano, int(base_dmg * 1.15))
        dano_final = random.randint(min_dano, max_dano) if max_dano > min_dano else base_dmg
        
        is_crit = random.random() < CRIT_CHANCE
        if is_crit: dano_final *= 2
        
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
        
        emit_area('mob_damaged', {'mob_id': mob_id, 'damage': dano_final, 'new_hp': mob_data['hp'], 'max_hp': mob_data.get('max_hp', mob_data['hp']), 'hit_type': hit_type, 'is_crit': is_crit, 'attacker_id': atacante_id, 'w_type': w_type, 'proj': proj, 'owner': ''}, room)
        
        if is_dead:
            xp_total = MOB_DB.get(mob_type_id, {}).get("xp", 40)
            xp_por_participante = _calcular_xp_por_participante(mob_data, xp_total)

            for participant_sid, xp_gained in xp_por_participante.items():
                if participant_sid not in online_players: continue

                part_p = online_players[participant_sid]

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
@socketio.on('register_map')
def handle_register_map(data):
    try:
        sid = request.sid
        if sid not in online_players or not isinstance(data, dict): return
        map_id = str(data.get('map', ''))[:200]
        fp = str(data.get('fp', ''))[:64]
        if not map_id: return
        for info in (data.get('mobs') or [])[:2000]:
            if not isinstance(info, dict): continue
            mob_id = str(info.get('id', ''))[:120]
            if not mob_id or spawn_do_mob_id(mob_id) is None: continue
            m = obter_ou_criar_mob(mob_id, str(info.get('type', '')) or None)
            m['mapa'] = map_id
            try:
                m['speed'] = min(10.0, max(0.2, float(info.get('speed', m.get('speed', MOB_SPEED_PADRAO)))))
                m['cooldown'] = min(30.0, max(0.3, float(info.get('cooldown', m.get('cooldown', MOB_COOLDOWN_PADRAO)))))
            except (TypeError, ValueError): pass
            hit = str(info.get('hit', 'physical_hit'))[:200]
            if hit and not hit.startswith('res://'): m['hit_effect'] = hit
        _registrar_npcs_do_mapa(map_id, data.get('npcs'), sid)
        grade = mapas_colisao.get(map_id)
        if grade is None or grade.get('fp') != fp:
            emit('need_map_grid', {'map': map_id}, room=sid)
        # Estado atual dos mobs (HP/posição/flag/morte) pra quem acabou de
        # entrar: no join eles ainda podiam não estar registrados.
        room = online_players[sid].get('room')
        if room:
            emit('sync_area_data', montar_sync_area(sid, room), room=sid)
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
        atual = mapas_colisao.get(map_id)
        if atual is not None and atual.get('fp') == fp: return  # já tenho essa versão
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

        loot_id = str(data.get('loot_id', ''))
        loot = ground_loot.get(loot_id)

        if loot is None or p.get('name') not in loot.get('owners', ()):
            emit('loot_collected', {'loot_id': loot_id, 'items': [], 'currency_gained': 0, 'currency_total': int(p.get('currency', 0)), 'already_taken': True, 'cap_bloqueado': False, 'bag_esvaziada': True}, room=sid)
            return

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

            if eh_municao(item_path):
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

@socketio.on('register_skill_hit')
def handle_skill_hit(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        
        skill_name = data.get('skill')
        p = online_players[sid]
        
        now = time.time()
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

MOVE_BALDE_MAX = 4.0
MOVE_RECARGA_POR_SEG = 4.0

@socketio.on('m')
def handle_m(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        p_name = p.get('name', '')
        if not p_name: return
        
        # Limite de passos por "balde": até MOVE_BALDE_MAX passos de rajada,
        # recarregando MOVE_RECARGA_POR_SEG por segundo. Antes era "no mínimo
        # 0.28s entre passos": quando a rede entregava dois passos juntos o
        # segundo era jogado fora e a posição do player no servidor (e na tela
        # dos outros, e pra IA dos mobs) ficava 1 SQM atrás.
        now = time.time()
        balde = min(MOVE_BALDE_MAX, p.get('_move_balde', MOVE_BALDE_MAX) + (now - p.get('last_move_time', now)) * MOVE_RECARGA_POR_SEG)
        p['last_move_time'] = now
        if balde < 1.0:
            p['_move_balde'] = balde
            return
        p['_move_balde'] = balde - 1.0
        
        if isinstance(data[0], str): x, y, d_int = float(data[1]), float(data[2]), int(data[3])
        else: x, y, d_int = float(data[0]), float(data[1]), int(data[2])

        destino_tile = tile_de(x, y)
        if any(int(npc.get('floor', 1)) == int(p.get('floor', 1) or 1)
               and tile_de(npc.get('pos_x', 0), npc.get('pos_y', 0)) == destino_tile
               for npc in active_npcs.values()):
            emit('sync_local_player', {
                'pos_x': p.get('pos_x', -1), 'pos_y': p.get('pos_y', -1),
                'direction': p.get('direction', 'down')
            }, room=sid)
            return
        
        p['pos_x'], p['pos_y'], p['direction'] = x, y, DIR_MAP.get(d_int, 'down')
        
        old_room = p.get('room')
        floor = p.get('floor', 1)
        new_room = get_chunk(x, y, floor)
        if old_room != new_room:
            if old_room: leave_room(old_room)
            join_room(new_room)
            p['room'] = new_room
            
        payload = [p_name, x, y, d_int]
        destinos = set(salas_vizinhas(new_room))
        if old_room and old_room != new_room:
            destinos |= set(salas_vizinhas(old_room))
        for r in destinos:
            emit('m', payload, room=r, include_self=False)
    except Exception: pass

@socketio.on('l')
def handle_l(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        p_name = p.get('name', '')
        if not p_name or not isinstance(data, list) or len(data) < 3: return
        if isinstance(data[0], str): d_int, x, y = float(data[1]), float(data[2]), float(data[3])
        else: d_int, x, y = int(data[0]), float(data[1]), float(data[2])
        
        p['direction'], p['pos_x'], p['pos_y'] = DIR_MAP.get(d_int, 'down'), x, y
        old_room = p.get('room')
        floor = p.get('floor', 1)
        new_room = get_chunk(x, y, floor)
        if old_room != new_room:
            if old_room: leave_room(old_room)
            join_room(new_room)
            p['room'] = new_room
            
        payload = [p_name, d_int, x, y]
        for r in salas_vizinhas(new_room):
            emit('l', payload, room=r, include_self=False)
    except Exception: pass

@socketio.on('request_area_sync')
def handle_request_area_sync(data):
    try:
        sid = request.sid
        if sid not in online_players: return
        p = online_players[sid]
        
        x = float(data.get('x', p.get('pos_x', 0)))
        y = float(data.get('y', p.get('pos_y', 0)))
        floor = int(data.get('floor', p.get('floor', 1)))
        
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
                socketio.emit('m', [p_name, x, y, d_int], room=r, skip_sid=sid)
                if floor_anterior != floor:
                    socketio.emit('player_status_updated', {"name": p_name, "floor": floor, "pos_x": x, "pos_y": y}, room=r, skip_sid=sid)
            p['_floor_broadcast'] = floor
            p['_move_balde'] = MOVE_BALDE_MAX  # o send_move logo depois do teleporte não pode cair no limite
            
        p['room'], p['pos_x'], p['pos_y'], p['floor'] = new_room, x, y, floor
        room = new_room
        
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

CHAVES_UPDATE_STATUS_PERMITIDAS = {'floor', 'is_typing', 'is_in_settings', 'is_in_skins', 'current_hp', 'current_mp', 'custom_z', 'is_dead'}

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

        if key == 'is_dead':
            value = bool(value)
            if value == bool(p.get('is_dead')) and not value:
                return
            if value:
                p['current_hp'] = 0.0
            else:
                # Respawn: servidor devolve o HP/MP cheio (o client não pode
                # mais subir o HP via update_status).
                max_hp_r, max_mp_r = calcular_max_vitais(p)
                p['current_hp'] = max_hp_r
                p['current_mp'] = max_mp_r

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
        if sid in online_players:
            online_players[sid]['floor'] = int(data.get('floor', 1))
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
        party, party_id = _get_party(sid)
        if not party or party['leader_sid'] != sid: return

        target_name = str(data.get('target_name', '')).strip()

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
    if eh_self and party['leader_sid'] == sid and len(party['members']) > 1:
        # Líder tentando sair sozinho com outros membros na party ainda: sem
        # fluxo de UI pra isso (ver plano) - rejeitado explicitamente.
        return

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
TRADE_INVITE_TIMEOUT_SEG = 15
trade_pending_invites = {}  # target_sid -> {'inviter_sid': sid, 'criado_em': time.time()}
trades = {}  # trade_id -> {'a_sid','b_sid','offers':{sid:{'items':[{'instance_id','qty'}],'currency':int}},'ready':{sid:bool},'locked':{sid:bool}}

def _get_trade(sid):
    trade_id = online_players.get(sid, {}).get('trade_id')
    return trades.get(trade_id), trade_id

def _outro_lado_trade(trade, sid):
    return trade['b_sid'] if trade['a_sid'] == sid else trade['a_sid']

def _validar_oferta(sid, offer):
    # Nunca confia no que o client mandou: reconstrói a oferta do zero a
    # partir do inventário REAL do player nesse exato instante. Usado tanto
    # no offer_update quanto (de novo, do zero) no lock_in/execução final.
    p = online_players.get(sid)
    if not p: return None
    inventario = p.get('inventory', [])
    vistos = set()
    itens_validados = []
    for entrada in offer.get('items', []):
        if not isinstance(entrada, dict): continue
        instance_id = str(entrada.get('instance_id', ''))
        if not instance_id or instance_id in vistos: return None
        inst = encontrar_instancia(inventario, instance_id)
        if inst is None: return None
        item_path = inst.get('item')
        if eh_municao(item_path):
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
    # Mesmo sem trade ativo, pode haver só um convite pendente envolvendo sid.
    for alvo_sid, entrada in list(trade_pending_invites.items()):
        inv_sid = entrada['inviter_sid']
        if alvo_sid == sid or inv_sid == sid:
            outro_sid = inv_sid if alvo_sid == sid else alvo_sid
            del trade_pending_invites[alvo_sid]
            if outro_sid in online_players:
                emit('trade_pending_status', {'pending': False, 'inviter_name': online_players.get(inv_sid, {}).get('name', '')}, room=outro_sid)

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
    if sid not in online_players: return
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
    if target_sid in trade_pending_invites or any(e['inviter_sid'] == sid for e in trade_pending_invites.values()):
        emit('trade_invite_result', {'success': False, 'reason': 'already_pending', 'target_name': target_name}, room=sid)
        return

    trade_pending_invites[target_sid] = {'inviter_sid': sid, 'criado_em': time.time()}
    emit('trade_invite_result', {'success': True, 'reason': '', 'target_name': target_name}, room=sid)
    inviter_name = online_players[sid].get('name', '')
    emit('trade_invite_received', {'inviter_name': inviter_name}, room=target_sid)
    emit('trade_pending_status', {'pending': True, 'inviter_name': inviter_name}, room=target_sid)

@socketio.on('accept_trade_invite')
def handle_accept_trade_invite(data):
    sid = request.sid
    if sid not in online_players: return
    inviter_name = str(data.get('inviter_name', '')).strip()
    inviter_sid = players_by_name.get(inviter_name)
    if not inviter_sid or inviter_sid not in online_players: return
    if trade_pending_invites.get(sid, {}).get('inviter_sid') != inviter_sid: return
    if online_players[sid].get('trade_id') or online_players[inviter_sid].get('trade_id'): return

    del trade_pending_invites[sid]
    trade_id = uuid.uuid4().hex
    trades[trade_id] = {
        'a_sid': inviter_sid, 'b_sid': sid,
        'offers': {inviter_sid: {'items': [], 'currency': 0}, sid: {'items': [], 'currency': 0}},
        'ready': {inviter_sid: False, sid: False},
        'locked': {inviter_sid: False, sid: False},
    }
    online_players[inviter_sid]['trade_id'] = trade_id
    online_players[sid]['trade_id'] = trade_id

    emit('trade_started', {'trade_id': trade_id, 'self_name': online_players[inviter_sid].get('name', ''), 'other_name': online_players[sid].get('name', '')}, room=inviter_sid)
    emit('trade_started', {'trade_id': trade_id, 'self_name': online_players[sid].get('name', ''), 'other_name': online_players[inviter_sid].get('name', '')}, room=sid)

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
            cap -= obter_cap_instancia(inst) if not eh_municao(inst.get('item')) else obter_cap_unitario_item(inst.get('item')) * entrada['qty']
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
            if eh_municao(inst.get('item')) and entrada['qty'] < int(inst.get('qty', 1)):
                inst['qty'] = int(inst.get('qty', 1)) - entrada['qty']
                adicionar_municao_ao_jogador(destino_p, inst.get('item'), entrada['qty'])
            else:
                origem_inv.remove(inst)
                if eh_municao(inst.get('item')):
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

@socketio.on('get_friends_list')
def handle_get_friends_list(data):
    conn = None
    try:
        sid = request.sid
        if sid not in online_players: return
        owner_name = online_players[sid].get('name', '')

        conn = db_pool.getconn()
        c = conn.cursor()
        c.execute("""SELECT f.friend_name, f.icon_pk, f.icon_guild, f.icon_seller, ch.class_name
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
            'online': row[0] in players_by_name,
        } for row in rows]

        emit('friends_list', {'friends': friends}, room=sid)
    except Exception: traceback.print_exc()
    finally:
        if conn: db_pool.putconn(conn)

# --- BACKGROUND TASKS ---
REGEN_BASE = 5.0       
REGEN_POR_5_LEVELS = 5.0  

def get_regen_amount(level):
    lvl = int(level or 1)
    return REGEN_BASE + REGEN_POR_5_LEVELS * (lvl // 5)

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

                regen = get_regen_amount(p.get('level', 1))
                curou = False

                if current_hp < max_hp: current_hp = min(max_hp, current_hp + regen); p['current_hp'] = current_hp; curou = True
                if current_mp < max_mp: current_mp = min(max_mp, current_mp + regen); p['current_mp'] = current_mp; curou = True

                if curou:
                    socketio.emit('sync_vitals', {'current_hp': current_hp, 'current_mp': current_mp}, room=sid)
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

# Convite de trade expira sozinho se o alvo não aceitar/recusar em 15s -
# tick de 1s (igual mob_cleanup_loop) pra não deixar o convite "pendurado"
# por muito mais tempo que o prometido.
def trade_invite_cleanup_loop():
    while True:
        socketio.sleep(1.0)
        now = time.time()
        stale = [alvo_sid for alvo_sid, entrada in list(trade_pending_invites.items()) if now - entrada.get('criado_em', now) > TRADE_INVITE_TIMEOUT_SEG]
        for alvo_sid in stale:
            entrada = trade_pending_invites.pop(alvo_sid, None)
            if not entrada: continue
            inviter_sid = entrada['inviter_sid']
            inviter_name = online_players.get(inviter_sid, {}).get('name', '')
            if inviter_sid in online_players:
                socketio.emit('trade_invite_result', {'success': False, 'reason': 'timeout', 'target_name': online_players.get(alvo_sid, {}).get('name', '')}, room=inviter_sid)
            if alvo_sid in online_players:
                socketio.emit('trade_pending_status', {'pending': False, 'inviter_name': inviter_name}, room=alvo_sid)

def mob_cleanup_loop():
    while True:
        # Tick curto: com 60s o respawn de 120s acontecia entre 120 e 180s.
        socketio.sleep(1.0)
        now = time.time()
        for m_id, m_data in list(active_mobs.items()):
            if m_data.get('hp', 1) > 0: continue
            if 'died_at' not in m_data:
                m_data['died_at'] = now
            if now - m_data['died_at'] < MOB_RESPAWN_SEG: continue
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
socketio.start_background_task(regen_loop)
socketio.start_background_task(autosave_loop)
socketio.start_background_task(loot_cleanup_loop)
socketio.start_background_task(trade_invite_cleanup_loop)
socketio.start_background_task(mob_cleanup_loop)
socketio.start_background_task(mob_ai_loop)
socketio.start_background_task(npc_ai_loop)
eventlet.spawn(db_writer_worker)

if __name__ == '__main__':
    init_db()
    socketio.run(app, host='0.0.0.0', port=3000, debug=True)