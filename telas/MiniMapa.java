package com.teste.game.telas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.teste.game.mapa.MapaMundo;

/**
 * Minimapa: o mapa com 1 pixel por SQM (Pintura, cor media do tile),
 * centrado no jogador, com 1 SQM colorido por personagem por cima - voce branco, players azul,
 * NPC amarelo, mob vermelho. O mesmo Actor serve de mapa grande (tela
 * cheia): ai' da' pra arrastar e mudar o zoom (ver WorldScreen).
 *
 * Cortado com scissor na area do Actor.
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
    // Outros players so' aparecem se estiverem na party, na cor da classe.
    public static final Color COR_KNIGHT = new Color(0.2f, 0.9f, 0.95f, 1f);  // ciano
    public static final Color COR_RANGER = new Color(0.25f, 0.85f, 0.25f, 1f); // verde
    public static final Color COR_MAGE = new Color(0.8f, 0.6f, 1f, 1f);        // roxo claro
    public static final Color COR_BARD = new Color(1f, 0.9f, 0.15f, 1f);       // amarelo
    // Laranja (amarelo agora e' do bard).
    public static final Color COR_NPC = new Color(1f, 0.55f, 0.1f, 1f);

    public static Color corDaClasse(String classe) {
        if ("Mage".equals(classe)) return COR_MAGE;
        if ("Ranger".equals(classe)) return COR_RANGER;
        if ("Bard".equals(classe)) return COR_BARD;
        return COR_KNIGHT;
    }
    public static final Color COR_MOB = new Color(0.95f, 0.15f, 0.15f, 1f);
    private static final Color COR_BORDA = new Color(83 / 255f, 83 / 255f, 83 / 255f, 1f);
    private static final Color COR_FUNDO = new Color(0.04f, 0.04f, 0.04f, 1f);

    private final MapaMundo mapa;
    private final Pintura pintura;
    private final Fonte fonte;
    private final Texture pixel;
    private final float borda;
    /** Quantos SQMs cabem na largura. */
    private float tilesVisiveis;
    private final float tilesMin, tilesMax;
    /** Deslocamento do centro (mapa grande arrastado), em pixels do mundo. */
    private float panX = 0f, panY = 0f;
    private final boolean arrastavel;

    private final Rectangle area = new Rectangle();
    private final Rectangle scissor = new Rectangle();
    private final Vector2 tmp = new Vector2();

    public MiniMapa(MapaMundo mapa, Pintura pintura, Fonte fonte, float tilesVisiveis, float tilesMin, float tilesMax,
                    boolean arrastavel, float borda) {
        this.mapa = mapa;
        this.pintura = pintura;
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

    /** Unidades do stage por pixel do mundo. SQM sempre com um numero
     * INTEIRO de pixels de tela (os "pixels" do minimapa ficam todos iguais). */
    private float escalaMundo() {
        float interno = Math.max(1f, getWidth() - borda * 2f);
        float k = pxTelaPorUnidade();
        float sqmPx = Math.max(1f, Math.round(interno / tilesVisiveis * k));
        return sqmPx / k / mapa.tileWidth;
    }

    private float pxTelaPorUnidade() {
        if (getStage() == null) return 1f;
        com.badlogic.gdx.utils.viewport.Viewport v = getStage().getViewport();
        return v.getScreenWidth() / v.getWorldWidth();
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
        float s = escalaMundo();
        float sqm = mapa.tileWidth * s; // 1 SQM em unidades do stage
        float cx = fonte.jogadorX() + panX;
        float cy = fonte.jogadorY() + mapa.tileHeight / 2f + panY;
        // Origem do mapa (canto de baixo a esquerda) presa na grade de pixels da tela.
        float origemX = Math.round((ax + aw / 2f - cx * s) * k) / k;
        float origemY = Math.round((ay + ah / 2f - cy * s) * k) / k;

        area.set(ax, ay, aw, ah);
        batch.flush();
        getStage().calculateScissors(area, scissor);
        if (ScissorStack.pushScissors(scissor)) {
            batch.setColor(1f, 1f, 1f, parentAlpha);
            for (Pintura.Pedaco p : pintura.pedacos) {
                if (p.textura == null) continue;
                batch.draw(p.textura, origemX + p.tileX * sqm, origemY + p.tileY * sqm, p.largura * sqm, p.altura * sqm);
            }
            // Cada um = 1 "pixel" do minimapa (tamanho de 1 SQM), sem contorno.
            // Na posicao continua (nao presa no SQM): anda liso junto com o
            // mapa e voce fica sempre no centro, sem pular de SQM em SQM.
            Coletor coletor = (mx, my, cor) -> {
                float px = Math.round((origemX + (mx - mapa.tileWidth / 2f) * s) * k) / k;
                float py = Math.round((origemY + my * s) * k) / k;
                if (px + sqm < ax || px > ax + aw || py + sqm < ay || py > ay + ah) return;
                batch.setColor(cor.r, cor.g, cor.b, cor.a * parentAlpha);
                batch.draw(pixel, px, py, sqm, sqm);
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
     * O mapa inteiro "pintado" com 1 pixel por SQM (MapaMundo.gerarPixmapMiniMapa:
     * cor media do tile mais de cima), gerado uma vez e compartilhado entre o
     * minimapa e o mapa grande. Em pedacos de ate' 1024x1024 (limite de
     * textura de celular). Desenhado com Nearest: ampliado, cada SQM vira um
     * quadrado de cor solida.
     */
    public static final class Pintura {
        static final class Pedaco {
            final int tileX, tileY, largura, altura, linhaPixmap;
            Texture textura;
            Pedaco(int tileX, int tileY, int largura, int altura, int linhaPixmap) {
                this.tileX = tileX; this.tileY = tileY; this.largura = largura; this.altura = altura;
                this.linhaPixmap = linhaPixmap;
            }
        }

        private static final int MAX = 1024;
        private final Pixmap pixmap; // guardado: recria as texturas se o GL for recriado (Android)
        final java.util.List<Pedaco> pedacos = new java.util.ArrayList<>();

        public Pintura(Pixmap pixmap) {
            this.pixmap = pixmap;
            int w = pixmap.getWidth(), h = pixmap.getHeight();
            for (int linha = 0; linha < h; linha += MAX) {
                int ph = Math.min(MAX, h - linha);
                // Linha 0 do pixmap = topo do mapa; tileY conta de baixo pra cima.
                for (int col = 0; col < w; col += MAX) {
                    pedacos.add(new Pedaco(col, h - linha - ph, Math.min(MAX, w - col), ph, linha));
                }
            }
            criarTexturas();
        }

        private void criarTexturas() {
            for (Pedaco p : pedacos) {
                Pixmap parte = new Pixmap(p.largura, p.altura, Pixmap.Format.RGBA8888);
                parte.setBlending(Pixmap.Blending.None);
                parte.drawPixmap(pixmap, 0, 0, p.tileX, p.linhaPixmap, p.largura, p.altura);
                p.textura = new Texture(parte);
                p.textura.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                parte.dispose();
            }
        }

        /** Android recriou o contexto GL: as texturas voltam vazias. */
        public void recriar() {
            for (Pedaco p : pedacos) if (p.textura != null) p.textura.dispose();
            criarTexturas();
        }

        public void dispose() {
            for (Pedaco p : pedacos) if (p.textura != null) p.textura.dispose();
            pixmap.dispose();
        }
    }
}
