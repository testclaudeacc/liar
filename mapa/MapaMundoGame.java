package com.teste.game.mapa;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

/**
 * Entry point de verificacao visual pro World.tmx de verdade. Camera livre
 * (WASD move, Q/E ou scroll da zoom) so pra navegar o mapa inteiro e
 * confirmar visualmente que bateu com o que foi validado no Tiled - nao e o
 * jogo final ainda (sem player, sem rede).
 */
public class MapaMundoGame extends ApplicationAdapter {

    private OrthographicCamera camera;
    private MapaMundo mapa;
    private SpriteBatch batch;
    private float velocidadeCamera = 400f;
    private float zoom = 3f;

    @Override
    public void create() {
        mapa = new MapaMundo();
        batch = new SpriteBatch();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(mapa.larguraPx() / 2f, mapa.alturaPx() / 2f, 0);
        camera.zoom = zoom;
        camera.update();

        Gdx.input.setInputProcessor(new com.badlogic.gdx.InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                zoom = MathUtils.clamp(zoom + amountY * 0.1f, 0.1f, 4f);
                return true;
            }
        });
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        float deslocamento = velocidadeCamera * zoom * delta;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) camera.position.y += deslocamento;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) camera.position.y -= deslocamento;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) camera.position.x -= deslocamento;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) camera.position.x += deslocamento;
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) zoom = MathUtils.clamp(zoom + delta, 0.1f, 4f);
        if (Gdx.input.isKeyPressed(Input.Keys.E)) zoom = MathUtils.clamp(zoom - delta, 0.1f, 4f);

        camera.zoom = zoom;
        camera.update();

        mapa.atualizar(delta);

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        mapa.desenharMapa(camera);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        mapa.desenharOverlays(batch, camera.position.x, camera.position.y);
        batch.end();
        mapa.desenharTelhados(camera, camera.position.x, camera.position.y);
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    @Override
    public void dispose() {
        mapa.dispose();
        batch.dispose();
    }
}
