package com.teste.game.mapa;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;

import java.util.List;

/**
 * Mapa de luz multiplicativo - mesma tecnica do Mirage Realms (decompilado
 * pra confirmar: FrameBuffer limpo com a cor ambiente, cada luz desenhada em
 * cima com blend ADITIVO (GL_SRC_ALPHA, GL_ONE - "light.png", um glow radial
 * branco, tingido pela cor da luz), depois esse framebuffer inteiro e'
 * desenhado por cima da cena MULTIPLICANDO (GL_DST_COLOR, GL_ZERO) - onde o
 * mapa de luz ta escuro, a cena escurece; onde tem luz, a cena clareia/tinge
 * com a cor da luz; branco puro deixa a cena intocada.
 *
 * Simplificacoes DELIBERADAS em relacao ao Mirage (nao replicadas porque nao
 * se aplicam aqui ainda): Mirage usa 2 FrameBuffers (um "estatico" cacheado,
 * so' redesenhado quando a camera anda, + um "dinamico" redesenhado todo
 * frame) porque la' o mapa e' grande e tem muitas luzes espalhadas, achadas
 * escaneando tile por tile perto da camera. Aqui MapaPropriedades ja extrai
 * TODAS as luzes do mapa inteiro de uma vez no load (poucas, ver
 * World.tmx::is_light) - redesenhar todas elas num FrameBuffer so' todo
 * frame e' barato o bastante pra nao precisar desse cache (se o mapa crescer
 * muito e isso virar gargalo de verdade, ai' sim vale separar em
 * estatico/dinamico). Tambem nao tem ciclo dia/noite (Mirage calcula a cor
 * ambiente a partir da hora do "realm time" do servidor) - cor ambiente fixa
 * por enquanto, ver corAmbiente.
 */
public class MapaIluminacao implements Disposable {

    /** Escurao fixo (sem ciclo dia/noite ainda) - baixo o bastante pra
     * tocha/luz fazerem diferenca visivel, alto o bastante pra nao ficar
     * tudo preto fora do alcance de alguma luz. */
    public static final Color AMBIENTE_PADRAO = new Color(0.22f, 0.22f, 0.3f, 1f);

    private final Texture texturaLuz;
    private final TextureRegion regiaoLuz;
    private FrameBuffer fbo;
    private TextureRegion fboRegiao;
    private int fboLargura = -1, fboAltura = -1;

    public Color corAmbiente = new Color(AMBIENTE_PADRAO);

    public MapaIluminacao() {
        texturaLuz = new Texture(Gdx.files.internal("lights/light.png"));
        texturaLuz.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        regiaoLuz = new TextureRegion(texturaLuz);
    }

    private void garantirFbo(int largura, int altura) {
        if (largura == fboLargura && altura == fboAltura && fbo != null) return;
        if (fbo != null) fbo.dispose();
        fboLargura = largura;
        fboAltura = altura;
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, largura, altura, false);
        fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        // flip(false,true) - FrameBuffer grava de baixo pra cima (origem
        // OpenGL), igual o Mirage faz na propria regiao da FBO.
        fboRegiao = new TextureRegion(fbo.getColorBufferTexture(), 0, 0, largura, altura);
        fboRegiao.flip(false, true);
    }

    /** Desenha todas as luzes no FrameBuffer interno - chamar 1x por frame,
     * FORA de qualquer outro batch.begin()/end() (usa o batch recebido pra
     * desenhar DENTRO do proprio FBO, nao na tela). */
    public void renderizar(Batch batch, OrthographicCamera camera, List<MapaPropriedades.Luz> luzes) {
        garantirFbo(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());

        fbo.begin();
        Gdx.gl.glClearColor(corAmbiente.r, corAmbiente.g, corAmbiente.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        for (MapaPropriedades.Luz luz : luzes) {
            batch.setColor(luz.cor);
            float diametro = luz.raio * 2f;
            batch.draw(regiaoLuz, luz.x - luz.raio, luz.y - luz.raio, diametro, diametro);
        }
        batch.setColor(Color.WHITE);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        fbo.end();
    }

    /** Multiplica o FrameBuffer de luz por cima do que ja foi desenhado na
     * tela nesse frame - chamar por ULTIMO, depois de tudo (mapa, player,
     * nome) e antes da UI (HUD/chat nao devem escurecer). */
    public void compositar(Batch batch, OrthographicCamera camera) {
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        batch.begin();
        float meiaLargura = camera.viewportWidth * camera.zoom / 2f;
        float meiaAltura = camera.viewportHeight * camera.zoom / 2f;
        batch.draw(fboRegiao, camera.position.x - meiaLargura, camera.position.y - meiaAltura,
                meiaLargura * 2f, meiaAltura * 2f);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void dispose() {
        texturaLuz.dispose();
        if (fbo != null) fbo.dispose();
    }
}
