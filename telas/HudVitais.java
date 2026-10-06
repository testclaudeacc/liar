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
    private static final Color COR_MP = new Color(96 / 255f, 153 / 255f, 1f, 1f);
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
    private static final float DURACAO_NOTIFICACAO = 5f;
    private static final int MAX_FILA_NOTIFICACOES = 6;

    private static final class Notificacao {
        final String texto;
        final Color cor;
        final Runnable aoClicar;
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
    private final Table iconesStatus = new Table();
    private Table quadradoFome, quadradoBatalha;
    private boolean comFome = false, emBatalha = false;
    // Tempo ate' sair do battle: travado em 30 enquanto luta; o servidor avisa
    // quando o player parou (counting) e daí desce sozinho aqui.
    private float batalhaRestante = 0f;
    private boolean batalhaContando = false; // false = lutando, travado no maximo
    private Table iconeDica; // icone cujo popup esta aberto
    private final Table dica = new Table() {
        @Override public void act(float delta) {
            super.act(delta);
            if (batalhaContando && batalhaRestante > 0f) batalhaRestante = Math.max(0f, batalhaRestante - delta);
            if (isVisible() && iconeDica == quadradoBatalha) {
                String texto = textoBatalha();
                if (!texto.contentEquals(textoDica.getText())) textoDica.setText(texto);
            }
        }
    };
    private Label textoDica;

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
        fonteNotificacao = gerarFonte(escala, MOBILE ? 16 : 11, true);
        Label.LabelStyle estilo = new Label.LabelStyle(fonte, Color.WHITE);

        hp = criarBarra(estilo, COR_HP, 314);
        mp = criarBarra(estilo, COR_MP, 314);

        // ---- Painel HP/MP ----
        Table interno = new Table();
        interno.setBackground(cor(COR_PAINEL));
        interno.pad(px(3), 0, px(2), px(3));
        adicionarLinhaVital(interno, atlas, "ui/HPIcon", hp);
        interno.row();
        adicionarLinhaVital(interno, atlas, "ui/MPIcon", mp).padTop(px(5));
        Table painel = new Table();
        painel.setBackground(cor(COR_BORDA_PAINEL));
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
            "You are hungry! 10% less Dmg and Speed. Regen disabled");
        quadradoBatalha = quadradoStatus(atlas, "sheet/r107_c2", COR_BATALHA, null);
        Table linhaTopo = new Table();
        linhaTopo.top().left();
        linhaTopo.add(painel).top().left();
        // Alinhado com a barra de HP (borda 2px + pad 3px do painel).
        linhaTopo.add(iconesStatus).top().left().padLeft(px(9)).padTop(px(10));
        raiz.setFillParent(true);
        raiz.top().left().padLeft(px(17)).padTop(px(13));
        raiz.add(linhaTopo).left().row();
        raiz.add(painelXp).left().padTop(px(6)).row();

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
        textoNotificacao = new Label("", new Label.LabelStyle(fonteNotificacao, Color.WHITE));
        Table internoNotif = new Table();
        internoNotif.setBackground(cor(COR_MUNICAO));
        internoNotif.left();
        internoNotif.add(iconeNotificacao).size(px(MOBILE ? 22 : 20)).padLeft(px(4)).padRight(px(6));
        internoNotif.add(textoNotificacao).left().expandX().padRight(px(8));
        painelNotificacao.setBackground(cor(Color.BLACK));
        painelNotificacao.pad(px(1));
        painelNotificacao.add(internoNotif).minWidth(px(110)).height(px(MOBILE ? 32 : 28));
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
        raiz.add(colunaInferior).left().padTop(px(6));
        reorganizarInferior();
        // Popup das explicacoes dos icones de status (fome/battle).
        textoDica = new Label("", new Label.LabelStyle(fonteNotificacao, Color.WHITE));
        textoDica.setWrap(true);
        Table internoDica = new Table();
        internoDica.setBackground(cor(COR_MUNICAO));
        internoDica.add(textoDica).width(px(MOBILE ? 260 : 230)).pad(px(6));
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

    private Drawable cor(Color c) {
        return new TextureRegionDrawable(new TextureRegion(pixel)).tint(c);
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
        fundo.setBackground(cor(COR_PAINEL));
        Stack pilha = new Stack(fundo, barra.alinhador, barra.texto);
        Table comSombra = new Table();
        comSombra.setBackground(cor(COR_SOMBRA));
        comSombra.pad(0, 0, px(1), px(1));
        comSombra.add(pilha).size(barra.largura, px(34));
        return pai.add(comSombra);
    }

    /** Quadrado 42x42 (52x52 no celular) com borda preta e o icone no meio. Mouse em cima (PC)
     * ou dedo segurando (celular) mostra a explicacao; saiu/soltou, some. */
    private Table quadradoStatus(TextureAtlas atlas, String regiao, Color fundo, String explicacao) {
        Table interno = new Table();
        interno.setBackground(cor(fundo));
        Image img = icone(atlas, regiao);
        // No celular um pouco maior (alem do FATOR que ja' vale pra HUD toda).
        if (img != null) interno.add(img).size(px(MOBILE ? 40 : 32));
        Table quadrado = new Table();
        quadrado.setBackground(cor(Color.BLACK));
        quadrado.pad(px(1));
        quadrado.add(interno).size(px(MOBILE ? 50 : 40));
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
        textoDica.setText(texto != null ? texto : textoBatalha());
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

    private String textoBatalha() {
        return "In battle (" + (int) Math.ceil(batalhaRestante) + "s)\n"
            + "If you log out now, your body stays in the game until the battle ends.";
    }

    /** Arredonda (unidades do stage) pro pixel de tela mais proximo. */
    private float pixelInteiro(float v) { return Math.round(v * escala) / escala; }

    private void reorganizarStatus() {
        esconderDica();
        iconesStatus.clearChildren();
        if (comFome) iconesStatus.add(quadradoFome).padRight(px(4));
        if (emBatalha) iconesStatus.add(quadradoBatalha).padRight(px(4));
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
    }

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

    private void mostrarNotificacao(Notificacao n) {
        notificacaoAtual = n;
        textoNotificacao.setText(n.texto);
        textoNotificacao.setColor(Color.WHITE); // todas brancas
        if (iconeNotificacaoPadrao != null) iconeNotificacao.setDrawable(new TextureRegionDrawable(iconeNotificacaoPadrao));
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
