package com.teste.game.mapa;

import java.util.HashMap;
import java.util.Map;

/**
 * Indice espacial de celulas do mapa (Above_Player, overlay, noturnas...):
 * baldes de BALDE x BALDE tiles. Antes cada frame percorria a lista do mapa
 * INTEIRO (milhares de copas de arvore) so' pra descartar quem estava fora
 * da tela - no celular era boa parte do "entities". Agora so' os baldes que
 * a camera ve. Devolve os indices em ordem crescente (mesma ordem de desenho
 * da lista original, entao a sobreposicao entre celulas nao muda).
 */
final class IndiceCelulas {
    private static final int BALDE = 16;
    private final Map<Long, int[]> baldes = new HashMap<>();
    private int[] saida = new int[64];
    private int n;
    final int tamanho; // tamanho da lista quando foi indexada (mudou: reindexar)

    interface Coord { int cx(int i); int cy(int i); }

    IndiceCelulas(int tamanho, Coord coord) {
        this.tamanho = tamanho;
        Map<Long, int[]> tmp = new HashMap<>();
        Map<Long, Integer> cont = new HashMap<>();
        for (int i = 0; i < tamanho; i++) cont.merge(chave(Math.floorDiv(coord.cx(i), BALDE), Math.floorDiv(coord.cy(i), BALDE)), 1, Integer::sum);
        for (Map.Entry<Long, Integer> e : cont.entrySet()) tmp.put(e.getKey(), new int[e.getValue()]);
        Map<Long, Integer> pos = new HashMap<>();
        for (int i = 0; i < tamanho; i++) {
            long k = chave(Math.floorDiv(coord.cx(i), BALDE), Math.floorDiv(coord.cy(i), BALDE));
            int p = pos.merge(k, 1, Integer::sum) - 1;
            tmp.get(k)[p] = i; // ja' em ordem crescente dentro do balde
        }
        baldes.putAll(tmp);
    }

    private static long chave(int bx, int by) { return ((long) bx << 32) ^ (by & 0xffffffffL); }

    /** Indices das celulas com cx0..cx1, cy0..cy1 (aproximado por balde), em
     * ordem crescente. O array devolvido e' reaproveitado: use so' os
     * primeiros quantos() itens, antes da proxima chamada. */
    int[] naArea(int cx0, int cy0, int cx1, int cy1) {
        n = 0;
        int bx0 = Math.floorDiv(cx0, BALDE), bx1 = Math.floorDiv(cx1, BALDE);
        int by0 = Math.floorDiv(cy0, BALDE), by1 = Math.floorDiv(cy1, BALDE);
        int baldesUsados = 0;
        for (int bx = bx0; bx <= bx1; bx++) {
            for (int by = by0; by <= by1; by++) {
                int[] b = baldes.get(chave(bx, by));
                if (b == null) continue;
                baldesUsados++;
                if (n + b.length > saida.length) saida = java.util.Arrays.copyOf(saida, Math.max(saida.length * 2, n + b.length));
                System.arraycopy(b, 0, saida, n, b.length);
                n += b.length;
            }
        }
        if (baldesUsados > 1) java.util.Arrays.sort(saida, 0, n);
        return saida;
    }

    int quantos() { return n; }
}
