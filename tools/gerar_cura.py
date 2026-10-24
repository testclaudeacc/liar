"""Gera sprites/spawn/Heal e ManaHeal (8 quadros 16x16 = 128x16): brilhos
subindo pelo corpo do player, crescendo, piscando e sumindo.
Uso: python3 tools/gerar_cura.py arte/"""
import sys
from PIL import Image

T, N = 16, 8
PALETAS = {
    'Heal':     [(255, 255, 255), (178, 255, 178), (70, 214, 98), (24, 130, 62)],
    'ManaHeal': [(255, 255, 255), (176, 222, 255), (74, 148, 255), (34, 74, 186)],
}
# (x, y inicial = pe' do sprite, quadro em que nasce, sobe px/quadro, tamanho maximo)
PARTICULAS = [
    (4, 14, 0, 1.6, 2), (11, 15, 0, 1.4, 1), (8, 13, 1, 1.5, 2), (2, 12, 2, 1.2, 0),
    (13, 12, 2, 1.7, 1), (6, 15, 3, 1.5, 2), (10, 13, 3, 1.3, 0), (3, 14, 4, 1.6, 1),
    (12, 15, 4, 1.4, 2), (7, 11, 1, 1.2, 1), (9, 15, 4, 1.6, 0), (5, 10, 0, 1.0, 1),
]
VIDA = 4  # quadros que cada brilho dura

def brilho(img, ox, x, y, tam, pal):
    """tam 0: ponto; 1: cruz 3x3; 2: estrela 5x5 (cruz longa + miolo)."""
    def px(dx, dy, c):
        xx, yy = x + dx, y + dy
        if 0 <= xx < T and 0 <= yy < T:
            img.putpixel((ox + xx, yy), pal[c] + (255,))
    if tam == 0:
        px(0, 0, 1); return
    braco = 1 if tam == 1 else 2
    for d in range(1, braco + 1):
        c = 2 if d == braco else 1
        for dx, dy in ((d, 0), (-d, 0), (0, d), (0, -d)): px(dx, dy, c)
    if tam == 2:
        for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)): px(dx, dy, 3)
    px(0, 0, 0)

def gerar(nome, pasta):
    pal = PALETAS[nome]
    img = Image.new('RGBA', (T * N, T), (0, 0, 0, 0))
    for k in range(N):
        for (x, y0, nasce, sobe, tmax) in PARTICULAS:
            idade = k - nasce
            if idade < 0 or idade >= VIDA: continue
            y = int(round(y0 - sobe * idade * 2))
            # cresce, fica no maximo e encolhe (piscando no fim)
            tam = [max(0, tmax - 1), tmax, tmax, 0][idade]
            brilho(img, k * T, x, y, tam, pal)
    img.save(f'{pasta}/{nome}.png')
    fr = []
    for k in range(N):
        bg = Image.new('RGBA', (T, T), (60, 90, 50, 255)); bg.alpha_composite(img.crop((k * T, 0, k * T + T, T)))
        fr.append(bg.resize((128, 128), Image.NEAREST).convert('P', palette=Image.ADAPTIVE))
    fr[0].save(f'{pasta}/{nome}_preview.gif', save_all=True, append_images=fr[1:], duration=100, loop=0, disposal=2)
    bg = Image.new('RGBA', img.size, (60, 90, 50, 255)); bg.alpha_composite(img)
    bg.resize((T * N * 8, T * 8), Image.NEAREST).save(f'{pasta}/{nome}_preview.png')

for nome in PALETAS: gerar(nome, sys.argv[1])
