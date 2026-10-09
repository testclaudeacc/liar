package com.teste.game.telas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

/**
 * HUD do canto superior esquerdo, medido pixel a pixel em
 * prints/tela desejada barras.png (1366x768):
 *   painel: borda 2px (83,83,83), fundo (48,48,48); coracao + barra de HP
 *   (198,0,0) e mana + barra de MP (96,153,255), 314x34 cada, com 1px de
 *   sombra (45,45,45) a direita/embaixo e 5px entre elas;
 *   XP: painel 262x34, borda 1px preta, fundo (14,14,14), icone a esquerda
 *   e "%" centralizado no painel todo.
 * Texto: Tahoma Bold branca com contorno preto de 2px.
 * Todas as medidas sao em PIXEL DE TELA e convertidas pra unidade do stage
 * (px()), senao bordas de 1-2px saem com espessura quebrada.
 */
public final class HudVitais {

    private static final Color COR_BORDA_PAINEL = new Color(83 / 255f, 83 / 255f, 83 / 255f, 1f);
    private static final Color COR_PAINEL = new Color(48 / 255f, 48 / 255f, 48 / 255f, 1f);
    private static final Color COR_SOMBRA = new Color(45 / 255f, 45 / 255f, 45 / 255f, 1f);
    private static final Color COR_HP = new Color(198 / 255f, 0f, 0f, 1f);
    private static final Color COR_MP = Color.valueOf("6a3ad8"); // mesmo roxo do arco de MP
    private static final Color COR_XP_FUNDO = new Color(14 / 255f, 14 / 255f, 14 / 255f, 1f);
    private static final Color COR_XP = Color.valueOf("e8b020");
    private static final Color COR_MUNICAO = new Color(0.15f, 0.15f, 0.15f, 1f); // cinza escuro

    private final float escala; // pixels de tela por unidade do stage
    // Tudo aqui e' medido em pixels de tela (da print do PC); no celular, com
    // tela de alta densidade, isso ficava pequeno demais - aumenta tudo junto.
    private static final float FATOR_MOBILE = 1.7f;
    private static final boolean MOBILE = com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
        || com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
    private static final float FATOR = MOBILE ? FATOR_MOBILE : 1f;
    private static final float FATOR_TEXTO = MOBILE ? 1.35f : 1f;
    private final Texture pixel;
    private final BitmapFont fonte;
    private final BitmapFont fonteNotificacao; // menor e com contorno mais fino
    private final Table raiz = new Table();
    private final Table conteudo = new Table();
    private Table linhaTopo;
    private Table painelVitais;
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<?> celulaPainel;
    private float alturaTopo = 0f;

    /** Painel reto de HP/MP: some (sem ocupar lugar) quando as barras sao em arco. */
    public void definirPainelVitaisVisivel(boolean visivel) {
        if ((celulaPainel.getActor() != null) == visivel) return;
        celulaPainel.setActor(visivel ? painelVitais : null);
        linhaTopo.invalidateHierarchy();
        centralizarNoRetrato();
    }

    private void centralizarNoRetrato() {
        // Sem o painel reto (barras em arco): nada pra centralizar, a
        // municao/notificacoes sobem pro topo, do lado do retrato.
        boolean comPainel = celulaPainel != null && celulaPainel.getActor() != null;
        raiz.getCell(conteudo).padTop(comPainel ? Math.max(0f, (alturaTopo - linhaTopo.getPrefHeight()) / 2f) : px(6));
        raiz.invalidate();
    }
    private final com.badlogic.gdx.scenes.scene2d.ui.Container<com.badlogic.gdx.scenes.scene2d.Actor> colunaEsquerda =
        new com.badlogic.gdx.scenes.scene2d.ui.Container<>();
    private final Barra hp, mp, xp;
    // Barrinha cinza de municao (icone da flecha + quantidade), abaixo do XP.
    private final Table painelMunicao = new Table();
    private final Image iconeMunicao = new Image();
    private final Label textoMunicao;
    // Notificacao (copia da barrinha de municao): fica embaixo dela, ou no
    // lugar dela quando nao tem flecha. Uma por vez, com fila.
    private final Table painelNotificacao = new Table();
    private final Image iconeNotificacao = new Image();
    private final Label textoNotificacao;
    private final Table colunaInferior = new Table();
    private final java.util.ArrayDeque<Notificacao> filaNotificacoes = new java.util.ArrayDeque<>();
    private Notificacao notificacaoAtual;
    private final TextureRegion iconeNotificacaoPadrao;
    private TextureRegion iconeNotifTrade, iconeNotifAmigo, iconeNotifParty;
    private Table internoNotif, blocoIconeNotif;
    private float alturaNotif;
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<?> celulaIconeNotif;

    /** Tipo da notificacao: muda o icone e a cor de fundo da caixinha. */
    public static final int NOTIF_AVISO = 0, NOTIF_TRADE = 1, NOTIF_AMIGO = 2, NOTIF_PARTY = 3;
    private static final Color COR_NOTIF_TRADE = new Color(0.42f, 0.33f, 0.05f, 1f); // amarelo escuro
    private static final Color COR_NOTIF_AMIGO = new Color(0.45f, 0.12f, 0.3f, 1f);  // rosa escuro
    private static final Color COR_NOTIF_PARTY = new Color(0.16f, 0.32f, 0.45f, 1f); // azul claro escurecido
    private static final float DURACAO_NOTIFICACAO = 5f;
    private static final int MAX_FILA_NOTIFICACOES = 6;

    private static final class Notificacao {
        final String texto;
        final Color cor;
        final Runnable aoClicar;
        int tipo = NOTIF_AVISO;
        TextureRegion iconeProprio; // notificarIcone: icone/cor de fundo escolhidos por quem chamou
        Color fundoProprio;
        Notificacao(String texto, Color cor, Runnable aoClicar) {
            this.texto = texto;
            this.cor = cor;
            this.aoClicar = aoClicar;
        }
    }
    private float hpAtual = 1f, hpMax = 1f, mpAtual = 1f, mpMax = 1f;

    // Quadradinhos de status a direita da barra de HP: fome (marrom) e battle.
    // Ficam lado a lado nessa ordem; sem a fome, o de battle ocupa o lugar dela.
    private static final Color COR_FOME = new Color(0.36f, 0.22f, 0.12f, 1f);    // marrom
    private static final Color COR_BATALHA = new Color(0.42f, 0.08f, 0.08f, 1f); // vermelho escuro
    private static final Color COR_ZONA_SEGURA = new Color(0.08f, 0.16f, 0.42f, 1f); // azul escuro
    // Titulo de cada dica no tom claro da cor de fundo do icone.
    private static final Color TITULO_FOME = Color.valueOf("d8955a");
    private static final Color TITULO_BATALHA = Color.valueOf("ff6b6b");
    private static final Color TITULO_ZONA_SEGURA = Color.valueOf("6f9bff");
    private final Table iconesStatus = new Table();
    private Table quadradoFome, quadradoBatalha, quadradoZonaSegura;
    private boolean comFome = false, emBatalha = false, naZonaSegura = false;
    // Tempo ate' sair do battle: travado em 30 enquanto luta; o servidor avisa
    // quando o player parou (counting) e daí desce sozinho aqui.
    private float batalhaRestante = 0f;
    private boolean batalhaContando = false; // false = lutando, travado no maximo
    private Table iconeDica; // icone cujo popup esta aberto
    private final Table dica = new Table() {
        @Override public void act(float delta) {
            super.act(delta);
            if (batalhaContando && batalhaRestante > 0f) batalhaRestante = Math.max(0f, batalhaRestante - delta);
            // Segundos do battle no canto do icone (nao mais no popup).
            if (contadorBatalha != null) {
                String seg = String.valueOf((int) Math.ceil(batalhaRestante));
                if (!seg.contentEquals(contadorBatalha.getText())) contadorBatalha.setText(seg);
            }
            if (isVisible() && iconeDica == quadradoBatalha) {
                String texto = corpo(textoBatalha());
                if (!texto.contentEquals(textoDica.getText())) textoDica.setText(texto);
            }
        }
    };
    private Label textoDica, tituloDica;
    private Label contadorBatalha;

    /** Label desenhado com a origem presa no pixel de tela: a fonte e' gerada
     * no tamanho real da tela (1 pixel da fonte = 1 pixel de tela), entao com
     * a origem inteira cada letra cai certinho num pixel - nitido. */
    private final class LabelNitido extends Label {
        LabelNitido(CharSequence texto, LabelStyle estilo) { super(texto, estilo); }
        @Override public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
            float x = getX(), y = getY();
            setPosition(pixelInteiro(x), pixelInteiro(y));
            super.draw(batch, parentAlpha);
            setPosition(x, y);
        }
    }

    private static final class Barra {
        final Image preenchimento;
        final Table alinhador;
        final Label texto;
        final float largura;
        Barra(Image preenchimento, Table alinhador, Label texto, float largura) {
            this.preenchimento = preenchimento;
            this.alinhador = alinhador;
            this.texto = texto;
            this.largura = largura;
        }
    }

    /** escala: a mesma usada pra gerar as fontes do skin (pixels reais / unidade do stage). */
    public HudVitais(Stage stage, TextureAtlas atlas, float escala) {
        this.escala = escala;
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
        fonte = gerarFonte(escala, 14, false);
        // Notificacoes (e popups dos debuffs): 15 no celular, 13 no PC.
        fonteNotificacao = gerarFonte(escala, MOBILE ? 15 : 13, true);
        // Sem arredondar pra unidade do STAGE (que nao e' pixel de tela): com
        // isso as letras caiam entre pixels e o filtro Linear "derretia" o
        // texto. O LabelNitido prende a origem no pixel de tela.
        fonteNotificacao.setUseIntegerPositions(false);
        Label.LabelStyle estilo = new Label.LabelStyle(fonte, Color.WHITE);

        hp = criarBarra(estilo, COR_HP, 314);
        mp = criarBarra(estilo, COR_MP, 314);

        // ---- Painel HP/MP ----
        // Fundo do painel cinza escuro SOLIDO; so' o trilho das barras (atras
        // do vermelho/roxo) e' semi transparente - ali da' pra ver o jogo.
        // Por isso o painel pinta tudo MENOS o retangulo de cada barra.
        final java.util.List<com.badlogic.gdx.scenes.scene2d.Actor> barrasPainel = new java.util.ArrayList<>();
        final TextureRegion regiaoPixel = new TextureRegion(pixel);
        Table interno = new Table() {
            @Override protected void drawBackground(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha, float x, float y) {
                Color antes = new Color(batch.getColor());
                batch.setColor(COR_PAINEL.r, COR_PAINEL.g, COR_PAINEL.b, parentAlpha * getColor().a);
                // Barras em coordenadas do painel, de baixo pra cima.
                java.util.List<float[]> furos = new java.util.ArrayList<>();
                for (com.badlogic.gdx.scenes.scene2d.Actor a : barrasPainel) {
                    com.badlogic.gdx.math.Vector2 v = a.localToAscendantCoordinates(this, new com.badlogic.gdx.math.Vector2(0, 0));
                    furos.add(new float[]{v.x, v.y, a.getWidth(), a.getHeight()});
                }
                furos.sort((a, b) -> Float.compare(a[1], b[1]));
                float w = getWidth(), h = getHeight();
                if (furos.isEmpty()) { batch.draw(regiaoPixel, x, y, w, h); batch.setColor(antes); return; }
                float fx0 = furos.get(0)[0], fx1 = fx0 + furos.get(0)[2];
                batch.draw(regiaoPixel, x, y, fx0, h);                 // esquerda (icones)
                batch.draw(regiaoPixel, x + fx1, y, w - fx1, h);       // direita
                float baixo = 0f;
                for (float[] f : furos) {                              // vaos entre as barras
                    if (f[1] > baixo) batch.draw(regiaoPixel, x + fx0, y + baixo, fx1 - fx0, f[1] - baixo);
                    baixo = f[1] + f[3];
                }
                if (h > baixo) batch.draw(regiaoPixel, x + fx0, y + baixo, fx1 - fx0, h - baixo);
                batch.setColor(antes);
            }
        };
        interno.setBackground(cor(COR_PAINEL)); // so' pra Table desenhar o fundo (drawBackground acima)
        interno.pad(px(3), 0, px(2), px(3));
        barrasPainel.add(adicionarLinhaVital(interno, atlas, "ui/HPIcon", hp).getActor());
        interno.row();
        barrasPainel.add(adicionarLinhaVital(interno, atlas, "ui/MPIcon", mp).padTop(px(5)).getActor());
        Table painel = new Table();
        painel.setBackground(moldura(COR_BORDA_PAINEL, px(2), px(1), px(1), px(1)));
        // Borda: 2px em cima, 1px nos outros lados (medido na print).
        painel.pad(px(2), px(1), px(1), px(1));
        painel.add(interno);

        // ---- Painel XP ----
        Image preenchXp = new Image(cor(COR_XP));
        Table alinhaXp = new Table();
        alinhaXp.left();
        alinhaXp.add(preenchXp).size(0f, px(32));
        Label textoXp = new Label("", estilo);
        textoXp.setAlignment(Align.center);
        xp = new Barra(preenchXp, alinhaXp, textoXp, px(260 - 31));
        Table linhaXp = new Table();
        linhaXp.left();
        Image iconeXp = icone(atlas, "ui/XPIcon");
        if (iconeXp != null) linhaXp.add(iconeXp).size(px(26)).padLeft(px(3)).padRight(px(2));
        else linhaXp.add().size(px(26)).padLeft(px(3)).padRight(px(2));
        linhaXp.add(alinhaXp).size(xp.largura, px(32));
        Table centroXp = new Table();
        centroXp.add(textoXp).expand().center();
        Table fundoXp = new Table();
        fundoXp.setBackground(cor(COR_XP_FUNDO));
        Stack pilhaXp = new Stack(fundoXp, linhaXp, centroXp);
        Table painelXp = new Table();
        painelXp.setBackground(cor(Color.BLACK));
        painelXp.pad(px(1));
        painelXp.add(pilhaXp).size(px(260), px(32));

        quadradoFome = quadradoStatus(atlas, "sheet/r18_c10", COR_FOME,
            "You are hungry!\n10% less Dmg and Speed. Regen disabled.");
        quadradoFome.setUserObject(TITULO_FOME);
        // Protection Zone (efeito neutro): sprite no 320,720 do atlas.
        quadradoZonaSegura = quadradoStatus(atlas, nomeRegiao(atlas, "sheet/r78_c4", 320, 720), COR_ZONA_SEGURA,
            "Safe Zone\nMobs can't target you, neither the players. Collision disabled.");
        quadradoZonaSegura.setUserObject(TITULO_ZONA_SEGURA);
        contadorBatalha = new Label("", estilo);
        contadorBatalha.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        quadradoBatalha = quadradoStatus(atlas, "sheet/r107_c2", COR_BATALHA, null, contadorBatalha);
        quadradoBatalha.setUserObject(TITULO_BATALHA);
        linhaTopo = new Table();
        linhaTopo.top().left();
        celulaPainel = linhaTopo.add(painel).top().left();
        this.painelVitais = painel;
        // Icones de status (fome/battle) nao ficam mais aqui: viraram a
        // "gaveta de debuffs" embaixo do minimapa (WorldScreen pega iconesStatus()).
        raiz.setFillParent(true);
        raiz.top().left().padLeft(px(17)).padTop(px(13));
        // Coluna da esquerda: retrato do player + botoes (ver WorldScreen::criarRetrato).
        raiz.add(colunaEsquerda).top().left();
        raiz.add(conteudo).top().left();
        conteudo.top().left();
        conteudo.add(linhaTopo).left().row();
        // Barra de XP saiu: o XP agora e' o anel em volta do retrato (WorldScreen).

        // ---- Municao (so' aparece com flecha equipada) ----
        iconeMunicao.setScaling(Scaling.fit);
        textoMunicao = new Label("", estilo);
        Table internoMunicao = new Table();
        internoMunicao.setBackground(cor(COR_MUNICAO));
        internoMunicao.left();
        internoMunicao.add(iconeMunicao).size(px(24)).padLeft(px(4)).padRight(px(6));
        internoMunicao.add(textoMunicao).left().expandX();
        painelMunicao.setBackground(cor(Color.BLACK));
        painelMunicao.pad(px(1));
        painelMunicao.add(internoMunicao).size(px(110), px(28));
        painelMunicao.setVisible(false);

        // ---- Notificacao (amigo on/off, convite de party/trade, avisos) ----
        TextureAtlas.AtlasRegion regNotif = atlas.findRegion("ui/NotificationIcon");
        iconeNotificacaoPadrao = regNotif;
        iconeNotificacao.setScaling(Scaling.fit);
        if (regNotif != null) iconeNotificacao.setDrawable(new TextureRegionDrawable(regNotif));
        textoNotificacao = new LabelNitido("", new Label.LabelStyle(fonteNotificacao, Color.WHITE));
        // Caixinha estilo "toast": bloco do icone (tom mais escuro) colado
        // na esquerda, texto com folga, borda fina num tom mais claro.
        alturaNotif = px(MOBILE ? 32 : 38);
        internoNotif = new Table();
        internoNotif.setBackground(cor(COR_MUNICAO));
        internoNotif.left();
        blocoIconeNotif = new Table();
        celulaIconeNotif = blocoIconeNotif.add(iconeNotificacao).size(alturaNotif * 0.68f);
        internoNotif.add(blocoIconeNotif).size(alturaNotif);
        iconeNotifTrade = atlas.findRegion("ui/currency/Gold");
        iconeNotifAmigo = atlas.findRegion("ui/buttons/FriendsBtn");
        iconeNotifParty = atlas.findRegion("ui/buttons/PartyBtn");
        internoNotif.add(textoNotificacao).left().expandX().padLeft(px(10)).padRight(px(12));
        painelNotificacao.setBackground(cor(Color.BLACK));
        painelNotificacao.pad(px(1));
        painelNotificacao.add(internoNotif).minWidth(px(150)).height(alturaNotif);
        painelNotificacao.setVisible(false);
        painelNotificacao.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        painelNotificacao.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                Notificacao n = notificacaoAtual;
                proximaNotificacao();
                if (n != null && n.aoClicar != null) n.aoClicar.run();
            }
        });

        colunaInferior.left().top();
        conteudo.add(colunaInferior).left().padTop(px(6));
        reorganizarInferior();
        // Popup das explicacoes dos icones de status (fome/battle).
        // Titulo (1a linha da explicacao, na cor do icone) + descricao.
        tituloDica = new LabelNitido("", new Label.LabelStyle(fonteNotificacao, Color.WHITE));
        textoDica = new LabelNitido("", new Label.LabelStyle(fonteNotificacao, Color.WHITE));
        textoDica.setWrap(true);
        Table internoDica = new Table();
        internoDica.setBackground(cor(COR_MUNICAO));
        internoDica.add(tituloDica).left().padLeft(px(6)).padRight(px(6)).padTop(px(6)).row();
        internoDica.add(textoDica).width(px(MOBILE ? 260 : 230)).pad(px(6)).padTop(px(3));
        dica.setBackground(cor(Color.BLACK));
        // Borda em pixels de tela INTEIROS (px(1) no celular dava 1.7px e a
        // borda sumia de um lado).
        dica.pad(Math.max(1, Math.round(FATOR)) / escala);
        dica.add(internoDica);
        // Sem arredondar pra unidade inteira do STAGE (que nao e' pixel de
        // tela): isso empurrava o fundo de dentro meio pixel pra esquerda e
        // ele cobria a borda da esquerda. A posicao ja' vai em pixel inteiro.
        dica.setRound(false);
        internoDica.setRound(false);
        dica.setVisible(false);
        dica.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        stage.addActor(dica);
        // Sempre por baixo de qualquer outra tela (BookMenu, chat, settings...).
        stage.getRoot().addActorAt(0, raiz);
        atualizar();
        definirXp(1, 0);
    }

    /** Tahoma Bold no tamanho da print, com contorno preto de 2px de tela
     * (1px ficava fino demais). Gerada na resolucao real e reduzida pro stage. */
    private static BitmapFont gerarFonte(float escala, int tamanho, boolean contornoFino) {
        com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator gerador =
            new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator(
                com.badlogic.gdx.Gdx.files.internal("fonts/TAHOMAB0.TTF"));
        com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter p =
            new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter();
        // No mobile o texto cresce menos que as barras (FATOR_TEXTO_MOBILE):
        // com o FATOR inteiro ficava grande demais pra barra. A borda acompanha
        // o tamanho (2px pra 14px, igual o PC) - fixa em 2px, numa fonte
        // gerada bem maior na tela do celular, a letra parecia fina.
        p.size = Math.round(tamanho * FATOR_TEXTO * escala);
        // PC com borda um pouco mais fina (a pedido do usuario); mobile mantem.
        // Notificacao: contorno de 1px de verdade (proporcional ao tamanho
        // dava o mesmo 2px da fonte das barras e nao mudava nada).
        p.borderWidth = contornoFino ? (MOBILE ? Math.max(1, Math.round(p.size / 14f)) : 1)
            : MOBILE ? Math.max(2, Math.round(p.size / 7f)) : Math.max(1, Math.round(p.size / 10f));
        p.borderColor = Color.BLACK;
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        BitmapFont f = gerador.generateFont(p);
        f.getData().setScale(1f / escala);
        gerador.dispose();
        return f;
    }

    /** Pixels de tela -> unidades do stage. */
    private float px(float pixels) {
        return pixels * FATOR / escala;
    }

    /** Fundo dos icones de status: borda (2px de tela), filete preto (1px)
     * e o fundo colorido, tudo alinhado em pixel inteiro de tela. */
    private Drawable quadroStatus(Color borda, Color fundo) {
        TextureRegion r = new TextureRegion(pixel);
        Color cBorda = new Color(borda), cFundo = new Color(fundo);
        return new com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable() {
            @Override public void draw(com.badlogic.gdx.graphics.g2d.Batch b, float x, float y, float w, float h) {
                float x0 = pixelInteiro(x), y0 = pixelInteiro(y);
                float x1 = pixelInteiro(x + w), y1 = pixelInteiro(y + h);
                float px1 = 1f / escala;
                float grossura = Math.max(1, Math.round(FATOR * 2f)) * px1;
                Color antes = new Color(b.getColor());
                b.setColor(cBorda.r, cBorda.g, cBorda.b, antes.a);
                b.draw(r, x0, y0, x1 - x0, y1 - y0);
                b.setColor(0f, 0f, 0f, antes.a);
                b.draw(r, x0 + grossura, y0 + grossura, x1 - x0 - grossura * 2f, y1 - y0 - grossura * 2f);
                float d = grossura + Math.max(1, Math.round(FATOR)) * px1;
                b.setColor(cFundo.r, cFundo.g, cFundo.b, antes.a);
                b.draw(r, x0 + d, y0 + d, x1 - x0 - d * 2f, y1 - y0 - d * 2f);
                b.setColor(antes);
            }
        };
    }

    /** So' a borda (cima, esquerda, baixo, direita), sem preencher o meio. */
    private Drawable moldura(Color c, float cima, float esq, float baixo, float dir) {
        TextureRegion r = new TextureRegion(pixel);
        Color cor = new Color(c);
        return new com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable() {
            @Override public void draw(com.badlogic.gdx.graphics.g2d.Batch b, float x, float y, float w, float h) {
                Color antes = new Color(b.getColor());
                b.setColor(cor.r, cor.g, cor.b, cor.a * antes.a);
                if (cima > 0f) b.draw(r, x, y + h - cima, w, cima);
                if (baixo > 0f) b.draw(r, x, y, w, baixo);
                if (esq > 0f) b.draw(r, x, y + baixo, esq, h - cima - baixo);
                if (dir > 0f) b.draw(r, x + w - dir, y + baixo, dir, h - cima - baixo);
                b.setColor(antes);
            }
        };
    }

    private Drawable cor(Color c) {
        return new TextureRegionDrawable(new TextureRegion(pixel)).tint(c);
    }

    /** nome, se existir no atlas; senao o nome da regiao que esta' em (x, y) da pagina. */
    private static String nomeRegiao(TextureAtlas atlas, String nome, int x, int y) {
        if (atlas.findRegion(nome) != null) return nome;
        for (TextureAtlas.AtlasRegion r : atlas.getRegions())
            if (r.getRegionX() == x && r.getRegionY() == y) return r.name;
        return nome;
    }

    private Image icone(TextureAtlas atlas, String nome) {
        TextureAtlas.AtlasRegion regiao = atlas.findRegion(nome);
        if (regiao == null) return null;
        Image img = new Image(new TextureRegionDrawable(regiao));
        img.setScaling(Scaling.fit);
        return img;
    }

    private Barra criarBarra(Label.LabelStyle estilo, Color cor, float larguraPx) {
        Image preenchimento = new Image(cor(cor));
        Table alinhador = new Table();
        alinhador.left();
        alinhador.add(preenchimento).size(0f, px(34));
        Label texto = new Label("", estilo);
        texto.setAlignment(Align.center);
        return new Barra(preenchimento, alinhador, texto, px(larguraPx));
    }

    /** [icone 39px + 5] [barra 314x34 + 1px de sombra a direita/embaixo]. */
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<?> adicionarLinhaVital(Table pai, TextureAtlas atlas, String nomeIcone, Barra barra) {
        Image img = icone(atlas, nomeIcone);
        // Icone 39x39 (16px * ~2.4) colado na borda esquerda; padding vertical
        // negativo pra ele nao aumentar a altura da linha (que e' a da barra).
        if (img != null) pai.add(img).size(px(39)).padTop(px(-2)).padBottom(px(-2)).padRight(px(5));
        else pai.add().size(px(39)).padTop(px(-2)).padBottom(px(-2)).padRight(px(5));
        Table fundo = new Table();
        // Trilho vazio (atras do vermelho/roxo): cinza escuro semi transparente.
        fundo.setBackground(cor(new Color(0.12f, 0.12f, 0.12f, 0.45f)));
        Stack pilha = new Stack(fundo, barra.alinhador, barra.texto);
        Table comSombra = new Table();
        // Sombra so' na direita/embaixo (antes era um retangulo cheio por tras).
        comSombra.setBackground(moldura(COR_SOMBRA, 0f, 0f, px(1), px(1)));
        comSombra.pad(0, 0, px(1), px(1));
        comSombra.add(pilha).size(barra.largura, px(34));
        return pai.add(comSombra);
    }

    /** Quadrado 42x42 (52x52 no celular) com borda preta e o icone no meio. Mouse em cima (PC)
     * ou dedo segurando (celular) mostra a explicacao; saiu/soltou, some. */
    private Table quadradoStatus(TextureAtlas atlas, String regiao, Color fundo, String explicacao) {
        return quadradoStatus(atlas, regiao, fundo, explicacao, null);
    }

    /** contador: texto no canto de baixo a direita (segundos do battle), ou null. */
    private Table quadradoStatus(TextureAtlas atlas, String regiao, Color fundo, String explicacao, Label contador) {
        // Mesmo visual do painel de HP/MP: borda cinza de 2px, filete preto
        // e o fundo colorido; icone grande no meio.
        float lado = px(MOBILE ? 58 : 52);
        // Borda, filete preto e fundo num desenho so', com cada camada presa
        // no pixel de tela: em tabelas separadas a borda caia entre pixels e
        // sumia na esquerda/embaixo.
        Table interno = new Table();
        Image img = icone(atlas, regiao);
        if (img != null) {
            img.setScaling(Scaling.fit);
            interno.add(img).size(lado * 0.78f);
        }
        Table quadrado = new Table();
        quadrado.setBackground(quadroStatus(new Color(fundo).mul(0.55f, 0.55f, 0.55f, 1f), fundo));
        quadrado.pad(px(3));
        if (contador != null) {
            Table canto = new Table();
            canto.bottom().right();
            canto.add(contador).padRight(px(3)).padBottom(px(1));
            quadrado.add(new Stack(interno, canto)).size(lado);
        } else {
            quadrado.add(interno).size(lado);
        }
        quadrado.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        quadrado.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override public void enter(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int pointer,
                                        com.badlogic.gdx.scenes.scene2d.Actor de) {
                if (pointer == -1) mostrarDica(quadrado, explicacao); // -1 = mouse, sem clique
            }
            @Override public void exit(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int pointer,
                                       com.badlogic.gdx.scenes.scene2d.Actor para) {
                if (pointer == -1 && (para == null || !para.isDescendantOf(quadrado))) esconderDica();
            }
            @Override public boolean touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int pointer, int botao) {
                mostrarDica(quadrado, explicacao);
                return true;
            }
            @Override public void touchUp(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int pointer, int botao) {
                esconderDica();
            }
        });
        return quadrado;
    }

    /** Popup da explicacao, logo abaixo do icone (por cima de tudo). */
    private void mostrarDica(Table icone, String texto) {
        iconeDica = icone;
        if (texto == null) texto = textoBatalha();
        int quebra = texto.indexOf('\n');
        tituloDica.setText(quebra >= 0 ? texto.substring(0, quebra) : "");
        tituloDica.setColor(icone.getUserObject() instanceof Color ? (Color) icone.getUserObject() : Color.WHITE);
        tituloDica.setVisible(quebra >= 0);
        textoDica.setText(corpo(texto));
        dica.pack();
        com.badlogic.gdx.math.Vector2 p = icone.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(0, 0));
        Stage stage = icone.getStage();
        float x = p.x;
        if (stage != null) x = Math.max(px(4), Math.min(x, stage.getWidth() - dica.getWidth() - px(4)));
        // Posicao e tamanho encaixados na grade de pixels da tela: em fracao de
        // pixel a borda da esquerda e a de baixo saiam cortadas.
        dica.setSize(pixelInteiro(dica.getWidth()), pixelInteiro(dica.getHeight()));
        dica.setPosition(pixelInteiro(x), pixelInteiro(p.y - dica.getHeight() - px(4)));
        dica.setVisible(true);
        dica.toFront();
    }

    private void esconderDica() { dica.setVisible(false); iconeDica = null; }

    /** Explicacao sem o titulo (1a linha). */
    private static String corpo(String texto) {
        int quebra = texto.indexOf('\n');
        return quebra >= 0 ? texto.substring(quebra + 1) : texto;
    }

    private String textoBatalha() {
        return "In Battle\n"
            + "If you log out now, your body stays in the game until the battle ends.";
    }

    /** Arredonda (unidades do stage) pro pixel de tela mais proximo. */
    private float pixelInteiro(float v) { return Math.round(v * escala) / escala; }

    private void reorganizarStatus() {
        esconderDica();
        iconesStatus.clearChildren();
        if (statusNoTopo) iconesStatus.center().left();
        else iconesStatus.top().right();
        // Em trios: 3 por linha, as linhas vao se empilhando pra baixo (6 = 3x2).
        java.util.List<Table> ativos = new java.util.ArrayList<>();
        if (naZonaSegura) ativos.add(quadradoZonaSegura);
        if (comFome) ativos.add(quadradoFome);
        if (emBatalha) ativos.add(quadradoBatalha);
        for (int i = 0; i < ativos.size(); i++) {
            iconesStatus.add(ativos.get(i)).pad(px(3));
            if (i % 3 == 2) iconesStatus.row();
        }
    }

    private boolean statusNoTopo = false;

    /** Celular: os debuffs voltam pro lado do retrato/barras de HP-MP. */
    public void colocarStatusNoTopo() {
        statusNoTopo = true;
        linhaTopo.add(iconesStatus).center().left().padLeft(px(10));
        iconesStatus.center().left();
        centralizarNoRetrato();
    }

    /** Gaveta de debuffs (fome/battle...) - WorldScreen poe embaixo do minimapa. */
    public Table iconesStatus() { return iconesStatus; }

    /** Some com o painel reto de HP/MP enquanto uma janela (chat/livro/...) esta aberta. */
    public void definirTopoVisivel(boolean visivel) {
        // Painel de HP/MP, municao e notificacoes somem juntos.
        conteudo.setVisible(visivel);
    }

    /** Icone da Protection Zone: aparece enquanto o player pisa nela. */
    public void definirZonaSegura(boolean naZonaSegura) {
        if (this.naZonaSegura == naZonaSegura) return;
        this.naZonaSegura = naZonaSegura;
        reorganizarStatus();
    }

    /** Icone de fome: aparece com a barra de Fullness zerada. */
    public void definirFome(boolean comFome) {
        if (this.comFome == comFome) return;
        this.comFome = comFome;
        reorganizarStatus();
    }

    /** Icone de battle (um mob mirou o player ou ele bateu num mob);
     * segundos = quanto falta pro battle acabar (contagem regressiva). */
    public void definirBatalha(boolean emBatalha, boolean contando, float segundos) {
        batalhaRestante = emBatalha ? segundos : 0f;
        batalhaContando = emBatalha && contando;
        definirBatalha(emBatalha);
    }

    public void definirBatalha(boolean emBatalha) {
        if (this.emBatalha == emBatalha) return;
        this.emBatalha = emBatalha;
        reorganizarStatus();
    }

    public boolean emBatalha() { return emBatalha; }

    /** Valores negativos = "nao mudou". */
    public void definir(float hpAtual, float hpMax, float mpAtual, float mpMax) {
        if (hpMax > 0f) this.hpMax = hpMax;
        if (mpMax > 0f) this.mpMax = mpMax;
        if (hpAtual >= 0f) this.hpAtual = hpAtual;
        if (mpAtual >= 0f) this.mpAtual = mpAtual;
        atualizar();
    }

    /** Barra de XP: % do level atual ate o proximo (mesma formula do servidor,
     * servidor.py::get_exp_for_level). */
    public void definirXp(int level, long exp) {
        long atual = expParaLevel(level), proximo = expParaLevel(level + 1);
        float pct = proximo > atual ? (float) (exp - atual) / (proximo - atual) : 0f;
        pct = Math.max(0f, Math.min(1f, pct));
        preencher(xp, pct);
        xp.texto.setText(String.format(java.util.Locale.US, "%.2f%%", pct * 100f));
        fracaoXp = pct;
    }

    private float fracaoXp = 0f;
    /** Tahoma Bold branca com contorno preto (a mesma das barras). */
    public BitmapFont fonteComContorno() { return fonte; }
    /** 0-1 do caminho ate' o proximo level (anel do retrato). */
    public float fracaoXp() { return fracaoXp; }

    private static long expParaLevel(int level) {
        if (level <= 1) return 0;
        return (long) ((50.0 / 3.0) * (Math.pow(level, 3) - 6 * Math.pow(level, 2) + 17 * level - 12));
    }

    /** Flecha equipada: icone + quantidade. icone null = sem municao (esconde). */
    public void definirMunicao(TextureRegion icone, int quantidade) {
        boolean mostrar = icone != null;
        if (painelMunicao.isVisible() != mostrar) {
            painelMunicao.setVisible(mostrar);
            reorganizarInferior();
        }
        if (!mostrar) return;
        iconeMunicao.setDrawable(new TextureRegionDrawable(icone));
        textoMunicao.setText(String.valueOf(Math.max(0, quantidade)));
        textoMunicao.setColor(quantidade <= 0 ? new Color(1f, 0.35f, 0.35f, 1f) : Color.WHITE);
    }

    /** Municao e notificacao empilhadas; a que estiver escondida nao ocupa
     * espaco (a notificacao sobe pro lugar da municao). */
    private void reorganizarInferior() {
        colunaInferior.clearChildren();
        boolean municao = painelMunicao.isVisible();
        if (municao) colunaInferior.add(painelMunicao).left().row();
        if (painelNotificacao.isVisible()) {
            colunaInferior.add(painelNotificacao).left().padTop(municao ? px(6) : 0f).row();
        }
    }

    /** Notificacao curta (icone de chat + texto) por alguns segundos.
     * aoClicar (pode ser null) roda ao clicar nela, ex: abrir a aba Party. */
    public void notificar(String texto, Color cor, Runnable aoClicar) {
        if (texto == null || texto.isEmpty()) return;
        Notificacao n = new Notificacao(texto, cor != null ? cor : Color.WHITE, aoClicar);
        if (notificacaoAtual == null) {
            mostrarNotificacao(n);
            return;
        }
        // Fila curta: se encher, descarta a mais antiga que esta esperando.
        if (filaNotificacoes.size() >= MAX_FILA_NOTIFICACOES) filaNotificacoes.pollFirst();
        filaNotificacoes.addLast(n);
    }

    public void notificar(String texto) { notificar(texto, null, null); }

    public void notificarTipo(int tipo, String texto) { notificarTipo(tipo, texto, null, null); }

    /** Notificacao com icone e cor de fundo proprios (ex: bestiary). */
    public void notificarIcone(String texto, TextureRegion icone, Color fundo, Runnable aoClicar) {
        if (texto == null || texto.isEmpty()) return;
        Notificacao n = new Notificacao(texto, Color.WHITE, aoClicar);
        n.iconeProprio = icone;
        n.fundoProprio = fundo;
        if (notificacaoAtual == null) {
            mostrarNotificacao(n);
            return;
        }
        if (filaNotificacoes.size() >= MAX_FILA_NOTIFICACOES) filaNotificacoes.pollFirst();
        filaNotificacoes.addLast(n);
    }

    /** Igual notificar, com icone/cor de fundo do tipo (trade, amigo, party). */
    public void notificarTipo(int tipo, String texto, Color cor, Runnable aoClicar) {
        if (texto == null || texto.isEmpty()) return;
        Notificacao n = new Notificacao(texto, cor != null ? cor : Color.WHITE, aoClicar);
        n.tipo = tipo;
        if (notificacaoAtual == null) {
            mostrarNotificacao(n);
            return;
        }
        if (filaNotificacoes.size() >= MAX_FILA_NOTIFICACOES) filaNotificacoes.pollFirst();
        filaNotificacoes.addLast(n);
    }

    private void mostrarNotificacao(Notificacao n) {
        notificacaoAtual = n;
        textoNotificacao.setText(n.texto);
        textoNotificacao.setColor(Color.WHITE); // todas brancas
        TextureRegion icone = iconeNotificacaoPadrao;
        Color fundo = COR_MUNICAO;
        float tamIcone = alturaNotif * 0.68f;
        if (n.tipo == NOTIF_TRADE && iconeNotifTrade != null) { icone = iconeNotifTrade; fundo = COR_NOTIF_TRADE; tamIcone *= 0.8f; }
        else if (n.tipo == NOTIF_AMIGO && iconeNotifAmigo != null) { icone = iconeNotifAmigo; fundo = COR_NOTIF_AMIGO; }
        else if (n.tipo == NOTIF_PARTY && iconeNotifParty != null) { icone = iconeNotifParty; fundo = COR_NOTIF_PARTY; }
        if (n.iconeProprio != null) icone = n.iconeProprio;
        if (n.fundoProprio != null) fundo = n.fundoProprio;
        if (icone != null) iconeNotificacao.setDrawable(new TextureRegionDrawable(icone));
        internoNotif.setBackground(cor(new Color(fundo.r, fundo.g, fundo.b, 0.93f)));
        blocoIconeNotif.setBackground(cor(new Color(fundo).mul(0.6f, 0.6f, 0.6f, 1f)));
        painelNotificacao.setBackground(cor(new Color(fundo).lerp(Color.WHITE, 0.3f)));
        celulaIconeNotif.size(tamIcone);
        internoNotif.invalidate();
        painelNotificacao.clearActions();
        painelNotificacao.getColor().a = 1f;
        painelNotificacao.setVisible(true);
        reorganizarInferior();
        // Com mais coisa na fila cada uma fica um pouco menos.
        float duracao = filaNotificacoes.isEmpty() ? DURACAO_NOTIFICACAO : DURACAO_NOTIFICACAO * 0.6f;
        painelNotificacao.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
            com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(duracao),
            com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut(0.3f),
            com.badlogic.gdx.scenes.scene2d.actions.Actions.run(this::proximaNotificacao)));
    }

    private void proximaNotificacao() {
        painelNotificacao.clearActions();
        Notificacao proxima = filaNotificacoes.pollFirst();
        if (proxima != null) {
            mostrarNotificacao(proxima);
            return;
        }
        notificacaoAtual = null;
        painelNotificacao.setVisible(false);
        reorganizarInferior();
    }

    public float hpAtual() { return hpAtual; }
    public float hpMax() { return hpMax; }
    public float mpMax() { return mpMax; }
    public float mpAtual() { return mpAtual; }

    private void atualizar() {
        preencher(hp, hpMax > 0f ? hpAtual / hpMax : 0f);
        hp.texto.setText(Math.round(hpAtual) + "/" + Math.round(hpMax));
        preencher(mp, mpMax > 0f ? mpAtual / mpMax : 0f);
        mp.texto.setText(Math.round(mpAtual) + "/" + Math.round(mpMax));
    }

    private static void preencher(Barra barra, float pct) {
        pct = Math.max(0f, Math.min(1f, pct));
        barra.alinhador.getCell(barra.preenchimento).width(barra.largura * pct);
        barra.alinhador.invalidateHierarchy();
    }

    /** Coloca algo a esquerda do painel de HP/MP (retrato + botoes), alinhado em cima. */
    public void definirColunaEsquerda(com.badlogic.gdx.scenes.scene2d.Actor ator, float espaco, float alturaTopo) {
        colunaEsquerda.setActor(ator);
        colunaEsquerda.top().left();
        raiz.getCell(colunaEsquerda).padRight(espaco);
        // Painel de HP/MP centralizado na altura do retrato (alturaTopo).
        this.alturaTopo = alturaTopo;
        centralizarNoRetrato();
        raiz.invalidate();
    }

    public void setVisivel(boolean visivel) {
        raiz.setVisible(visivel);
        if (!visivel) esconderDica();
    }

    public void dispose() {
        pixel.dispose();
        fonte.dispose();
        fonteNotificacao.dispose();
    }
}
