import math, sys
from PIL import Image
T, N = 16, 9
BRANCO = (255, 255, 255); CLARO = (210, 218, 232); MEIO = (148, 154, 172); ESCURO = (84, 86, 104)
VERM = (210, 28, 28); VERM_CLARO = (255, 110, 100)
# Curva do corte (bezier quadratica): de cima-esquerda ate' baixo-direita,
# barriga pra cima-direita (lua crescente).
# Meia lua: arco de circulo de ~200 graus, da esquerda-baixo, por cima, ate'
# a direita-baixo (y da imagem cresce pra baixo).
CX, CY, R = 8.0, 9.5, 5.6
ANG0, ANG1 = math.radians(200), math.radians(-20)
def ponto(u):
    a = ANG0 + (ANG1 - ANG0) * u
    return (CX + R * math.cos(a), CY - R * math.sin(a))
AMOSTRAS = [(i / 400, ponto(i / 400)) for i in range(401)]
cabeca = [0.2, 0.45, 0.72, 0.95, 1.0, 1.0, 1.0, 1.0, 1.0]
cauda  = [0.0, 0.0, 0.0, 0.0, 0.12, 0.32, 0.52, 0.72, 0.88]
largura = [2.0, 2.6, 3.2, 3.6, 3.6, 3.1, 2.5, 1.9, 1.3]
img = Image.new('RGBA', (T * N, T), (0, 0, 0, 0))
for k in range(N):
    for y in range(T):
        for x in range(T):
            px, py = x + 0.5, y + 0.5
            u, (qx, qy) = min(AMOSTRAS, key=lambda s: (s[1][0] - px) ** 2 + (s[1][1] - py) ** 2)
            if u < cauda[k] or u > cabeca[k]: continue
            d = math.hypot(px - qx, py - qy)
            # grossa no meio, fina nas pontas
            w = largura[k] * math.sin(math.pi * u) ** 0.7 / 2 + 0.3
            if d > w: continue
            # lado: convexo (de fora, fio da lamina) = claro; concavo = cinza
            u2 = min(1, u + 0.01); tx, ty = ponto(u2)[0] - qx, ponto(u2)[1] - qy
            lado = tx * (py - qy) - ty * (px - qx)   # < 0: pro lado de fora (cima-direita)
            frac = d / w
            if lado < 0: cor = BRANCO if frac > 0.45 else CLARO
            else: cor = MEIO if frac < 0.6 else ESCURO
            if cabeca[k] < 1 and cabeca[k] - u < 0.08: cor = BRANCO   # ponta que avanca brilha
            if k >= 6: cor = {BRANCO: CLARO, CLARO: MEIO, MEIO: ESCURO}.get(cor, cor)
            if k >= 8: cor = ESCURO
            img.putpixel((k * T + x, y), cor + (255,))
    # Toque vermelho: rastro de sangue colado no lado de dentro do corte.
    if 3 <= k <= 6:
        ini, fim = (0.38, 0.66) if k <= 5 else (0.5, 0.62)
        for i in range(12):
            u = ini + (fim - ini) * i / 11
            if u < cauda[k]: continue
            qx, qy = ponto(u); tx, ty = ponto(u + 0.01)[0] - qx, ponto(u + 0.01)[1] - qy
            n = math.hypot(CX - qx, CY - qy); nx, ny = (CX - qx) / n, (CY - qy) / n   # pro centro (lado de dentro)
            w = largura[k] * math.sin(math.pi * u) ** 0.7 / 2 + 0.3
            x, y = int(qx + nx * (w + 0.4)), int(qy + ny * (w + 0.4))
            if 0 <= x < T and 0 <= y < T and img.getpixel((k * T + x, y))[3] == 0:
                img.putpixel((k * T + x, y), (VERM if i % 3 else VERM_CLARO) + (255,))
out = sys.argv[1]
img.save(out)
bg = Image.new('RGBA', img.size, (60, 90, 50, 255)); bg.alpha_composite(img)
bg.resize((T * N * 8, T * 8), Image.NEAREST).save(out.replace('.png', '_preview.png'))
