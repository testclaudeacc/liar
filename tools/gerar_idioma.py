#!/usr/bin/env python3
"""Gera telas/IdiomaTextos.java a partir de i18n/textos.tsv.

textos.tsv: uma frase por linha, "ingles<TAB>portugues<TAB>espanhol".
Linhas vazias ou comecando com # sao ignoradas. Valores no meio: {0}, {1}...
Uso (na raiz do projeto): python3 tools/gerar_idioma.py
"""
import os, sys
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ORIGEM = os.path.join(RAIZ, 'i18n', 'textos.tsv')
DESTINO = os.path.join(RAIZ, 'telas', 'IdiomaTextos.java')

def esc(t):
    # Acentos viram \uXXXX: o .java fica so' ASCII e compila igual com qualquer encoding.
    t = t.replace('\\', '\\\\').replace('"', '\\"')
    return ''.join(c if ord(c) < 128 else '\\u%04x' % ord(c) for c in t)

linhas, vistos, erros = [], set(), 0
for n, l in enumerate(open(ORIGEM, encoding='utf-8').read().split('\n'), 1):
    if not l.strip() or l.startswith('#'): continue
    c = l.split('\t')
    if len(c) != 3:
        print(f"linha {n}: precisa de 3 colunas (tem {len(c)}): {l[:60]}"); erros += 1; continue
    if c[0] in vistos:
        print(f"linha {n}: repetida: {c[0][:60]}"); erros += 1; continue
    vistos.add(c[0])
    for col in c[1:]:
        for i in range(10):
            if ('{%d}' % i in c[0]) != ('{%d}' % i in col) and col:
                print(f"linha {n}: {{{i}}} nao bate entre ingles e traducao: {l[:80]}"); erros += 1
    linhas.append('\t'.join(c))
if erros: sys.exit(1)

# Constantes de String no .class tem limite de 64KB: blocos de ~20k caracteres.
blocos, atual = [], ''
for l in linhas:
    if len(atual) + len(l) > 20000: blocos.append(atual); atual = ''
    atual += l + '\n'
if atual: blocos.append(atual)

with open(DESTINO, 'w', encoding='utf-8') as f:
    f.write('package com.teste.game.telas;\n\n')
    f.write('/** GERADO por tools/gerar_idioma.py a partir de i18n/textos.tsv - nao editar a mao. */\n')
    f.write('final class IdiomaTextos {\n    private IdiomaTextos() {}\n\n    static final String[] BLOCOS = {\n')
    for b in blocos:
        partes = [esc(x) + '\\n' for x in b.rstrip('\n').split('\n')]
        f.write('        ""\n')
        for p in partes: f.write(f'            + "{p}"\n')
        f.write('        ,\n')
    f.write('    };\n}\n')
print(f"{len(linhas)} frases -> {os.path.relpath(DESTINO, RAIZ)} ({len(blocos)} bloco(s))")
