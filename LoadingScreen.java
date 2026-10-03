package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

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
 * o frame "Loading..." realmente chegou a ser apresentado na tela (via
 * glfwSwapBuffers do backend) antes do travamento comecar - disparar no
 * mesmo frame que a tela e' criada corre o risco do trabalho pesado competir
 * com esse primeiro render() e o usuario nunca ver o "Loading..." de verdade.
 */
public class LoadingScreen extends ScreenAdapter {

    private final Runnable trabalhoPesado;
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private int frame = 0;
    private boolean disparado = false;

    public LoadingScreen(Runnable trabalhoPesado) {
        this.trabalhoPesado = trabalhoPesado;
        font.getData().setScale(2.2f);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        layout.setText(font, "Loading...");
        batch.begin();
        font.draw(batch, layout, (Gdx.graphics.getWidth() - layout.width) / 2f, (Gdx.graphics.getHeight() + layout.height) / 2f);
        batch.end();

        frame++;
        if (frame >= 2 && !disparado) {
            disparado = true;
            trabalhoPesado.run();
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
