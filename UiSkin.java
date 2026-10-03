package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

/**
 * Skin das telas novas (login/char-select/etc) - cores/fontes tiradas direto
 * de jogo/main_menu.tscn (StyleBoxFlat dos botoes/paineis, martel.ttf pro
 * titulo, dejavu-sans pro corpo, outline preto em tudo igual
 * start_screen.gd::aplicar_contorno_em_todos) pra bater com os prints que o
 * usuario mandou, em vez de inventar uma paleta nova.
 */
public class UiSkin {

    public static final Color COR_PAINEL = new Color(0.035f, 0.035f, 0.035f, 0.906f);
    public static final Color COR_BORDA_PAINEL = new Color(0.42f, 0.42f, 0.42f, 1f);
    public static final Color COR_CAMPO = new Color(0.05f, 0.05f, 0.07f, 0.95f);
    // Borda do campo de texto quase invisivel de proposito - no jogo real o
    // campo so se distingue do painel por uma sombra bem sutil, nao por um
    // contorno cinza forte tipo os paineis/botoes.
    public static final Color COR_BORDA_CAMPO = new Color(0.14f, 0.14f, 0.16f, 1f);

    /**
     * @param escala fator entre a resolucao real do device e o mundo virtual
     *               do ExtendViewport (1280x720) no momento em que a tela foi
     *               criada. Gera a textura da fonte num tamanho de pixel maior
     *               (bater com a resolucao real) e usa setScale pra ela voltar
     *               a ocupar o mesmo tamanho em unidades de stage - sem isso,
     *               o ExtendViewport estica a textura pequena e borra o texto
     *               em telas com mais pixels que 1280x720 (a maioria dos
     *               celulares). Calculado uma unica vez na criacao da tela,
     *               NUNCA dentro de resize() - o teclado do Android dispara
     *               resize ao abrir/fechar, e regenerar fonte FreeType (~6
     *               fontes, rasterizacao + upload de textura) nesse callback
     *               reintroduziria o mesmo lag que motivou a saida do Godot.
     */
    public static Skin criar(float escala) {
        Skin skin = new Skin();

        // Contorno dos textos em martel (titulo/botoes/secoes) - uma rodada
        // anterior tentou reduzir so' no PC (supondo que "escala" maior la
        // deixava a borda proporcionalmente mais grossa), mas o resultado
        // foi o oposto do esperado: o piso de 1px de borda EM TEXTURA, depois
        // encolhido de volta por setScale(1/escala), acabava em MENOS de 1
        // pixel de tela real no PC (escala tipicamente maior ali) - ficava
        // fina/fraca em vez de grossa. Valor unico pra qualquer plataforma
        // (os mesmos numeros que ja funcionavam bem no mobile) evita essa
        // armadilha.
        int bordaMartelGrande = 3;
        int bordaMartelPequena = 2;

        // Trocado de NicoClean pra dejavu-sans.condensed a pedido do usuario -
        // mesma fonte que ja era usada em "Beta version" (fonteVersao abaixo),
        // ficou mais legivel nos campos de formulario. Nao bate mais 100% com
        // o main_menu.tscn original do Godot (que usava NicoClean aqui), foi
        // uma escolha estetica deliberada por cima da fidelidade visual.
        BitmapFont fonteCorpo = gerarFonte("fonts/dejavu-sans.condensed.ttf", 20, 2, escala);
        // Fonte dedicada do TextField, sem contorno - os campos ja tem fundo
        // cinza solido + texto branco (COR_CAMPO/COR_BORDA_CAMPO), contraste
        // suficiente sem precisar da borda preta (a pedido do usuario, que
        // achou a borda desnecessaria/estranha so' ali - "default-font"
        // continua com borda pros labels soltos por cima do fundo de tela,
        // que PRECISAM do contorno pra continuar legiveis).
        BitmapFont fonteCampoTexto = gerarFonte("fonts/dejavu-sans.condensed.ttf", 20, 0, escala);
        BitmapFont fonteTitulo = gerarFonte("fonts/martel.ttf", 100, bordaMartelGrande, escala);
        BitmapFont fonteSubtitulo = gerarFonte("fonts/martel.ttf", 34, bordaMartelPequena, escala);
        BitmapFont fonteVersao = gerarFonte("fonts/dejavu-sans.condensed.ttf", 16, 2, escala);
        // Todo botao do jogo real usa martel (mesma fonte do titulo), nao uma
        // fonte generica - ver PlayBtn/BackBtn/CreateBtn etc em main_menu.tscn.
        BitmapFont fonteBotao = gerarFonte("fonts/martel.ttf", 32, bordaMartelPequena, escala);
        // Trocada de NicoClean (pixelada, ficava embacada nas stats do
        // char-select) pra dejavu-sans.condensed - mesma familia do "Beta
        // version" - e subida 8px (24 -> 32) a pedido do usuario. Contorno
        // reduzido de 4 pra 2 (o valor de LabelSettings do Godot, outline=6,
        // nao bate 1:1 com o parametro borderWidth do FreeType aqui -
        // visualmente ficava mais grosso que a referencia real).
        BitmapFont fonteInfo = gerarFonte("fonts/dejavu-sans.condensed.ttf", 32, 2, escala);
        // "Lv. X" no char-select - mesma familia/tamanho-base do "default"
        // (fonteCorpo), so' 8px maior (20 -> 28), a pedido do usuario -
        // precisa de instancia PROPRIA (nao só aumentar fonteCorpo) porque
        // "default" e' reusado em campos/labels no resto de todas as telas.
        BitmapFont fonteNivel = gerarFonte("fonts/dejavu-sans.condensed.ttf", 28, 2, escala);
        // Fontes exclusivas da tela de Settings/Options (CanvasLayer/SettingsMenu
        // e CanvasLayer/OptionsMenu em player.tscn) - tamanhos tirados direto de
        // la (SectionLabel=48, TitleLabel=70), nao reaproveitadas porque nao
        // existe nenhum texto desse tamanho no resto do jogo ainda portado.
        BitmapFont fonteSecao = gerarFonte("fonts/martel.ttf", 48, bordaMartelGrande, escala);
        BitmapFont fonteOpcoesTitulo = gerarFonte("fonts/martel.ttf", 70, bordaMartelGrande, escala);
        // Variante menor do martel de botao (20, nao 32) - usada onde o texto
        // e' comprido demais pro tamanho padrao (ex: "Return to game" no
        // popup de pause). player.tscn usa font_size=24 pro BackBtn real, mas
        // o martel renderizado aqui pelo FreeType fica mais largo por
        // caractere que no Godot nesse mesmo tamanho nominal - 24 ainda
        // vazava do botao de 200px, 20 coube (achado testando ao vivo).
        BitmapFont fonteBotaoPequeno = gerarFonte("fonts/martel.ttf", 20, bordaMartelPequena, escala);
        // FPS/ms do WorldScreen - sem contorno (borda=0) a pedido do usuario,
        // que achava o texto de debug com a borda preta pesada demais pra um
        // numero pequeno no canto da tela.
        BitmapFont fonteHud = gerarFonte("fonts/dejavu-sans.condensed.ttf", 18, 0, escala);
        skin.add("hud-font", fonteHud);
        skin.add("hud", new Label.LabelStyle(fonteHud, Color.WHITE));
        // Log do chat - sem contorno (fundo escuro + texto branco ja tem
        // contraste, igual o campo de texto) e com MARKUP ligado (so'
        // nessa fonte - nao na do TextField, pra um "[" digitado de verdade
        // pelo usuario nao virar tag de cor por engano) pra colorir
        // timestamp/nome por dentro da MESMA linha (ver ChatUI).
        BitmapFont fonteChatLog = gerarFonte("fonts/dejavu-sans.condensed.ttf", 20, 0, escala);
        fonteChatLog.getData().markupEnabled = true;
        skin.add("chat-log", new Label.LabelStyle(fonteChatLog, Color.WHITE));
        // "+"/"-" do ChatUI - gerada JA no tamanho final (34px), em vez de
        // reusar "default-font" (20px) upscalado via setFontScale(1.6) - usar
        // fontScale pra aumentar alem do tamanho em que a textura foi
        // rasterizada estica os pixels da textura (borra) em vez de desenhar
        // com nitidez; gerar direto nesse tamanho evita o blur (achado pelo
        // usuario testando).
        BitmapFont fonteSimbolo = gerarFonte("fonts/dejavu-sans.condensed.ttf", 34, 2, escala);
        skin.add("simbolo-font", fonteSimbolo);
        skin.add("default-font", fonteCorpo);
        skin.add("titulo-font", fonteTitulo);
        skin.add("botao-pequeno-font", fonteBotaoPequeno);
        // Exposta por nome pra telas poderem trocar o font de UM botao/label
        // especifico pra essa (ex: AuthScreen::deleteBtn - "Are you sure?"/
        // "100% sure?" tem numero/simbolo que a fonte martel dos botoes nao
        // cobre direito) sem precisar clonar/gerar outra fonte.
        skin.add("versao-font", fonteVersao);

        skin.add("default", new Label.LabelStyle(fonteCorpo, Color.WHITE));
        skin.add("titulo", new Label.LabelStyle(fonteTitulo, Color.WHITE));
        skin.add("subtitulo", new Label.LabelStyle(fonteSubtitulo, Color.WHITE));
        skin.add("versao", new Label.LabelStyle(fonteVersao, new Color(0.2f, 1f, 0.2f, 1f)));
        skin.add("info", new Label.LabelStyle(fonteInfo, Color.WHITE));
        skin.add("nivel", new Label.LabelStyle(fonteNivel, Color.WHITE));
        skin.add("secao", new Label.LabelStyle(fonteSecao, new Color(0f, 1f, 0f, 1f)));
        skin.add("opcoes-titulo", new Label.LabelStyle(fonteOpcoesTitulo, Color.WHITE));
        // fonteCampoTexto (dejavu-sans.condensed SEM contorno) - trocado de
        // fonteBotao (martel) a pedido do usuario, texto de corpo da tela de
        // Settings nao devia usar a fonte decorativa dos titulos/botoes.
        // "secao"/"opcoes-titulo" (acima) continuam em martel de proposito -
        // sao titulo/cabecalho, nao corpo. fonteCampoTexto (nao fonteCorpo)
        // porque o usuario tambem pediu pra tirar o contorno preto desse
        // texto - a mesma fonte/tamanho de fonteCorpo, so' sem o borderWidth
        // (ver comentario dela acima, onde foi gerada so' pro TextField
        // originalmente).
        skin.add("opcoes-label", new Label.LabelStyle(fonteCampoTexto, Color.WHITE));

        // Borda 2px (nao 1px) de proposito - com 1px, em certos tamanhos de
        // celula o nine-patch perdia a borda de um lado por arredondamento de
        // sub-pixel (achado pelo usuario testando).
        NinePatchDrawable fundoCampo = retangulo(COR_CAMPO, COR_BORDA_CAMPO, 2);
        // Margem de verdade entre o texto e a borda esquerda - TextField usa
        // background.getLeftWidth() (nao um padding proprio dele) pra decidir
        // onde o texto comeca; sem isso o texto digitado saia colado na
        // borda (a pedido do usuario). getLeftWidth() == a espessura da
        // borda em si (2px) por padrao - 10px de respiro por cima dela ficou
        // grande demais (o cursor piscando "I" ficava bem afastado da borda,
        // a pedido do usuario) - reduzido pra 4px.
        fundoCampo.setLeftWidth(fundoCampo.getLeftWidth() + 4f);
        // Cursor fino (1px de textura, nao um NinePatch de 4px) - o solido()
        // de baixo usa uma textura 4x4, e TextField desenha o cursor com a
        // largura MINIMA do drawable (getMinWidth() = largura total da
        // textura pra um NinePatch, nao encolhe) - ficava grosso demais
        // (achado pelo usuario testando).
        Drawable cursor = cursorFino(Color.WHITE);
        NinePatchDrawable selecao = solido(new Color(0.5f, 0.4f, 0.2f, 0.5f));
        skin.add("default", new TextField.TextFieldStyle(fonteCampoTexto, Color.WHITE, cursor, selecao, fundoCampo));
        skin.add("campo-normal", fundoCampo, Drawable.class);
        // Campo fica verde quando o valor digitado e' valido, igual
        // start_screen.gd::update_input_bg (mesma cor: Color(0,0.3,0,0.9)).
        // Mesma margem esquerda do fundoCampo normal (ver acima) - sem
        // repetir aqui, o texto voltava a colar na borda assim que o campo
        // ficava valido (marcarValidacao troca pra esse drawable).
        NinePatchDrawable fundoCampoValido = retangulo(new Color(0f, 0.3f, 0f, 0.9f), COR_BORDA_CAMPO, 2);
        fundoCampoValido.setLeftWidth(fundoCampoValido.getLeftWidth() + 4f);
        skin.add("campo-valido", fundoCampoValido, Drawable.class);

        // Sem fundo/scrollbar desenhados de proposito (igual TesteGame::criarSkinMinima)
        // - so' existe pra ScrollPane(actor, skin) achar um estilo "default"
        // registrado (ChatUI usa esse construtor) - sem isso o Skin.get()
        // lanca GdxRuntimeException na hora de montar a tela (crash duro,
        // fecha a janela inteira - a construcao do WorldScreen e' sincrona).
        skin.add("default", new ScrollPane.ScrollPaneStyle());

        // "zoom-select" - dropdown do Zoom da Camera no Options, pedido pelo
        // usuario pra ficar igual ao SelectBox real do Mirage Realms
        // (print de referencia "Selecao de zoom") - cor amostrada direto do
        // print: caixa/lista em cinza escuro solido (73,73,73), item
        // selecionado destacado em teal solido (0,127,127). Fundo SOLIDO
        // (solido(), nao retangulo() com borda) e fonte sem contorno
        // (fonteCampoTexto) - usuario pediu pra tirar tanto a borda preta da
        // caixa quanto o contorno do texto dela. Sem o icone de seta pra
        // cima/baixo do canto direito do original - nao ha asset pra isso
        // no projeto ainda, so' a caixa/lista mesmo.
        Color corCaixaSelect = new Color(73f / 255f, 73f / 255f, 73f / 255f, 1f);
        Color corSelecaoTeal = new Color(0f, 127f / 255f, 127f / 255f, 1f);
        SelectBox.SelectBoxStyle estiloSelect = new SelectBox.SelectBoxStyle();
        estiloSelect.font = fonteCampoTexto;
        estiloSelect.fontColor = Color.WHITE;
        estiloSelect.background = solido(corCaixaSelect);
        estiloSelect.scrollStyle = new ScrollPane.ScrollPaneStyle();
        List.ListStyle estiloLista = new List.ListStyle();
        estiloLista.font = fonteCampoTexto;
        estiloLista.fontColorSelected = Color.WHITE;
        estiloLista.fontColorUnselected = Color.WHITE;
        estiloLista.selection = solido(corSelecaoTeal);
        estiloLista.background = solido(corCaixaSelect);
        estiloSelect.listStyle = estiloLista;
        skin.add("zoom-select", estiloSelect);

        registrarBotao(skin, "default",
            new Color(0.416f, 0.416f, 0.416f, 1f), new Color(0.536f, 0.536f, 0.536f, 1f), new Color(0.306f, 0.306f, 0.306f, 1f),
            new Color(0.337f, 0.337f, 0.337f, 1f), fonteBotao);
        registrarBotao(skin, "verde",
            new Color(0.144f, 0.743f, 0f, 1f), new Color(0.196f, 0.941f, 0f, 1f), new Color(0.092f, 0.529f, 0f, 1f),
            new Color(0.196f, 0.941f, 0f, 1f), fonteBotao);
        registrarBotao(skin, "vermelho",
            new Color(0.688f, 0f, 0f, 1f), new Color(0.983f, 0.001f, 0.001f, 1f), new Color(0.460f, 0f, 0f, 1f),
            new Color(0.8f, 0f, 0.01f, 1f), fonteBotao);
        registrarBotao(skin, "laranja",
            new Color(0.892f, 0.366f, 0f, 1f), new Color(1f, 0.495f, 0.247f, 1f), new Color(0.7f, 0.28f, 0f, 1f),
            new Color(1f, 0.5f, 0.1f, 1f), fonteBotao);

        // Cores exatas do popup de pause (CanvasLayer/SettingsMenu em
        // player.tscn: StyleBoxFlat_q13i1/_bwjto/_ivps1) - palheta propria,
        // um pouco diferente do menu inicial (verde/vermelho de la nao batem
        // 1:1 com esses aqui).
        registrarBotao(skin, "cinza-popup",
            new Color(0.478f, 0.478f, 0.478f, 1f), new Color(0.345f, 0.345f, 0.345f, 1f), new Color(0.584f, 0.584f, 0.584f, 1f),
            Color.BLACK, fonteBotao);
        registrarBotao(skin, "verde-popup",
            new Color(0.00001f, 0.645f, 0f, 1f), new Color(0.00001f, 0.436f, 0f, 1f), new Color(0.201f, 1f, 0.180f, 1f),
            new Color(0.28f, 1f, 0.255f, 1f), fonteBotao, new Color(0.597f, 1f, 0.566f, 1f));
        registrarBotao(skin, "vermelho-popup",
            new Color(0.733f, 0f, 0f, 1f), new Color(0.573f, 0f, 0f, 1f), new Color(0.945f, 0.0007f, 0.0004f, 1f),
            new Color(1f, 0.26f, 0.2f, 1f), fonteBotao, new Color(1f, 0.657f, 0.608f, 1f));
        // BackBtn do rodape do OptionsMenu (StyleBoxFlat_7ahtn/_7lmhl/_dx0e4).
        registrarBotao(skin, "verde-rodape",
            new Color(0.285f, 0.578f, 0f, 1f), new Color(0.161f, 0.344f, 0f, 1f), new Color(0.377f, 0.744f, 0f, 1f),
            new Color(0.156f, 0.964f, 0f, 1f), fonteBotao);

        skin.add("popup-painel", retangulo(new Color(0.1025f, 0.1025f, 0.1025f, 1f), new Color(0.4935f, 0.4935f, 0.4935f, 1f), 1), Drawable.class);
        // Fundo da tela cheia de Options - o Godot original nao tem isso (o
        // OptionsMenu de player.tscn e' transparente, so' TopBar/BottomBar tem
        // cor), mas a pedido do usuario: cinza escuro e' semi-transparente pra
        // o mundo continuar visivel atras enquanto a tela esta aberta.
        skin.add("fundo-opcoes", solido(new Color(0.08f, 0.08f, 0.08f, 0.82f)), Drawable.class);

        // "link" - texto sublinhado sem caixa/borda nenhuma, tipo "Forgot
        // Password?" no jogo real (nao e um botao retangular cinza).
        TextButton.TextButtonStyle link = new TextButton.TextButtonStyle();
        link.font = fonteCorpo;
        link.fontColor = Color.WHITE;
        link.up = linhaEmbaixo(Color.WHITE);
        link.down = linhaEmbaixo(new Color(0.7f, 0.7f, 0.7f, 1f));
        link.over = linhaEmbaixo(new Color(0.85f, 0.85f, 0.85f, 1f));
        skin.add("link", link);

        skin.add("painel", retangulo(COR_PAINEL, COR_BORDA_PAINEL, 1), Drawable.class);
        skin.add("barra-topo", retanguloBordaUnica(COR_PAINEL, COR_BORDA_PAINEL, false), Drawable.class);
        skin.add("barra-baixo", retanguloBordaUnica(COR_PAINEL, COR_BORDA_PAINEL, true), Drawable.class);

        return skin;
    }

    private static void registrarBotao(Skin skin, String nome, Color up, Color down, Color over, Color borda, BitmapFont fonte) {
        registrarBotao(skin, nome, up, down, over, borda, fonte, Color.WHITE);
    }

    private static void registrarBotao(Skin skin, String nome, Color up, Color down, Color over, Color borda, BitmapFont fonte, Color corTexto) {
        TextButton.TextButtonStyle estilo = new TextButton.TextButtonStyle();
        estilo.font = fonte;
        estilo.fontColor = corTexto;
        estilo.up = retangulo(up, borda, 1);
        estilo.down = retangulo(down, borda, 1);
        estilo.over = retangulo(over, borda, 1);
        estilo.disabled = retangulo(new Color(0.15f, 0.15f, 0.15f, 0.8f), new Color(0.3f, 0.3f, 0.3f, 1f), 1);
        skin.add(nome, estilo);
    }

    // Pacote-privado (nao mais private) - DialogoNPCUI reusa isso pra gerar
    // uma instancia PROPRIA (nao compartilhada) da fonte do corpo, com
    // markup/glyph de icone remapeados so' nela (ver comentario la).
    static BitmapFont gerarFonte(String caminho, int tamanho, int borda, float escala) {
        FreeTypeFontGenerator gerador = new FreeTypeFontGenerator(Gdx.files.internal(caminho));
        FreeTypeFontParameter parametros = new FreeTypeFontParameter();
        parametros.size = Math.round(tamanho * escala);
        parametros.minFilter = TextureFilter.Linear;
        parametros.magFilter = TextureFilter.Linear;
        // Borda tambem escalada - senao ela fica proporcionalmente mais fina
        // que o glifo (que cresceu com o size acima) e o contorno preto some.
        parametros.borderWidth = borda > 0 ? Math.max(1, Math.round(borda * escala)) : 0;
        parametros.borderColor = Color.BLACK;
        BitmapFont fonte = gerador.generateFont(parametros);
        // Volta o tamanho renderizado pra escala de stage original (a
        // textura por tras continua na resolucao real do device, so o
        // layout/metrica que encolhe de volta) - isso e' o que da nitidez.
        fonte.getData().setScale(1f / escala);
        gerador.dispose();
        return fonte;
    }

    /** Retangulo de cantos retos (StyleBoxFlat do Godot nao arredonda nada nas
     * telas de login/char-select - so 1px de borda solida). */
    public static NinePatchDrawable retangulo(Color preenchimento, Color borda, int espessuraBorda) {
        int tamanho = 16;
        Pixmap pm = new Pixmap(tamanho, tamanho, Pixmap.Format.RGBA8888);
        pm.setColor(borda);
        pm.fill();
        pm.setColor(preenchimento);
        pm.fillRectangle(espessuraBorda, espessuraBorda, tamanho - 2 * espessuraBorda, tamanho - 2 * espessuraBorda);
        Texture tex = new Texture(pm);
        pm.dispose();
        return new NinePatchDrawable(new NinePatch(tex, espessuraBorda, espessuraBorda, espessuraBorda, espessuraBorda));
    }

    /** Mesma barra escura do painel, mas com borda so de um lado (topo ou
     * baixo) - as barras de header/rodape do Godot fazem isso (ver
     * StyleBoxFlat_vno1p/StyleBoxFlat_gtard). */
    public static NinePatchDrawable retanguloBordaUnica(Color preenchimento, Color borda, boolean bordaEmCima) {
        int tamanho = 16;
        Pixmap pm = new Pixmap(tamanho, tamanho, Pixmap.Format.RGBA8888);
        pm.setColor(preenchimento);
        pm.fill();
        pm.setColor(borda);
        if (bordaEmCima) {
            pm.drawLine(0, 0, tamanho - 1, 0);
        } else {
            pm.drawLine(0, tamanho - 1, tamanho - 1, tamanho - 1);
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        return new NinePatchDrawable(new NinePatch(tex, 0, 0, bordaEmCima ? 1 : 0, bordaEmCima ? 0 : 1));
    }

    /** So uma linha de 1px na base, resto transparente - usado pro estilo
     * "link" (texto sublinhado sem fundo/borda). */
    private static NinePatchDrawable linhaEmbaixo(Color cor) {
        int tamanho = 16;
        Pixmap pm = new Pixmap(tamanho, tamanho, Pixmap.Format.RGBA8888);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        pm.setColor(cor);
        pm.drawLine(0, tamanho - 1, tamanho - 1, tamanho - 1);
        Texture tex = new Texture(pm);
        pm.dispose();
        return new NinePatchDrawable(new NinePatch(tex, 0, 0, 0, 1));
    }

    private static NinePatchDrawable solido(Color cor) {
        Pixmap pm = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pm.setColor(cor);
        pm.fill();
        Texture tex = new Texture(pm);
        pm.dispose();
        return new NinePatchDrawable(new NinePatch(tex, 1, 1, 1, 1));
    }

    /** TextureRegionDrawable (nao NinePatch) de 1x1 - getMinWidth() devolve a
     * largura de verdade da textura (1), entao TextField desenha um cursor
     * genuinamente fino, ao contrario de um NinePatch que nunca encolhe
     * abaixo da textura de origem inteira. */
    private static Drawable cursorFino(Color cor) {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(cor);
        pm.fill();
        Texture tex = new Texture(pm);
        pm.dispose();
        return new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(
            new com.badlogic.gdx.graphics.g2d.TextureRegion(tex));
    }
}
