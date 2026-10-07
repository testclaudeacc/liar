package com.teste.game.telas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.teste.game.mapa.MapaMundo;

/**
 * Minimapa: o mapa de verdade (MapaMundo.desenharMiniMapa) em miniatura,
 * centrado no jogador, com pontinhos por cima - voce branco, players azul,
 * NPC amarelo, mob vermelho. O mesmo Actor serve de mapa grande (tela
 * cheia): ai' da' pra arrastar e mudar o zoom (ver WorldScreen).
 *
 * Desenha numa projecao propria em cima da area do Actor, cortado com
 * scissor - o resto da UI continua no batch normal do stage.
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

    public static final Color COR_VOCE = Color.WHITE;
    public static final Color COR_PLAYER = new Color(0.35f, 0.75f, 1f, 1f);
    public static final Color COR_NPC = new Color(1f, 0.85f, 0.15f, 1f);
    public static final Color COR_MOB = new Color(0.95f, 0.15f, 0.15f, 1f);
    private static final Color COR_BORDA = new Color(83 / 255f, 83 / 255f, 83 / 255f, 1f);
    private static final Color COR_FUNDO = new Color(0.04f, 0.04f, 0.04f, 1f);

    private final MapaMundo mapa;
    private final Fonte fonte;
    private final Texture pixel;
    private final float borda;
    /** Quantos SQMs cabem na largura. */
    private float tilesVisiveis;
    private final float tilesMin, tilesMax;
    /** Deslocamento do centro (mapa grande arrastado), em pixels do mundo. */
    private float panX = 0f, panY = 0f;
    private final boolean arrastavel;

    private final Matrix4 projecao = new Matrix4();
    private final Rectangle area = new Rectangle();
    private final Rectangle scissor = new Rectangle();
    private final Vector2 tmp = new Vector2();

    public MiniMapa(MapaMundo mapa, Fonte fonte, float tilesVisiveis, float tilesMin, float tilesMax,
                    boolean arrastavel, float borda) {
        this.mapa = mapa;
        this.fonte = fonte;
        this.tilesVisiveis = tilesVisiveis;
        this.tilesMin = tilesMin;
        this.tilesMax = tilesMax;
        this.arrastavel = arrastavel;
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
                    float s = escalaMundo();
                    panX -= (x - ultimoX) / s;
                    panY -= (y - ultimoY) / s;
                    limitarPan();
                    ultimoX = x;
                    ultimoY = y;
                }
                @Override public boolean scrolled(InputEvent e, float x, float y, float sx, float sy) {
                    zoom(sy > 0 ? 1.25f : 0.8f);
                    return true;
                }
                @Override public void enter(InputEvent e, float x, float y, int pointer, Actor de) {
                    if (getStage() != null) getStage().setScrollFocus(MiniMapa.this);
                }
            });
        }
    }

    /** fator > 1 = mostra mais mapa (afasta). */
    public void zoom(float fator) {
        tilesVisiveis = MathUtils.clamp(tilesVisiveis * fator, tilesMin, tilesMax);
        limitarPan();
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

    /** Unidades do stage por pixel do mundo. */
    private float escalaMundo() {
        float interno = Math.max(1f, getWidth() - borda * 2f);
        return interno / (tilesVisiveis * mapa.tileWidth);
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
        float s = escalaMundo();
        float cx = fonte.jogadorX() + panX;
        float cy = fonte.jogadorY() + mapa.tileHeight / 2f + panY;
        float mundoL = aw / s, mundoA = ah / s;

        batch.end();
        area.set(ax, ay, aw, ah);
        getStage().calculateScissors(area, scissor);
        if (ScissorStack.pushScissors(scissor)) {
            projecao.set(getStage().getCamera().combined)
                .translate(ax + aw / 2f, ay + ah / 2f, 0f)
                .scale(s, s, 1f)
                .translate(-cx, -cy, 0f);
            mapa.desenharMiniMapa(projecao, cx - mundoL / 2f, cy - mundoA / 2f, mundoL, mundoA,
                fonte.jogadorX(), fonte.jogadorY());

            batch.begin();
            // Pontinho do tamanho de ~1 SQM, mas nunca sumindo nem ficando enorme.
            float tam = MathUtils.clamp(s * mapa.tileWidth * 0.8f, 3.5f, 9f);
            float centroX = ax + aw / 2f, centroY = ay + ah / 2f;
            Coletor coletor = (mx, my, cor) -> {
                float px = centroX + (mx - cx) * s;
                // y dos personagens = pes (base do SQM): sobe meio SQM pro meio dele.
                float py = centroY + (my + mapa.tileHeight / 2f - cy) * s;
                if (px < ax - tam || px > ax + aw + tam || py < ay - tam || py > ay + ah + tam) return;
                desenharPonto(batch, px, py, tam, cor, parentAlpha);
            };
            fonte.pontos(coletor);
            coletor.ponto(fonte.jogadorX(), fonte.jogadorY(), COR_VOCE);
            batch.flush();
            ScissorStack.popScissors();
        } else {
            batch.begin();
        }
        batch.setColor(Color.WHITE);
    }

    private void desenharPonto(Batch batch, float x, float y, float tam, Color cor, float alpha) {
        // Contorno preto de 1 unidade pra ler em cima de qualquer chao.
        float c = Math.max(1f, tam * 0.2f);
        batch.setColor(0f, 0f, 0f, alpha);
        batch.draw(pixel, x - tam / 2f - c, y - tam / 2f - c, tam + c * 2f, tam + c * 2f);
        batch.setColor(cor.r, cor.g, cor.b, cor.a * alpha);
        batch.draw(pixel, x - tam / 2f, y - tam / 2f, tam, tam);
    }

    public void dispose() {
        pixel.dispose();
    }
}
