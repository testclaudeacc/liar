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
    // Tarde: sol se pondo (laranja-dourado forte); crepusculo: alaranjado
    // avermelhado ja' escurecendo.
    private static final Color TARDE = new Color(1f, 0.70f, 0.40f, 1f);
    private static final Color CREPUSCULO = new Color(0.80f, 0.40f, 0.30f, 1f);
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

    /** Options > Lighting quality: 0 High (resolucao cheia), 1 Medium (1/2),
     * 2 Low (1/4), 3 Off (sem luzes: so' a cor do dia/noite por cima). */
    public int qualidade = 0;
    private Texture texturaBranca;
    private boolean passouLuzNesteFrame = false;

    /** Dia claro fora da caverna: a cor ambiente e' branca e luz nenhuma muda
     * nada (multiplicar por branco) - pula o FrameBuffer e o composite inteiros. */
    private boolean semEfeito() {
        return corAmbiente.r >= 0.995f && corAmbiente.g >= 0.995f && corAmbiente.b >= 0.995f;
    }

    /** Hora do ciclo vinda do servidor (world_time). */
    public void definirTempoCiclo(float segundos) {
        tempoCiclo = ((segundos % CICLO_SEG) + CICLO_SEG) % CICLO_SEG;
    }

    public float tempoCiclo() { return tempoCiclo; }

    private final Color corCicloTmp = new Color();

    /** 0 = dia claro, 1 = noite fechada (pelo brilho da cor do ciclo): o que
     * e' appear_at_night vai aparecendo enquanto escurece e some no amanhecer. */
    public float fatorNoite() {
        corDoCiclo(tempoCiclo, corCicloTmp);
        float brilho = (corCicloTmp.r + corCicloTmp.g + corCicloTmp.b) / 3f;
        float noite = (NOITE.r + NOITE.g + NOITE.b) / 3f;
        float f = (1f - brilho) / (1f - noite);
        // A tarde dourada ja' e' ~0.36 escura: so' comeca a aparecer depois dela
        // (crepusculo), cheio na noite, e some durante o amanhecer.
        f = (f - 0.36f) / (1f - 0.36f);
        return Math.max(0f, Math.min(1f, f));
    }

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
    public void renderizar(Batch batch, OrthographicCamera camera, List<MapaPropriedades.Luz> luzes,
                           List<MapaPropriedades.Luz> luzesMagia) {
        // Resolucao cheia (a pedido do usuario: em meia resolucao a borda da
        // luz perdia os pixels). O que pesava era o mapa trocando de textura
        // (ver UnificadorTilesets) e as luzes fora da tela, ja' resolvidos.
        passouLuzNesteFrame = false;
        if (semEfeito() || qualidade >= 3) return;
        int div = qualidade == 1 ? 2 : qualidade == 2 ? 4 : 1;
        garantirFbo(Math.max(1, Gdx.graphics.getBackBufferWidth() / div), Math.max(1, Gdx.graphics.getBackBufferHeight() / div));
        passouLuzNesteFrame = true;
        // Luzes de magia primeiro (FBO proprio, sem somar entre si) - antes do
        // fbo.begin(): FrameBuffer nao aninha.
        boolean comMagia = renderizarMax(batch, camera, luzesMagia);

        fbo.begin();
        Gdx.gl.glClearColor(corAmbiente.r, corAmbiente.g, corAmbiente.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        // So' as luzes que encostam na tela (antes ia o mapa inteiro todo frame).
        float meiaL = camera.viewportWidth * camera.zoom / 2f, meiaA = camera.viewportHeight * camera.zoom / 2f;
        float cx = camera.position.x, cy = camera.position.y;
        for (MapaPropriedades.Luz luz : luzes) {
            if (Math.abs(luz.x - cx) > meiaL + luz.raio || Math.abs(luz.y - cy) > meiaA + luz.raio) continue;
            if (luz.soNoite) {
                float f = fatorNoite();
                if (f <= 0.01f) continue;
                batch.setColor(luz.cor.r, luz.cor.g, luz.cor.b, luz.cor.a * f);
            } else batch.setColor(luz.cor);
            float diametro = luz.raio * 2f;
            batch.draw(regiaoLuz, luz.x - luz.raio, luz.y - luz.raio, diametro, diametro);
        }
        batch.setColor(Color.WHITE);
        if (comMagia) {
            batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
            desenharNaTela(batch, camera, fboMaxRegiao);
        }
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        fbo.end();
    }

    /** Multiplica o FrameBuffer de luz por cima do que ja foi desenhado na
     * tela nesse frame - chamar por ULTIMO, depois de tudo (mapa, player,
     * nome) e antes da UI (HUD/chat nao devem escurecer). */
    public void compositar(Batch batch, OrthographicCamera camera) {
        if (semEfeito()) return;
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        batch.begin();
        float meiaLargura = camera.viewportWidth * camera.zoom / 2f;
        float meiaAltura = camera.viewportHeight * camera.zoom / 2f;
        if (passouLuzNesteFrame && fboRegiao != null) {
            batch.draw(fboRegiao, camera.position.x - meiaLargura, camera.position.y - meiaAltura,
                    meiaLargura * 2f, meiaAltura * 2f);
        } else {
            // Off: so' escurece com a cor do momento (noite/caverna), sem as luzes.
            if (texturaBranca == null) {
                Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                px.setColor(Color.WHITE);
                px.fill();
                texturaBranca = new Texture(px);
                px.dispose();
            }
            batch.setColor(corAmbiente);
            batch.draw(texturaBranca, camera.position.x - meiaLargura, camera.position.y - meiaAltura,
                    meiaLargura * 2f, meiaAltura * 2f);
            batch.setColor(Color.WHITE);
        }
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Brilho por cima da cena (aditivo): luz de magia que aparece ate' de
     * dia (o mapa de luz multiplicativo nao clareia alem do branco). Chamar
     * depois do compositar(). Luzes que se cruzam nao somam (ver renderizarMax). */
    public void brilhoAditivo(Batch batch, OrthographicCamera camera, List<MapaPropriedades.Luz> luzes) {
        if (!renderizarMax(batch, camera, luzes)) return;
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
        batch.begin();
        desenharNaTela(batch, camera, fboMaxRegiao);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    // ---- Luzes de magia sem somar ----
    // Duas AOEs lado a lado nao ficam mais fortes onde se cruzam: cada uma vai
    // num FBO (1/4 da tela, glow e' borrado mesmo) com blend MAX - fica a mais
    // forte de cada pixel - e esse FBO e' somado uma vez so' no mapa de luz /
    // na cena. Textura radial propria com a cor ja' multiplicada (o MAX ignora
    // o alfa). Sem suporte a MAX (GLES2 antigo sem a extensao): soma, como antes.
    private static final int GL_MAX = 0x8008, GL_FUNC_ADD = 0x8006;
    private FrameBuffer fboMax;
    private TextureRegion fboMaxRegiao;
    private int fboMaxL = -1, fboMaxA = -1;
    private Texture texturaRadial;
    private TextureRegion regiaoRadial;
    private Boolean suportaMax;

    private boolean suportaMax() {
        if (suportaMax == null) {
            suportaMax = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop
                || Gdx.gl30 != null || Gdx.graphics.supportsExtension("GL_EXT_blend_minmax");
        }
        return suportaMax;
    }

    private void garantirRadial() {
        if (texturaRadial != null) return;
        int t = 64;
        Pixmap px = new Pixmap(t, t, Pixmap.Format.RGBA8888);
        float c = (t - 1) / 2f;
        for (int y = 0; y < t; y++) {
            for (int x = 0; x < t; x++) {
                float d = (float) Math.sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c;
                float v = Math.max(0f, 1f - d);
                v = v * v * (3f - 2f * v);
                px.drawPixel(x, y, Color.rgba8888(v, v, v, 1f));
            }
        }
        texturaRadial = new Texture(px);
        texturaRadial.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        regiaoRadial = new TextureRegion(texturaRadial);
        px.dispose();
    }

    /** Desenha as luzes no fboMax (fundo preto). false = nada pra desenhar. */
    private boolean renderizarMax(Batch batch, OrthographicCamera camera, List<MapaPropriedades.Luz> luzes) {
        if (luzes == null || luzes.isEmpty()) return false;
        garantirRadial();
        int l = Math.max(1, Gdx.graphics.getBackBufferWidth() / 4), a = Math.max(1, Gdx.graphics.getBackBufferHeight() / 4);
        if (fboMax == null || l != fboMaxL || a != fboMaxA) {
            if (fboMax != null) fboMax.dispose();
            fboMaxL = l; fboMaxA = a;
            fboMax = new FrameBuffer(Pixmap.Format.RGBA8888, l, a, false);
            fboMax.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            fboMaxRegiao = new TextureRegion(fboMax.getColorBufferTexture(), 0, 0, l, a);
            fboMaxRegiao.flip(false, true);
        }
        boolean max = suportaMax();
        fboMax.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
        if (max) Gdx.gl.glBlendEquation(GL_MAX);
        batch.begin();
        for (MapaPropriedades.Luz luz : luzes) {
            float k = luz.cor.a;
            batch.setColor(luz.cor.r * k, luz.cor.g * k, luz.cor.b * k, 1f);
            float diametro = luz.raio * 2f;
            batch.draw(regiaoRadial, luz.x - luz.raio, luz.y - luz.raio, diametro, diametro);
        }
        batch.setColor(Color.WHITE);
        batch.end();
        if (max) Gdx.gl.glBlendEquation(GL_FUNC_ADD);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        fboMax.end();
        return true;
    }

    private void desenharNaTela(Batch batch, OrthographicCamera camera, TextureRegion regiao) {
        float meiaL = camera.viewportWidth * camera.zoom / 2f, meiaA = camera.viewportHeight * camera.zoom / 2f;
        batch.draw(regiao, camera.position.x - meiaL, camera.position.y - meiaA, meiaL * 2f, meiaA * 2f);
    }

    @Override
    public void dispose() {
        texturaLuz.dispose();
        if (fbo != null) fbo.dispose();
        if (texturaBranca != null) texturaBranca.dispose();
        if (fboMax != null) fboMax.dispose();
        if (texturaRadial != null) texturaRadial.dispose();
    }
}
