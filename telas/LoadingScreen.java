package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/**
 * Transicao curta entre AuthScreen e WorldScreen - equivalente ao
 * fade+loading do Global.gd no jogo Godot (ver memoria
 * scene_transition_loading_stall). Sem isso, apertar Play travava a tela por
 * alguns segundos com a tela anterior parada: a construcao de WorldScreen
 * (texturas/fontes/TiledMap, tudo sincrono) acontecia no MESMO frame do
 * clique, entao nada novo era desenhado ate ela terminar.
 *
 * Usa so' BitmapFont/SpriteBatch default (nada de Skin/arquivo) de proposito
 * - tem que ser instantaneo de construir, senao a propria tela de loading
 * vira outro soluco.
 *
 * O trabalho pesado so' roda no 2o frame (nao no primeiro) pra garantir que
 * o 1o frame das bolinhas realmente chegou a ser apresentado na tela (via
 * glfwSwapBuffers do backend) antes do travamento comecar - disparar no
 * mesmo frame que a tela e' criada corre o risco do trabalho pesado competir
 * com esse primeiro render() e o usuario nunca ver o loading de verdade.
 */
public class LoadingScreen extends ScreenAdapter {

    private final Runnable trabalhoPesado;
    private final ShapeRenderer formas = new ShapeRenderer();
    private int frame = 0;
    private boolean disparado = false;
    private float tempo = 0f;

    // Bolinhas carregando: 3 no meio da tela, cada uma "pulando" (cresce e
    // clareia) atras da outra. So' ShapeRenderer - nada pra carregar.
    private static final int BOLINHAS = 3;
    private static final float CICLO_SEG = 1.1f;

    // true: antes do trabalho pesado, espera a Precarga (mapa/atlas) terminar -
    // ela carrega aos poucos a cada frame, entao as bolinhas continuam animando.
    private final boolean precarregar;

    public LoadingScreen(Runnable trabalhoPesado) { this(trabalhoPesado, false); }

    public LoadingScreen(Runnable trabalhoPesado, boolean precarregar) {
        this.trabalhoPesado = trabalhoPesado;
        this.precarregar = precarregar;
        if (precarregar) Precarga.iniciar();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        // delta grande (frame travado no trabalho pesado) nao pula a animacao.
        tempo += Math.min(delta, 0.05f);
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        formas.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        float raio = Math.max(5f, Math.min(w, h) * 0.012f);
        float espaco = raio * 3.6f;
        float x0 = w / 2f - espaco * (BOLINHAS - 1) / 2f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        formas.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < BOLINHAS; i++) {
            float fase = ((tempo / CICLO_SEG) - i * 0.18f) % 1f;
            if (fase < 0f) fase += 1f;
            // Pulo curto no comeco do ciclo, parada no resto.
            float pulo = fase < 0.5f ? MathUtils.sin(fase * 2f * MathUtils.PI) : 0f;
            float c = 0.45f + 0.55f * pulo;
            formas.setColor(c, c, c, 0.55f + 0.45f * pulo);
            formas.circle(x0 + i * espaco, h / 2f + pulo * raio * 1.4f, raio * (1f + 0.25f * pulo), 24);
        }
        formas.end();

        if (precarregar && !Precarga.atualizar()) return;
        frame++;
        if (frame >= 2 && !disparado) {
            disparado = true;
            trabalhoPesado.run();
        }
    }

    @Override
    public void dispose() {
        formas.dispose();
    }
}
