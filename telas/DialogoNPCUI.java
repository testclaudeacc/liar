package com.teste.game.telas;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

public final class DialogoNPCUI {

    // Era 15ms -> 26ms -> 42ms -> 110ms -> 60ms -> 20ms (a pedido do
    // usuario). PAUSA_FORTE_MS entra depois de ./!/? , PAUSA_FRACA_MS depois
    // de ,/;/: (metade da forte).
    private static final long INTERVALO_LETRA_MS = 20L;
    private static final long PAUSA_FORTE_MS = 220L;
    private static final long PAUSA_FRACA_MS = 110L;

    // Caractere "^" reaproveitado como icone de moeda inline (ver
    // DialogoNPCUI(...) abaixo) - nunca aparece de verdade nas falas, entao
    // e' seguro usar como marcador. "[#RRGGBBAA]"/"[]" = markup de cor do
    // proprio BitmapFont (igual "chat-log" ja usa pro log do chat) pra
    // destacar "2 Silver Coins" num cinza mais escuro que o resto da fala.
    public static final char ICONE_MOEDA = '^';
    private static final String COR_MOEDA_MARKUP = "[#A8A8B2FF]";
    private static final String FIM_COR_MARKUP = "[]";

    /** Monta "^[cor]rotulo[]" pra um trecho de fala - usado pelo chamador
     * (WorldScreen) ao montar o texto de uma pagina. Ex:
     * "Give me " + DialogoNPCUI.moeda("2 Silver Coins") + " to sail...". */
    public static String moeda(String rotulo) {
        return ICONE_MOEDA + COR_MOEDA_MARKUP + rotulo + FIM_COR_MARKUP;
    }

    private final Table root = new Table();
    private final Table rodape = new Table();
    private final Image retrato;
    private final Label nomeLabel;
    private final Label falaLabel;
    private final TextButton fecharButton;
    private final TextButton botaoAcao;
    private final com.badlogic.gdx.scenes.scene2d.ui.Image iconeAcao;
    private Runnable acao;
    private float escalaRodape = 1f;
    private final TextButton.TextButtonStyle estiloProximo;
    private final TextButton.TextButtonStyle estiloFechar;
    private Runnable aoFechar = () -> {};
    private String[] paginas = {""};
    private int paginaAtual;
    private String falaCompleta = "";
    private int caracteresVisiveis;
    // Acumulador em delta-time (nao wall-clock) - revela no MAXIMO 1 letra
    // por atualizar() e descarta qualquer sobra depois de revelar. Antes
    // (TimeUtils.millis() + while de "alcancar o atraso") uma trava de frame
    // bem na abertura do dialogo (textura do retrato, resize do Table pelo
    // novo texto) fazia varias letras saltarem de uma vez assim que o jogo
    // "acordava" - sentido pelo usuario como "o comecinho sai acelerado" e a
    // digitacao "meio travada" no resto.
    private float relogioLetraMs;
    private long atrasoExtraPendenteMs;

    public DialogoNPCUI(Stage stage, Skin skin, TextureRegion iconeMoeda, float escalaFonte) {
        // Mesmo criterio de "mobile" usado em WorldScreen - no celular a
        // caixa inteira (retrato, texto, botao) escala ~35% maior, igual os
        // outros elementos de toque (TAMANHO_BOTAO_TOPO, LARGURA_BOTAO_
        // SETTINGS) ja fazem, a pedido do usuario ("aumentar no mobile").
        boolean mobile = Gdx.app.getType() == Application.ApplicationType.Android
            || Gdx.app.getType() == Application.ApplicationType.iOS;
        float escala = mobile ? 1.35f : 1f;

        skin.add("dialogo-npc-fundo", UiSkin.retangulo(
            new Color(0.008f, 0.008f, 0.008f, 0.98f), new Color(0.15f, 0.15f, 0.15f, 1f), 1),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("dialogo-npc-retrato", UiSkin.retangulo(
            new Color(0.025f, 0.025f, 0.03f, 1f), new Color(0.15f, 0.15f, 0.15f, 1f), 1),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);

        retrato = new Image();
        retrato.setScaling(Scaling.fit);
        nomeLabel = new Label("", skin, "subtitulo");
        nomeLabel.setColor(new Color(1f, 0.84f, 0.42f, 1f));

        // Fonte PROPRIA (nao a "default" compartilhada do skin, usada em
        // telas/labels no resto do jogo inteiro) - precisa disso porque:
        // (1) markupEnabled fica ligado so' aqui, sem risco de um "["
        // literal em QUALQUER outro texto "default" (nome de jogador, item
        // etc) virar tag sem querer; (2) o glyph de "^" e' REMAPEADO pro
        // icone de moeda so' nesta instancia, sem bagunçar o "^" de verdade
        // em outro lugar do jogo que use a fonte "default".
        BitmapFont fonteFala = UiSkin.gerarFonte("fonts/dejavu-sans.condensed.ttf", 20, 2, escalaFonte);
        fonteFala.getData().markupEnabled = true;
        BitmapFont.Glyph glifoMoeda = fonteFala.getData().getGlyph(ICONE_MOEDA);
        if (glifoMoeda != null) {
            // setGlyphRegion() so' recalcula u/v (coordenadas de textura) -
            // ele NAO troca glyph.page, que continuava apontando pra pagina
            // da propria fonte (a textura onde o "^" de verdade foi
            // rasterizado). Resultado: u/v calculados em cima do atlas de
            // graficos, mas lidos da textura ERRADA na hora de desenhar -
            // nada (ou lixo) aparecia. Precisa adicionar o atlas de graficos
            // como uma pagina NOVA da fonte e apontar glyph.page pra ela.
            com.badlogic.gdx.utils.Array<TextureRegion> paginas = fonteFala.getRegions();
            int indicePagina = paginas.size;
            TextureRegion paginaIcone = new TextureRegion(iconeMoeda);
            paginas.add(paginaIcone);
            glifoMoeda.page = indicePagina;
            // srcX/srcY/width/height ANTES da chamada = a janela de recorte
            // DENTRO da region nova (0,0 ate' a region inteira) - sem isso
            // ele reusa os valores antigos do "^" (posicao dentro da TEXTURA
            // DA FONTE), que nao tem nada a ver com a posicao do icone
            // dentro do atlas de graficos, e o recorte saia vazio.
            glifoMoeda.srcX = 0;
            glifoMoeda.srcY = 0;
            glifoMoeda.width = paginaIcone.getRegionWidth();
            glifoMoeda.height = paginaIcone.getRegionHeight();
            fonteFala.getData().setGlyphRegion(glifoMoeda, paginaIcone);
            // DEPOIS da chamada (ela nao mexe em width/height/xoffset/
            // yoffset quando a region nao tem espaco em branco cortado, como
            // aqui) - tamanho de EXIBICAO (unidades de fonte/stage),
            // independente do tamanho cru do icone no atlas (16x16), pra nao
            // sair minusculo/gigante dependendo do fator HiDPI do device.
            //
            // yoffset/height copiados do glifo de "A" (ja' renderiza certo,
            // ver "KHARON" no titulo) em vez de um valor chutado - um
            // yoffset fixo pequeno deixava o icone MUITO acima da linha
            // (quase encostando no nome do NPC, print mandado pelo usuario)
            // porque a altura que eu dei (quase a altura da linha toda)
            // some ate' acima da regiao onde as letras realmente ficam (o
            // "cap-height") quando alinhada pelo TOPO da caixa da linha, nao
            // pelo topo do "A". Copiando de "A" o icone fica exatamente na
            // mesma faixa vertical que as letras maiusculas ao redor.
            BitmapFont.Glyph referencia = fonteFala.getData().getGlyph('A');
            int ladoGlifo = referencia != null ? referencia.height : Math.round(18f * escalaFonte);
            glifoMoeda.width = ladoGlifo;
            glifoMoeda.height = ladoGlifo;
            glifoMoeda.xoffset = 0;
            glifoMoeda.yoffset = referencia != null ? referencia.yoffset : 0;
            glifoMoeda.xadvance = ladoGlifo + Math.round(3f * escalaFonte);
            glifoMoeda.kerning = null;
        }
        Label.LabelStyle estiloFala = new Label.LabelStyle(fonteFala, Color.WHITE);
        falaLabel = new Label("", estiloFala);
        falaLabel.setWrap(true);
        falaLabel.setAlignment(Align.topLeft);

        // Fonte trocada de Martel (padrao dos estilos de popup) pra
        // dejavu-sans.condensed, a mesma usada no log do chat (skin
        // "chat-log") - a pedido do usuario. "Close" (vermelho) aparece so'
        // na ULTIMA pagina da fala; enquanto houver mais pagina, o mesmo
        // botao vira "Next" (cinza, estilo "cinza-popup" ja usado em
        // ChatUI/outros popups) - a pedido do usuario.
        BitmapFont fonteBotao = skin.get("chat-log", Label.LabelStyle.class).font;
        estiloFechar = new TextButton.TextButtonStyle(skin.get("vermelho-popup", TextButton.TextButtonStyle.class));
        estiloFechar.font = fonteBotao;
        // "vermelho-popup" vem com fontColor vermelho-claro (pra combinar
        // com o fundo vermelho escuro) - trocado pra branco, a pedido do
        // usuario.
        estiloFechar.fontColor = Color.WHITE;
        estiloProximo = new TextButton.TextButtonStyle(skin.get("cinza-popup", TextButton.TextButtonStyle.class));
        estiloProximo.font = fonteBotao;

        fecharButton = new TextButton("Close", estiloFechar);
        fecharButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                avancarOuFechar();
            }
        });

        // Botao de acao (ex: "Pay" com o SellerIcon), so' na ultima pagina,
        // na direita - ver definirAcao().
        TextButton.TextButtonStyle estiloAcao = new TextButton.TextButtonStyle(skin.get("verde-popup", TextButton.TextButtonStyle.class));
        estiloAcao.font = fonteBotao;
        estiloAcao.fontColor = Color.WHITE;
        botaoAcao = new TextButton("", estiloAcao);
        iconeAcao = new com.badlogic.gdx.scenes.scene2d.ui.Image();
        iconeAcao.setScaling(com.badlogic.gdx.utils.Scaling.fit);
        botaoAcao.clearChildren();
        botaoAcao.add(iconeAcao).size(22f * escala).padRight(5f * escala);
        botaoAcao.add(botaoAcao.getLabel());
        botaoAcao.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (acao != null) acao.run();
            }
        });
        this.escalaRodape = escala;

        Table retratoMoldura = new Table();
        retratoMoldura.setBackground(skin.getDrawable("dialogo-npc-retrato"));
        retratoMoldura.add(retrato).size(58f * escala, 70f * escala).pad(4f * escala);

        Table texto = new Table();
        texto.top().left();
        texto.add(nomeLabel).growX().left().padBottom(5f * escala).row();
        texto.add(falaLabel).grow().top().left();

        Table corpo = new Table();
        corpo.top();
        corpo.add(retratoMoldura).size(70f * escala, 80f * escala).padRight(10f * escala).top();
        corpo.add(texto).growX().top();

        // Alinhamento trocado dinamicamente em atualizarBotaoRodape(): Next
        // sempre na direita, Close sempre na esquerda (a pedido do usuario).
        rodape.right();
        rodape.add(fecharButton).width(86f * escala).height(42f * escala);

        Table painel = new Table();
        painel.setBackground(skin.getDrawable("dialogo-npc-fundo"));
        painel.pad(8f * escala);
        // minHeight (nao height fixo) - deixa a caixa crescer conforme a
        // fala quebra em mais linhas, em vez de cortar o texto.
        painel.add(corpo).growX().minHeight(82f * escala).row();
        painel.add(rodape).growX().padTop(4f * escala);

        // Table.center() SOBRESCREVE o align inteiro (nao faz OR com o
        // bottom() anterior, ver fonte do Table) - por isso "bottom().
        // center()" virava centralizado tanto na vertical quanto na
        // horizontal (bug reportado pelo usuario). So' bottom() ja basta:
        // o align horizontal default da Table ja e' centralizado.
        //
        // padBottom aqui (nao 0) - a pedido do usuario, que achou colado
        // DEMAIS na borda inferior da tela (a 1a versao, sem margem nenhuma).
        root.setFillParent(true);
        root.bottom().padBottom(16f * escala);
        // Sem height fixo tambem, pelo mesmo motivo do painel - a altura
        // segue o conteudo (preferred height), crescendo pra CIMA porque o
        // root inteiro fica ancorado embaixo (fillParent + bottom()).
        root.add(painel).width(400f * escala);
        stage.addActor(root);
        // Fica atras de QUALQUER outro menu (chat/settings/bookmenu/
        // joystick, ja existentes neste stage nesse ponto da construcao) -
        // a pedido do usuario. A barra do topo, criada DEPOIS deste
        // construtor, continua por cima normalmente (ordem de insercao).
        root.toBack();
        root.setVisible(false);
    }

    /** @param paginas Fala dividida em paginas - cada uma aparece por vez,
     *  com "Next" avancando pra proxima e "Close" (so' na ultima) fechando o
     *  dialogo. Pra embutir um valor em moeda na MESMA linha da fala, use
     *  DialogoNPCUI.moeda("2 Silver Coins") dentro da string. Array vazio/
     *  nulo vira 1 pagina em branco. */
    public void abrir(String nome, TextureRegion spriteFrente, String[] paginas, Runnable aoFechar) {
        nomeLabel.setText(nome);
        retrato.setDrawable(new TextureRegionDrawable(spriteFrente));
        this.paginas = (paginas == null || paginas.length == 0) ? new String[] {""} : paginas;
        this.paginaAtual = 0;
        this.aoFechar = aoFechar == null ? () -> {} : aoFechar;
        this.acao = null; // quem quiser o botao chama definirAcao depois de abrir
        iniciarPagina();
        root.setVisible(true);
    }

    private void iniciarPagina() {
        falaCompleta = paginas[paginaAtual];
        // pularTags() (nao só "0") pro caso raro de uma pagina comecar com
        // "^"/tag de cor logo de cara - mantem o invariante de atualizar()
        // de que caracteresVisiveis nunca aponta pra um "[".
        caracteresVisiveis = pularTags(0);
        falaLabel.setText("");
        relogioLetraMs = 0f;
        atrasoExtraPendenteMs = 0L;
        atualizarBotaoRodape();
    }

    /** Next (cinza, direita) enquanto houver mais pagina depois desta;
     * Close (vermelho, esquerda) so' na ultima - a pedido do usuario. */
    private void atualizarBotaoRodape() {
        boolean ultimaPagina = paginaAtual >= paginas.length - 1;
        fecharButton.setText(ultimaPagina ? "Close" : "Next");
        fecharButton.setStyle(ultimaPagina ? estiloFechar : estiloProximo);
        rodape.clearChildren();
        if (ultimaPagina) {
            // Close na esquerda; acao (Pay...) na direita, se tiver.
            rodape.add(fecharButton).width(86f * escalaRodape).height(42f * escalaRodape).left();
            rodape.add().expandX();
            if (acao != null) rodape.add(botaoAcao).height(42f * escalaRodape).minWidth(86f * escalaRodape).right();
        } else {
            rodape.add().expandX();
            rodape.add(fecharButton).width(86f * escalaRodape).height(42f * escalaRodape).right();
        }
        rodape.invalidateHierarchy();
    }

    /** Botao extra na ultima pagina (direita), ex: "Pay" com o icone do vendedor. */
    public void definirAcao(String texto, TextureRegion icone, Runnable acao) {
        this.acao = acao;
        botaoAcao.setText(texto);
        iconeAcao.setDrawable(icone != null ? new TextureRegionDrawable(icone) : null);
        atualizarBotaoRodape();
    }

    /** Resposta do NPC (ex: depois do Pay): troca a fala por essa, sem botao de acao. */
    public void mostrarResposta(String fala) {
        if (!root.isVisible()) return;
        this.paginas = new String[]{fala};
        this.paginaAtual = 0;
        this.acao = null;
        iniciarPagina();
    }

    public void atualizar() {
        if (!root.isVisible() || caracteresVisiveis >= falaCompleta.length()) return;
        relogioLetraMs += Gdx.graphics.getDeltaTime() * 1000f;
        long necessarioMs = INTERVALO_LETRA_MS + atrasoExtraPendenteMs;
        if (relogioLetraMs < necessarioMs) return;
        // Descarta a sobra (nao "while" ate' zerar) - no maximo 1 letra por
        // chamada, mesmo se um frame tiver demorado bem mais que o intervalo.
        relogioLetraMs = 0f;
        char letra = falaCompleta.charAt(caracteresVisiveis);
        // Tags de markup ("[#AARRGGBB]"/"[]") sao invisiveis - reveladas de
        // 1 vez junto com a PROXIMA letra de verdade, senao a digitacao
        // parece travar por varios intervalos revelando so' pontuacao
        // invisivel (ver pularTags()).
        caracteresVisiveis = pularTags(caracteresVisiveis + 1);
        atrasoExtraPendenteMs = pausaExtra(letra);
        falaLabel.setText(falaCompleta.substring(0, caracteresVisiveis));
    }

    private int pularTags(int indice) {
        while (indice < falaCompleta.length() && falaCompleta.charAt(indice) == '[') {
            int fim = falaCompleta.indexOf(']', indice);
            if (fim < 0) break;
            indice = fim + 1;
        }
        return indice;
    }

    private static long pausaExtra(char letra) {
        switch (letra) {
            case '.': case '!': case '?': return PAUSA_FORTE_MS;
            case ',': case ';': case ':': return PAUSA_FRACA_MS;
            default: return 0L;
        }
    }

    /** Completa a pagina atual (se ainda digitando), senao avanca pra
     * proxima pagina, senao fecha (ultima pagina) - mesma acao pro clique no
     * botao (Next/Close), tecla E e o avanco manual existente. */
    public boolean avancarOuFechar() {
        if (!root.isVisible()) return false;
        if (caracteresVisiveis < falaCompleta.length()) {
            caracteresVisiveis = falaCompleta.length();
            falaLabel.setText(falaCompleta);
        } else if (paginaAtual < paginas.length - 1) {
            paginaAtual++;
            iniciarPagina();
        } else {
            fechar();
        }
        return true;
    }

    /** True quando o jogador ja chegou na ULTIMA pagina da fala atual (nao
     * precisa ter terminado de "digitar" ela) - usado por quem chama
     * abrir() pra saber, dentro do callback de fechar(), se deve marcar o
     * dialogo como "ja visto" (client e servidor) ou se foi um fechamento
     * no meio do caminho (ESC numa pagina intermediaria, por ex). */
    public boolean estaNaUltimaPagina() {
        return paginaAtual >= paginas.length - 1;
    }

    public void fechar() {
        if (!root.isVisible()) return;
        root.setVisible(false);
        aoFechar.run();
    }

    public boolean isVisible() {
        return root.isVisible();
    }
}
