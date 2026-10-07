package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.teste.game.mapa.MapaMundo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimapa: o mapa de verdade "pintado" em 4x4 pixels por SQM (cada pixel =
 * media de 4x4 pixels do tile, ver Cache), com pontinhos por cima - voce
 * branco, players azul, NPC amarelo, mob vermelho. O mesmo Actor serve de
 * mapa grande (tela cheia, arrastavel).
 *
 * Zoom = quantos pixels de TELA cada pixel do minimapa ocupa (inteiro): dar
 * zoom so' amplia os mesmos pixels, igual uma lupa numa pintura - nunca
 * aparece mais detalhe.
 */
public class MiniMapa extends Actor {

    /** De onde vem o centro (jogador) e os pontinhos - WorldScreen. */
    public interface Fonte {
        float jogadorX();
        float jogadorY();
        void pontos(Coletor c);
    }

    public interface Coletor {
        void ponto(float mundoX, float mundoY, Color cor);
    }

    public static final int PX_POR_SQM = 4;
    public static final int ZOOM_MIN = 1, ZOOM_MAX = 12;

    public static final Color COR_VOCE = Color.WHITE;
    public static final Color COR_PLAYER = new Color(0.35f, 0.75f, 1f, 1f);
    public static final Color COR_NPC = new Color(1f, 0.85f, 0.15f, 1f);
    public static final Color COR_MOB = new Color(0.95f, 0.15f, 0.15f, 1f);
    private static final Color COR_BORDA = new Color(83 / 255f, 83 / 255f, 83 / 255f, 1f);
    private static final Color COR_FUNDO = new Color(0.04f, 0.04f, 0.04f, 1f);

    private final MapaMundo mapa;
    private final Cache cache;
    private final Fonte fonte;
    private final Texture pixel;
    private final float borda;
    /** Pixels de tela por pixel do minimapa; <= 0 = automatico (~SQMS_AUTO na largura). */
    private int zoom;
    private final float sqmsAuto;
    /** Deslocamento do centro (mapa grande arrastado), em pixels do mundo. */
    private float panX = 0f, panY = 0f;

    private final Rectangle area = new Rectangle();
    private final Rectangle scissor = new Rectangle();
    private final Vector2 tmp = new Vector2();

    /** zoom <= 0: escolhe sozinho o zoom inteiro que mostra ~sqmsAuto SQMs na largura. */
    public MiniMapa(MapaMundo mapa, Cache cache, Fonte fonte, int zoom, float sqmsAuto, boolean arrastavel, float borda) {
        this.mapa = mapa;
        this.cache = cache;
        this.fonte = fonte;
        this.zoom = zoom;
        this.sqmsAuto = sqmsAuto;
        this.borda = borda;
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
        if (arrastavel) {
            addListener(new InputListener() {
                float ultimoX, ultimoY;
                @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, int botao) {
                    ultimoX = x;
                    ultimoY = y;
                    return true;
                }
                @Override public void touchDragged(InputEvent e, float x, float y, int pointer) {
                    float s = unidadesPorPxMundo();
                    panX -= (x - ultimoX) / s;
                    panY -= (y - ultimoY) / s;
                    limitarPan();
                    ultimoX = x;
                    ultimoY = y;
                }
                @Override public boolean scrolled(InputEvent e, float x, float y, float sx, float sy) {
                    mudarZoom(sy > 0 ? -1 : 1);
                    return true;
                }
                @Override public void enter(InputEvent e, float x, float y, int pointer, Actor de) {
                    if (getStage() != null) getStage().setScrollFocus(MiniMapa.this);
                }
            });
        }
    }

    /** +1 = aproxima (pixels maiores), -1 = afasta. */
    public void mudarZoom(int passo) {
        zoom = MathUtils.clamp(zoomAtual() + passo, ZOOM_MIN, ZOOM_MAX);
    }

    public void centralizar() {
        panX = panY = 0f;
    }

    private void limitarPan() {
        // Nao deixa o centro sair do mapa.
        float jx = fonte.jogadorX(), jy = fonte.jogadorY();
        panX = MathUtils.clamp(panX, -jx, mapa.larguraPx() - jx);
        panY = MathUtils.clamp(panY, -jy, mapa.alturaPx() - jy);
    }

    /** Pixels de tela por unidade do stage. */
    private float pxTelaPorUnidade() {
        Viewport v = getStage().getViewport();
        return v.getScreenWidth() / v.getWorldWidth();
    }

    private int zoomAtual() {
        if (zoom > 0 || getStage() == null) return Math.max(zoom, ZOOM_MIN);
        float larguraTela = (getWidth() - borda * 2f) * pxTelaPorUnidade();
        return MathUtils.clamp(Math.round(larguraTela / (sqmsAuto * PX_POR_SQM)), ZOOM_MIN, ZOOM_MAX);
    }

    /** Pixels do mundo que cabem em 1 pixel do minimapa (16/4 = 4). */
    private float mundoPorPx() {
        return mapa.tileWidth / (float) PX_POR_SQM;
    }

    /** Unidades do stage por pixel do mundo. */
    private float unidadesPorPxMundo() {
        return zoomAtual() / pxTelaPorUnidade() / mundoPorPx();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        // Moldura + fundo.
        batch.setColor(COR_BORDA.r, COR_BORDA.g, COR_BORDA.b, parentAlpha);
        batch.draw(pixel, getX(), getY(), getWidth(), getHeight());
        batch.setColor(COR_FUNDO.r, COR_FUNDO.g, COR_FUNDO.b, parentAlpha);
        batch.draw(pixel, getX() + borda, getY() + borda, getWidth() - borda * 2f, getHeight() - borda * 2f);
        batch.setColor(Color.WHITE);
        if (getStage() == null) return;

        localToStageCoordinates(tmp.set(borda, borda));
        float ax = tmp.x, ay = tmp.y;
        float aw = getWidth() - borda * 2f, ah = getHeight() - borda * 2f;
        if (aw <= 0f || ah <= 0f) return;

        float k = pxTelaPorUnidade();
        float pxMini = zoomAtual() / k;          // 1 pixel do minimapa, em unidades do stage
        float mpp = mundoPorPx();                // pixels do mundo por pixel do minimapa
        float s = pxMini / mpp;                  // unidades do stage por pixel do mundo
        // Centro preso na grade de pixels do minimapa (e o meio da area na
        // grade da tela): os pixels nao "escorregam" nem borram ao andar.
        float cx = Math.round((fonte.jogadorX() + panX) / mpp) * mpp;
        float cy = Math.round((fonte.jogadorY() + mapa.tileHeight / 2f + panY) / mpp) * mpp;
        float centroX = Math.round((ax + aw / 2f) * k) / k;
        float centroY = Math.round((ay + ah / 2f) * k) / k;

        // Blocos que aparecem.
        float blocoMundoL = Cache.BLOCO * mapa.tileWidth, blocoMundoA = Cache.BLOCO * mapa.tileHeight;
        float esq = cx - (aw / 2f) / s, dir = cx + (aw / 2f) / s;
        float baixo = cy - (ah / 2f) / s, cima = cy + (ah / 2f) / s;
        int bx0 = Math.max(0, (int) Math.floor(esq / blocoMundoL));
        int bx1 = Math.min(cache.blocosX - 1, (int) Math.floor(dir / blocoMundoL));
        int by0 = Math.max(0, (int) Math.floor(baixo / blocoMundoA));
        int by1 = Math.min(cache.blocosY - 1, (int) Math.floor(cima / blocoMundoA));

        batch.end();
        // Gera os blocos que faltam ANTES do scissor (o scissor cortaria o FBO).
        cache.gerarFaltando(bx0, bx1, by0, by1, cx / blocoMundoL, cy / blocoMundoA);
        getStage().getViewport().apply();

        area.set(ax, ay, aw, ah);
        getStage().calculateScissors(area, scissor);
        batch.begin();
        if (ScissorStack.pushScissors(scissor)) {
            batch.setColor(1f, 1f, 1f, parentAlpha);
            for (int by = by0; by <= by1; by++) {
                for (int bx = bx0; bx <= bx1; bx++) {
                    Texture t = cache.textura(bx, by);
                    if (t == null) continue;
                    float x = centroX + (bx * blocoMundoL - cx) * s;
                    float y = centroY + (by * blocoMundoA - cy) * s;
                    // FBO guarda de cabeca pra baixo: flipY.
                    batch.draw(t, x, y, blocoMundoL * s, blocoMundoA * s, 0, 0, t.getWidth(), t.getHeight(), false, true);
                }
            }

            // Pontinho = 2x2 pixels do minimapa no meio do SQM, contorno preto
            // de 1 pixel (4x4 no total = 1 SQM).
            Coletor coletor = (mx, my, cor) -> {
                int dx = Math.round((mx - cx) / mpp);
                int dy = Math.round((my + mapa.tileHeight / 2f - cy) / mpp);
                float px = centroX + dx * pxMini, py = centroY + dy * pxMini;
                if (px < ax - pxMini * 2f || px > ax + aw + pxMini * 2f || py < ay - pxMini * 2f || py > ay + ah + pxMini * 2f) return;
                batch.setColor(0f, 0f, 0f, parentAlpha);
                batch.draw(pixel, px - pxMini * 2f, py - pxMini * 2f, pxMini * 4f, pxMini * 4f);
                batch.setColor(cor.r, cor.g, cor.b, cor.a * parentAlpha);
                batch.draw(pixel, px - pxMini, py - pxMini, pxMini * 2f, pxMini * 2f);
            };
            fonte.pontos(coletor);
            coletor.ponto(fonte.jogadorX(), fonte.jogadorY(), COR_VOCE);
            batch.flush();
            ScissorStack.popScissors();
        }
        batch.setColor(Color.WHITE);
    }

    public void dispose() {
        pixel.dispose();
    }

    /**
     * Pintura do mapa em blocos de BLOCO x BLOCO SQMs, 4x4 pixels por SQM,
     * gerada sob demanda e guardada (compartilhada entre minimapa e mapa
     * grande). Cada bloco: desenha o mapa no tamanho real (16px/SQM) num FBO
     * e reduz pela metade 2x com filtro Linear - cada pixel final e' a media
     * exata dos 4x4 pixels de origem (cor "pintada", sem chuvisco).
     * Animacoes ficam congeladas no quadro em que o bloco foi gerado.
     */
    public static final class Cache {
        static final int BLOCO = 32;
        private static final int MAX_BLOCOS = 600;
        private static final int GERAR_POR_FRAME = 3;

        private final MapaMundo mapa;
        final int blocosX, blocosY;
        private final Map<Long, FrameBuffer> blocos = new LinkedHashMap<>(64, 0.75f, true); // LRU
        private FrameBuffer fboReal, fboMeio;
        private SpriteBatch batchReducao;
        private final Matrix4 projecao = new Matrix4();
        private final List<long[]> faltando = new ArrayList<>();

        public Cache(MapaMundo mapa) {
            this.mapa = mapa;
            blocosX = (mapa.mapaLarguraTiles + BLOCO - 1) / BLOCO;
            blocosY = (mapa.mapaAlturaTiles + BLOCO - 1) / BLOCO;
        }

        private static long chave(int bx, int by) {
            return ((long) bx << 32) | (by & 0xffffffffL);
        }

        Texture textura(int bx, int by) {
            FrameBuffer f = blocos.get(chave(bx, by));
            return f != null ? f.getColorBufferTexture() : null;
        }

        /** Gera ate' GERAR_POR_FRAME blocos que faltam, os mais perto do centro primeiro. */
        void gerarFaltando(int bx0, int bx1, int by0, int by1, float centroBx, float centroBy) {
            faltando.clear();
            for (int by = by0; by <= by1; by++)
                for (int bx = bx0; bx <= bx1; bx++)
                    if (!blocos.containsKey(chave(bx, by))) faltando.add(new long[]{bx, by});
            if (faltando.isEmpty()) return;
            faltando.sort((a, b) -> Float.compare(dist(a, centroBx, centroBy), dist(b, centroBx, centroBy)));
            for (int i = 0; i < Math.min(GERAR_POR_FRAME, faltando.size()); i++) {
                gerar((int) faltando.get(i)[0], (int) faltando.get(i)[1]);
            }
        }

        private static float dist(long[] b, float cx, float cy) {
            float dx = b[0] + 0.5f - cx, dy = b[1] + 0.5f - cy;
            return dx * dx + dy * dy;
        }

        private void gerar(int bx, int by) {
            int real = BLOCO * mapa.tileWidth;             // 16 px/SQM
            int meio = real / 2;                           // 8 px/SQM
            int fim = BLOCO * PX_POR_SQM;                  // 4 px/SQM
            if (fboReal == null) {
                fboReal = new FrameBuffer(Pixmap.Format.RGBA8888, real, real, false);
                fboReal.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                fboMeio = new FrameBuffer(Pixmap.Format.RGBA8888, meio, meio, false);
                fboMeio.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                batchReducao = new SpriteBatch(4);
            }
            float x0 = bx * real, y0 = by * real;

            // 1) Mapa no tamanho real.
            fboReal.begin();
            Gdx.gl.glClearColor(COR_FUNDO.r, COR_FUNDO.g, COR_FUNDO.b, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            projecao.setToOrtho2D(x0, y0, real, real);
            mapa.desenharBlocoMiniMapa(projecao, x0, y0, real, real);
            fboReal.end();

            // 2) e 3) Reduz pela metade duas vezes (media 2x2 de cada vez).
            reduzir(fboReal, fboMeio, meio);
            while (blocos.size() >= MAX_BLOCOS) {
                Long velho = blocos.keySet().iterator().next();
                blocos.remove(velho).dispose();
            }
            FrameBuffer bloco = new FrameBuffer(Pixmap.Format.RGBA8888, fim, fim, false);
            bloco.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            reduzir(fboMeio, bloco, fim);
            blocos.put(chave(bx, by), bloco);
        }

        private void reduzir(FrameBuffer origem, FrameBuffer destino, int tamanho) {
            destino.begin();
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batchReducao.setProjectionMatrix(projecao.setToOrtho2D(0, 0, tamanho, tamanho));
            batchReducao.disableBlending();
            batchReducao.begin();
            Texture t = origem.getColorBufferTexture();
            // flipY: desfaz o "de cabeca pra baixo" do FBO, todos os blocos ficam iguais.
            batchReducao.draw(t, 0, 0, tamanho, tamanho, 0, 0, t.getWidth(), t.getHeight(), false, true);
            batchReducao.end();
            destino.end();
        }

        /** Some com tudo (ex: GL recriado no Android) - gera de novo sob demanda. */
        public void limpar() {
            for (FrameBuffer f : blocos.values()) f.dispose();
            blocos.clear();
        }

        public void dispose() {
            limpar();
            if (fboReal != null) fboReal.dispose();
            if (fboMeio != null) fboMeio.dispose();
            if (batchReducao != null) batchReducao.dispose();
        }
    }
}
