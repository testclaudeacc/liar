package com.teste.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Teste de comparacao Godot vs libGDX: "mundo" com sprites animados (carga de
 * fundo) + chat de verdade em cima + FPS na tela. Objetivo: ver se abrir o
 * chat com essa carga de fundo custa FPS igual ao Godot custava, ou nao.
 */
public class TesteGame extends ApplicationAdapter {

    private SpriteBatch batch;
    private Stage stage;
    private Skin skin;
    private OrthographicCamera camera;
    private Viewport viewport;

    private Texture texturaEntidade;
    private MundoSimulado mundo;
    private ChatUI chat;
    private Label fpsLabel;

    private float tempoDesdeUltimaMsg = 0f;
    private int contadorMsg = 0;

    private static final int LARGURA_MUNDO = 900;
    private static final int ALTURA_MUNDO = 400;

    @Override
    public void create() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        viewport = new ScreenViewport(camera);

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        skin = criarSkinMinima();

        texturaEntidade = criarTexturaQuadrada(Color.WHITE);
        mundo = new MundoSimulado(texturaEntidade, LARGURA_MUNDO, ALTURA_MUNDO);

        chat = new ChatUI(stage, skin, LARGURA_MUNDO, ALTURA_MUNDO, "Testador", "Knight");
        chat.adicionarMensagemDeTeste("Sistema", Color.WHITE, "Chat de teste iniciado. TAB abre/fecha, ENTER envia.");

        fpsLabel = new Label("FPS: 0", skin);
        fpsLabel.setPosition(10, ALTURA_MUNDO - 24);
        stage.addActor(fpsLabel);

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.TAB) {
                    chat.setVisivel(!chat.isVisivel());
                    return true;
                }
                return false;
            }
        });
    }

    private Skin criarSkinMinima() {
        Skin s = new Skin();
        BitmapFont fonte = new BitmapFont();
        s.add("default-font", fonte);

        Label.LabelStyle labelStyle = new Label.LabelStyle(fonte, Color.WHITE);
        s.add("default", labelStyle);

        NinePatchDrawable fundoCampo = criarFundoDrawable(new Color(0.15f, 0.15f, 0.15f, 0.9f));
        NinePatchDrawable cursor = criarFundoDrawable(Color.WHITE);
        NinePatchDrawable selecao = criarFundoDrawable(new Color(0.3f, 0.5f, 1f, 0.5f));

        TextField.TextFieldStyle tfStyle = new TextField.TextFieldStyle(fonte, Color.WHITE, cursor, selecao, fundoCampo);
        s.add("default", tfStyle);

        ScrollPane.ScrollPaneStyle spStyle = new ScrollPane.ScrollPaneStyle();
        s.add("default", spStyle);

        // ChatUI agora tem botoes (+/-/abas/close) que pedem esses 3 nomes
        // de estilo pelo nome (ver UiSkin real) - essa skin minima de
        // benchmark nao tinha nenhum TextButtonStyle antes (o ChatUI antigo
        // nao usava botao nenhum). So' pra nao quebrar esse benchmark
        // isolado, nao pra bater visualmente com o jogo de verdade.
        s.add("verde-popup", criarEstiloBotao(fonte, new Color(0.1f, 0.6f, 0.1f, 1f)));
        s.add("vermelho-popup", criarEstiloBotao(fonte, new Color(0.6f, 0.1f, 0.1f, 1f)));
        s.add("cinza-popup", criarEstiloBotao(fonte, new Color(0.4f, 0.4f, 0.4f, 1f)));
        s.add("subtitulo", labelStyle);
        s.add("painel", criarFundoDrawable(new Color(0.12f, 0.12f, 0.12f, 0.95f)), com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        s.add("popup-painel", criarFundoDrawable(new Color(0.12f, 0.12f, 0.12f, 0.95f)), com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);

        return s;
    }

    private com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle criarEstiloBotao(BitmapFont fonte, Color cor) {
        com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle estilo = new com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle();
        estilo.font = fonte;
        estilo.fontColor = Color.WHITE;
        estilo.up = criarFundoDrawable(cor);
        estilo.down = criarFundoDrawable(cor.cpy().mul(0.7f, 0.7f, 0.7f, 1f));
        estilo.over = criarFundoDrawable(cor.cpy().mul(1.2f, 1.2f, 1.2f, 1f));
        return estilo;
    }

    private NinePatchDrawable criarFundoDrawable(Color cor) {
        Pixmap pm = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pm.setColor(cor);
        pm.fill();
        Texture tex = new Texture(pm);
        pm.dispose();
        return new NinePatchDrawable(new NinePatch(tex, 1, 1, 1, 1));
    }

    private Texture criarTexturaQuadrada(Color cor) {
        Pixmap pm = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        pm.setColor(cor);
        pm.fill();
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        mundo.atualizar(delta);

        // Simula mensagens chegando periodicamente, igual jogadores reais
        // conversando - testa o custo continuo de popular o log, nao só o
        // custo de abrir o painel.
        tempoDesdeUltimaMsg += delta;
        if (tempoDesdeUltimaMsg > 1.5f) {
            tempoDesdeUltimaMsg = 0f;
            contadorMsg++;
            chat.adicionarMensagemDeTeste("Player" + MathUtils.random(1, 20), Color.WHITE,
                "mensagem de teste numero " + contadorMsg);
        }

        fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());

        ScreenUtilsClear();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        mundo.desenhar(batch);
        batch.end();

        stage.act(delta);
        stage.draw();
    }

    private void ScreenUtilsClear() {
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        stage.dispose();
        skin.dispose();
        texturaEntidade.dispose();
    }
}
