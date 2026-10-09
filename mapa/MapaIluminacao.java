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
 * estatico/dinamico).
 *
 * Dia e noite: o servidor manda a hora do ciclo (world_time, 20 min) e
 * corDoCiclo() da' a cor ambiente de cada momento (dia sem escurecer, tarde
 * dourada, noite azul escura). Caverna (camada "Caves") ignora o ciclo: fica
 * sempre em AMBIENTE_CAVERNA, com uma transicao suave ao entrar/sair.
 */
public class MapaIluminacao implements Disposable {

    /** Escurao das cavernas (sempre igual, sem dia/noite) - era a noite
     * eterna de antes, um pouco mais escura. */
    public static final Color AMBIENTE_PADRAO = new Color(0.16f, 0.16f, 0.23f, 1f);
    public static final Color AMBIENTE_CAVERNA = AMBIENTE_PADRAO;

    // ---- Ciclo (segundos dentro dos 20 min) ----
    // 0-420 dia, 420-780 tarde, 780-1200 noite. As trocas acontecem no FIM
    // da fase anterior, entao cada fase ja' comeca com a cor dela (/time
    // afternoon = tarde dourada na hora, /time night = noite na hora).
    public static final float CICLO_SEG = 1200f;
    private static final Color DIA = new Color(1f, 1f, 1f, 1f);
    private static final Color AMANHECER = new Color(0.62f, 0.55f, 0.68f, 1f);
    private static final Color TARDE = new Color(1f, 0.84f, 0.62f, 1f);
    private static final Color CREPUSCULO = new Color(0.72f, 0.42f, 0.42f, 1f);
    private static final Color NOITE = new Color(0.13f, 0.14f, 0.24f, 1f);
    private static final float[] MARCAS = {0f, 360f, 420f, 690f, 735f, 780f, 1110f, 1155f, 1200f};
    private static final Color[] CORES = {DIA, DIA, TARDE, TARDE, CREPUSCULO, NOITE, NOITE, AMANHECER, DIA};
    private static final float TRANSICAO_CAVERNA_SEG = 1.5f;

    private float tempoCiclo = 100f; // ate' o servidor mandar a hora: dia
    private float fatorCaverna = -1f; // 0 = campo, 1 = caverna; -1 = ainda nao sabe

    private final Texture texturaLuz;
    private final TextureRegion regiaoLuz;
    private FrameBuffer fbo;
    private TextureRegion fboRegiao;
    private int fboLargura = -1, fboAltura = -1;

    public Color corAmbiente = new Color(AMBIENTE_PADRAO);

    /** Hora do ciclo vinda do servidor (world_time). */
    public void definirTempoCiclo(float segundos) {
        tempoCiclo = ((segundos % CICLO_SEG) + CICLO_SEG) % CICLO_SEG;
    }

    public float tempoCiclo() { return tempoCiclo; }

    /** Cor ambiente do campo naquele momento do ciclo. */
    public static Color corDoCiclo(float t, Color saida) {
        for (int i = 0; i < MARCAS.length - 1; i++) {
            if (t <= MARCAS[i + 1]) {
                float f = (t - MARCAS[i]) / (MARCAS[i + 1] - MARCAS[i]);
                // Suave nas pontas (sem "quina" na troca de fase).
                f = f * f * (3f - 2f * f);
                return saida.set(CORES[i]).lerp(CORES[i + 1], f);
            }
        }
        return saida.set(CORES[0]);
    }

    /** 1x por frame: anda o relogio e mistura com a caverna (transicao suave
     * na entrada/saida). */
    public void atualizar(float delta, boolean naCaverna) {
        tempoCiclo = (tempoCiclo + delta) % CICLO_SEG;
        float alvo = naCaverna ? 1f : 0f;
        if (fatorCaverna < 0f) fatorCaverna = alvo;
        float passo = delta / TRANSICAO_CAVERNA_SEG;
        fatorCaverna = fatorCaverna < alvo ? Math.min(alvo, fatorCaverna + passo) : Math.max(alvo, fatorCaverna - passo);
        corDoCiclo(tempoCiclo, corAmbiente).lerp(AMBIENTE_CAVERNA, fatorCaverna);
    }

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
