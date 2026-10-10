package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.profiling.GLProfiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Medidor de desempenho so' pro admin (fica embaixo do fps). Mostra quanto
 * cada parte do frame gastou e, quando o frame demora demais (pico), guarda
 * o que aconteceu naquele frame por alguns segundos:
 * - partes do render (logica, luzes, mapa, entidades, telhados, nomes, ui);
 * - eventos do servidor que rodaram antes do frame (nome e tempo de cada);
 * - "espera": tempo fora do render e dos eventos - GPU atrasada (o celular
 *   ainda desenhando o frame anterior), vsync ou o sistema;
 * - GC: a memoria usada caiu (o coletor de lixo do Java rodou).
 * Tambem mostra draw calls e trocas de textura (GLProfiler).
 */
public final class PerfAdmin {

    /** Frame mais lento que isso vira "pico" (≈ abaixo de 30 fps). */
    private static final float PICO_MS = 34f;
    private static final float MOSTRAR_PICO_SEG = 10f;

    private final GLProfiler gl;
    private final Map<String, Float> secoes = new LinkedHashMap<>();
    private final Map<String, Float> eventos = new LinkedHashMap<>();
    private long inicioFrame, ultimaMarca, inicioAnterior = 0L;
    private long heapAnterior = 0L;
    private boolean gcNesteFrame = false;
    private float suavizadoRender = 0f, suavizadoEspera = 0f;
    private String linhaAtual = "", textoPico = "";
    private float tempoPico = 0f;
    private final List<String> historico = new ArrayList<>();

    public PerfAdmin() {
        gl = new GLProfiler(Gdx.graphics);
        gl.enable();
    }

    /** Chamado pelo GameSocket (thread do jogo) a cada evento tratado. */
    public void eventoMedido(String nome, long nanos) {
        eventos.merge(nome, nanos / 1e6f, Float::sum);
    }

    public void inicioFrame() {
        inicioFrame = System.nanoTime();
        ultimaMarca = inicioFrame;
        secoes.clear();
        long heap = Gdx.app.getJavaHeap();
        gcNesteFrame = heapAnterior > 0 && heap < heapAnterior - 1_500_000L;
        heapAnterior = heap;
    }

    public void marca(String nome) {
        long agora = System.nanoTime();
        secoes.merge(nome, (agora - ultimaMarca) / 1e6f, Float::sum);
        ultimaMarca = agora;
    }

    public void fimFrame(float delta) {
        long agora = System.nanoTime();
        float render = (agora - inicioFrame) / 1e6f;
        float intervalo = inicioAnterior == 0L ? delta * 1000f : (inicioFrame - inicioAnterior) / 1e6f;
        inicioAnterior = inicioFrame;
        float totalEventos = 0f;
        for (float v : eventos.values()) totalEventos += v;
        float espera = Math.max(0f, intervalo - render - totalEventos);
        suavizadoRender += (render - suavizadoRender) * 0.1f;
        suavizadoEspera += (espera - suavizadoEspera) * 0.1f;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.US, "cpu %.1f  wait %.1f  dc %d  tex %d  heap %dMB",
            suavizadoRender, suavizadoEspera, gl.getDrawCalls(), gl.getTextureBindings(),
            Gdx.app.getJavaHeap() / 1_000_000L));
        linhaAtual = sb.toString();

        if (intervalo >= PICO_MS) {
            StringBuilder p = new StringBuilder();
            p.append(String.format(java.util.Locale.US, "SPIKE %.0fms: cpu %.1f, events %.1f, wait %.1f",
                intervalo, render, totalEventos, espera));
            if (gcNesteFrame) p.append(", GC");
            // As 3 partes mais caras do render e os eventos mais caros.
            p.append("\n  ").append(maiores(secoes, 4));
            if (!eventos.isEmpty()) p.append("\n  ev: ").append(maiores(eventos, 3));
            textoPico = p.toString();
            tempoPico = MOSTRAR_PICO_SEG;
            historico.add(textoPico.replace('\n', ' '));
            if (historico.size() > 20) historico.remove(0);
            Gdx.app.log("PerfAdmin", textoPico.replace('\n', ' '));
        } else if (tempoPico > 0f) {
            tempoPico -= delta;
            if (tempoPico <= 0f) textoPico = "";
        }
        eventos.clear();
        gl.reset();
    }

    private static String maiores(Map<String, Float> mapa, int n) {
        List<Map.Entry<String, Float>> lista = new ArrayList<>(mapa.entrySet());
        lista.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, lista.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(lista.get(i).getKey()).append(' ')
                .append(String.format(java.util.Locale.US, "%.1f", lista.get(i).getValue()));
        }
        return sb.toString();
    }

    /** Linha fixa + o ultimo pico (some depois de alguns segundos). */
    public String texto() {
        return textoPico.isEmpty() ? linhaAtual : linhaAtual + "\n" + textoPico;
    }

    /** Ultimos picos (comando /perf no chat). */
    public List<String> historico() { return historico; }

    public void dispose() {
        gl.disable();
    }
}
