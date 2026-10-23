#!/usr/bin/env python3
"""Gera telas/TermosTexto.java a partir de termos/termos_<pt|en|es>.txt.

O texto dos Termos de Uso fica DENTRO do client (nada de arquivo do lado do
servidor nem fora do jogo). A versao (VERSAO) tem que ser igual a
servidor.py::TERMOS_VERSAO: mudou o texto de verdade? Suba as duas.
Uso (na raiz do projeto): python3 tools/gerar_termos.py
"""
import os, re
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESTINO = os.path.join(RAIZ, 'telas', 'TermosTexto.java')

servidor = open(os.path.join(RAIZ, 'servidor.py'), encoding='utf-8').read()
versao = re.search(r'^TERMOS_VERSAO = "([^"]+)"', servidor, re.M).group(1)

def esc(t):
    t = t.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n')
    return ''.join(c if ord(c) < 128 else '\\u%04x' % ord(c) for c in t)

def blocos(t, tam=6000):
    # Constante Java tem limite de tamanho: junta pedacos.
    partes = [t[i:i + tam] for i in range(0, len(t), tam)]
    return '\n            + '.join('"%s"' % esc(p) for p in partes)

textos = {l: open(os.path.join(RAIZ, 'termos', f'termos_{l}.txt'), encoding='utf-8').read().strip()
          for l in ('en', 'pt', 'es')}
java = f'''package com.teste.game.telas;

/** GERADO por tools/gerar_termos.py a partir de termos/*.txt - nao edite a mao.
 * VERSAO tem que bater com servidor.py::TERMOS_VERSAO (vai junto no /register). */
final class TermosTexto {{
    private TermosTexto() {{}}

    static final String VERSAO = "{versao}";

    /** Indice igual Idioma.atual(): 0 = ingles, 1 = portugues, 2 = espanhol. */
    static String texto(int idioma) {{
        switch (idioma) {{
            case 1: return PT;
            case 2: return ES;
            default: return EN;
        }}
    }}

    private static final String EN = {blocos(textos['en'])};

    private static final String PT = {blocos(textos['pt'])};

    private static final String ES = {blocos(textos['es'])};
}}
'''
open(DESTINO, 'w', encoding='ascii').write(java)
print(f"Termos v{versao} -> telas/TermosTexto.java")
