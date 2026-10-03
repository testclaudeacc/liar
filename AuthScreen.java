package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.teste.game.rede.CharacterData;
import com.teste.game.rede.ServerApi;
import com.teste.game.rede.Sessao;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Porta funcional + visual de start_screen.gd (menu principal/login/registro/
 * esqueci senha/selecao de personagem/selecao de classe/criacao) contra o
 * ./server de verdade. Cores, fontes e assets tirados direto de
 * jogo/main_menu.tscn pra bater com os prints de referencia que o usuario
 * mandou (ver pasta ~/Imagens) - sem LML ainda (risco de resolucao via
 * JitPack, ver memoria libgdx_migration), Scene2D puro com Table
 * grow()/expand() + FitViewport(1280,720) pra escalar direito em telas com
 * proporcao diferente.
 */
public class AuthScreen extends ScreenAdapter {

    public interface PlayListener {
        void onPlay(int userId, CharacterData personagem);
    }

    private static final int MAX_SLOTS = 4;
    // Mundo virtual do ExtendViewport - era 1280x720; reduzido (mantendo a
    // proporcao 16:9) pra 960x540 a pedido do usuario ("aumentar literalmente
    // tudo" no mobile). Cada widget/fonte e' definido em unidades desse mundo
    // virtual, entao encolher o mundo faz TUDO ocupar uma fatia maior da tela
    // real automaticamente (1280/960 = 720/540 = 1.333x maior), sem precisar
    // tocar em nenhuma constante de tamanho individual pelo resto do arquivo.
    private static final float MUNDO_VIRTUAL_LARGURA = 960f;
    private static final float MUNDO_VIRTUAL_ALTURA = 540f;
    // Mesmo spritesheet/quadro parado "de frente" que o WorldScreen usa pro
    // jogador de verdade (ver WorldScreen::FRAME_LARGURA/FRAME_INDICE_FRENTE)
    // - preview do slot reaproveita o quadro 3, so' que parado (sem andar).
    private static final int FRAME_LARGURA = 16;
    private static final int FRAME_INDICE_FRENTE = 3;
    private static final Pattern EMAIL_REGEX = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private static final String[] BANNED_NAMES = {
        "admin", "administrator", "gm", "gamemaster", "mod", "moderator",
        "system", "server", "owner", "staff", "support", "dev", "developer", "root",
        "hitler", "nazi", "stalin", "putin", "mussolini", "polpot", "osama", "binladen",
        "saddam", "hussein", "gaddafi", "pinochet", "maozedong", "kimjong", "idi_amin",
        "bokassa", "franco", "videla", "fidel", "castro", "chavez", "maduro", "fascist",
        "communist", "lula", "trump", "biden",
        "epstein", "watergate", "mensalao", "petrolao", "lavajato", "odebrecht",
        "madoff", "manson", "dahmer", "bundy", "gacy", "pabloescobar", "elchapo",
        "zodiac", "columbine",
        "rape", "pedophile", "pedo", "incest", "terrorist", "murderer", "slave",
        "suicide", "kill", "homicide", "racist", "kkk", "estupro", "assassino",
        "gay", "lgbt", "lesbian", "lesbica", "trans", "queer", "homo", "bi", "bicha",
        "viado", "sapatao", "traveco", "fag", "dyke",
        "fuck", "shit", "bitch", "cunt", "asshole", "dick", "cock", "pussy",
        "slut", "whore", "bastard", "twat", "wanker", "prick", "boob", "tits",
        "porn", "nude", "naked", "cum", "jizz", "suck", "blowjob", "handjob",
        "vagina", "penis", "semen",
        "puta", "caralho", "buceta", "pica", "cu", "merda", "porra", "fuder",
        "foda", "arrombado", "corno", "vadia", "piranha", "cacete", "gozar",
        "punheta", "boquete", "bosta", "xoxota", "rola", "caceta", "siririca",
        "macaco", "fdp", "cuzao", "pnc"
    };

    private static class ClasseInfo {
        final String titulo, desc, primeiraSkill;
        final Color cor;
        ClasseInfo(String t, String d, String skill, Color c) { titulo = t; desc = d; primeiraSkill = skill; cor = c; }
    }

    // Cores exatas de SKILL_DISPLAY (start_screen.gd) - a cor da skill NAO e'
    // a cor da classe (ex: Mage e' roxo mas "Magic" e' rosa).
    private static final Map<String, Color> CORES_SKILL = new LinkedHashMap<>();
    static {
        CORES_SKILL.put("Magic", Color.valueOf("ff76fc"));
        CORES_SKILL.put("Focus", Color.valueOf("96f161"));
        CORES_SKILL.put("Melee", Color.valueOf("dcdcdc"));
        CORES_SKILL.put("Musicality", Color.valueOf("ffa24e"));
    }

    private static final Map<String, ClasseInfo> CLASSES = new LinkedHashMap<>();
    static {
        CLASSES.put("Mage", new ClasseInfo("SORCERER", "Fragile spellcaster who wipes out groups with AOE magic. Very low HP, Massive damage.", "Magic", new Color(0.68f, 0.28f, 1.0f, 1f)));
        CLASSES.put("Knight", new ClasseInfo("KNIGHT", "Frontline tank built to take hits and lead the charge. High HP, Moderate damage.", "Melee", new Color(0.75f, 0.75f, 0.75f, 1f)));
        CLASSES.put("Ranger", new ClasseInfo("RANGER", "Precise marksman who drops single targets from afar. Moderate HP, Moderate damage.", "Focus", new Color(0.2f, 0.8f, 0.2f, 1f)));
        CLASSES.put("Bard", new ClasseInfo("BARD", "Support caster who keeps the party alive with heals and buffs. Low HP, Moderate damage.", "Musicality", new Color(1.0f, 0.84f, 0.0f, 1f)));
    }

    // Tamanhos de botao (menu principal/rodape/selecao de personagem) sao
    // definidos pensando no mobile primeiro (ver comentario de
    // MUNDO_VIRTUAL_LARGURA) - ficavam grandes demais no PC (a pedido do
    // usuario); reduzidos so' quando !mobile, mesmo padrao ja usado em
    // WorldScreen::TAMANHO_BOTAO_TOPO.
    private final boolean mobile = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
        || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;

    private final Stage stage;
    private final Skin skin;
    private final Table root;
    private final Table content;
    private final Cell<Table> contentCell;
    private final Table bottomBar;
    private final Table tituloBox;
    private final Label topBar;
    private final Label versaoLabel;
    private final ImageButton logoutBtn;
    private final PlayListener playListener;

    // Atlas unico (graphics/graphics.atlas+.png, gerado por "gradlew
    // desktop:pack" a partir de core/assets-raw/graphics/**) - substitui os
    // ~20 Texture individuais que existiam aqui antes (1 bind de GPU por
    // frame em vez de 1 por sprite/icone desenhado). Nome de cada regiao e'
    // o caminho relativo a assets-raw/graphics SEM extensao (ex:
    // "ui/armas/Bard"). Pra adicionar um icone/skin novo: solta o PNG numa
    // subpasta de core/assets-raw/graphics/, roda "gradlew desktop:pack" de
    // novo, usa atlas.findRegion("subpasta/nome") - nao precisa anotar
    // coordenada nenhuma, o packer resolve isso sozinho.
    private final TextureAtlas atlas;
    // Fundo NAO fica mais no atlas de sprites (imagem de cenario de 640x360,
    // nao faz sentido junto de icone/sprite de 16x16 - desperdicava espaco e
    // saia do proposito do atlas) - Texture solta, carregada/descartada por
    // conta propria.
    private final Texture fundoTexture;
    private final Map<String, TextureRegion> texturas = new LinkedHashMap<>();
    // Reusado por encaixarTexto() - so' pra medir largura, nunca desenhado.
    private final GlyphLayout medidaTexto = new GlyphLayout();

    private int currentUserId = -1;
    private final CharacterData[] slots = new CharacterData[MAX_SLOTS];
    private int currentSlotIndex = 0;
    private int deleteConfirmStage = 0;
    private String selectedClassKey = "";
    private int forgotStep = 0;

    public AuthScreen(PlayListener playListener) {
        this(playListener, null);
    }

    /** mensagemInicial: mostrado como um telaErro() assim que a tela abre -
     * usado quando o jogo te devolve pra ca por causa de algo que aconteceu
     * (ex: "Server shutdown" quando o servidor cai enquanto voce tava no
     * mundo, ver InvertedRealmsGame::onDisconnected). null = comportamento
     * normal, abre direto no menu principal. */
    public AuthScreen(PlayListener playListener, String mensagemInicial) {
        this.playListener = playListener;
        // Fator entre a resolucao real do device (aqui, ainda cheia - antes
        // de qualquer teclado abrir) e o mundo virtual do ExtendViewport
        // abaixo. E' o mesmo calculo que o Viewport faz por dentro pra
        // decidir a escala uniforme (a menor das duas razoes, pra manter a
        // proporcao) - usado so aqui, uma vez, pra gerar a fonte (UiSkin) ja
        // na resolucao de pixel certa e evitar ela ser esticada (borrada)
        // depois pelo proprio viewport.
        // getBackBufferWidth/Height (nao getWidth/Height) - ver o mesmo
        // comentario em WorldScreen::construirUiSettings (HiDPI no desktop
        // Linux/Windows faz esses 2 pares de valor divergirem e borra a
        // fonte quando o ExtendViewport estica ela pro framebuffer real).
        float escalaFonte = Math.min(Gdx.graphics.getBackBufferWidth() / MUNDO_VIRTUAL_LARGURA, Gdx.graphics.getBackBufferHeight() / MUNDO_VIRTUAL_ALTURA);
        skin = UiSkin.criar(escalaFonte);
        stage = new Stage(new ExtendViewport(MUNDO_VIRTUAL_LARGURA, MUNDO_VIRTUAL_ALTURA));
        Gdx.input.setInputProcessor(stage);

        atlas = new TextureAtlas(Gdx.files.internal("graphics/graphics.atlas"));
        fundoTexture = new Texture(Gdx.files.internal("backgrounds/background.png"));
        Image fundo = new Image(new TextureRegionDrawable(new TextureRegion(fundoTexture)));
        fundo.setScaling(Scaling.fill);
        fundo.setFillParent(true);
        stage.addActor(fundo);

        for (String key : CLASSES.keySet()) {
            texturas.put("classe-" + key, atlas.findRegion("ui/" + key + "Icon"));
            // Grade de selecao de classe usa um conjunto de icones DIFERENTE
            // (arquivos com "_Icon", na raiz do projeto Godot) do icone
            // pequeno usado no painel de stats do char-select - descoberto
            // comparando print antigo x novo, nao e o mesmo arquivo.
            texturas.put("classe-grande-" + key, atlas.findRegion("ui/" + key + "_Icon"));
        }
        texturas.put("corpo-base", atlas.findRegion("sprites/base/BaseSoul"));
        texturas.put("logout", atlas.findRegion("ui/Logout"));
        texturas.put("discord", atlas.findRegion("ui/Discord"));
        texturas.put("seta-esq", atlas.findRegion("ui/LeftArrow"));
        texturas.put("seta-dir", atlas.findRegion("ui/RightArrow"));
        texturas.put("defesa", atlas.findRegion("ui/DefenseIcon"));
        texturas.put("tempo", atlas.findRegion("ui/TimeIcon"));
        texturas.put("xp", atlas.findRegion("ui/XPIcon"));
        // Icone da "primeira skill" no char-select: pra Magic (Mage) usa o
        // icone da ARMA inicial (cajado, ui/armas/Mage.png) porque nao existe
        // MagicIcon.png em lugar nenhum do projeto Godot original (conferido
        // de novo, a pedido do usuario, 2026-09-27) - pras outras 3 classes
        // existe icone de skill de verdade (Focus/Musicality certos, Melee so'
        // com o typo "MeeleIcon" que ja vem do proprio Godot).
        for (String key : CLASSES.keySet()) {
            texturas.put("arma-" + key, atlas.findRegion("ui/armas/" + key));
        }
        texturas.put("skill-Focus", atlas.findRegion("ui/FocusIcon"));
        texturas.put("skill-Musicality", atlas.findRegion("ui/MusicalityIcon"));
        texturas.put("skill-Melee", atlas.findRegion("ui/MeeleIcon"));

        root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        topBar = new Label("MITERA ONLINE", skin, "titulo");
        topBar.setAlignment(Align.center);
        versaoLabel = new Label("Beta version v1.0", skin, "versao");

        logoutBtn = new ImageButton(new ImageButton.ImageButtonStyle());
        logoutBtn.getStyle().imageUp = new TextureRegionDrawable(texturas.get("logout"));
        // ImageButton nao estica a imagem pro tamanho da celula sozinho (ao
        // contrario de Image) - sem isso o icone ficava minusculo (16x16 cru)
        // dentro de uma celula bem maior.
        logoutBtn.getImageCell().size(48);
        logoutBtn.setVisible(false);
        logoutBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                Sessao.limpar();
                currentUserId = -1;
                logoutBtn.setVisible(false);
                telaMenuPrincipal();
            }
        });

        Image discordImg = new Image(new TextureRegionDrawable(texturas.get("discord")));

        tituloBox = new Table();
        montarTituloBox(true);

        Table headerRow = new Table();
        headerRow.top();
        headerRow.add(logoutBtn).size(48).top().left().pad(10).width(68);
        headerRow.add(tituloBox).expandX().top().padLeft(-68);
        headerRow.add(discordImg).size(48).top().right().pad(10);

        content = new Table();

        bottomBar = new Table();
        bottomBar.setBackground(skin.getDrawable("barra-baixo"));
        bottomBar.pad(10);

        root.top();
        root.add(headerRow).growX().row();
        // Guardada pra poder realinhar so' essa celula em telas especificas
        // (ver telaSelecaoPersonagem) - "content.top()"/"content.center()"
        // chamados DENTRO do proprio content nao bastam pra isso: sem
        // grow()/fill(), o Table "content" sempre fica do tamanho exato do
        // seu conteudo (zero sobra interna pra top()/center() reposicionar),
        // entao quem decide onde esse bloco todo aparece verticalmente e' o
        // alinhamento da CELULA aqui no root, nao o alinhamento interno do
        // content (bug achado nessa investigacao - a chamada antiga
        // "content.top()" em telaSelecaoPersonagem nunca teve efeito nenhum).
        contentCell = root.add(content).expand();
        contentCell.row();
        // Reduzida no PC junto com os botoes de dentro dela (ver
        // ALTURA_BOTAO_RODAPE) - tinha diminuido so' os botoes e esquecido
        // da barra em si, que ficava com uma sobra vazia grande demais
        // embaixo/em cima deles (achado pelo usuario testando).
        root.add(bottomBar).growX().height(mobile ? 110f : 86f);

        telaMenuPrincipal();
        if (mensagemInicial != null) {
            telaErro(mensagemInicial, this::telaMenuPrincipal);
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        // getBackBufferWidth/Height, nao os parametros width/height recebidos
        // aqui - no LWJGL3 esses parametros vem em unidades LOGICAS da janela
        // (GLFW), que divergem dos pixels reais do framebuffer quando o
        // desktop tem escala de tela HiDPI/fracionaria (mesma divergencia
        // documentada em UiSkin.criar()/construirUiSettings() do WorldScreen
        // pra geracao de fonte). A fonte do skin ja' e' gerada casando com o
        // framebuffer real (ver escalaFonte no construtor) - atualizar o
        // viewport com as unidades logicas aqui descasava a escala de
        // desenho da escala em que a fonte foi rasterizada, borrando/
        // desalinhando o texto.
        stage.getViewport().update(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        atlas.dispose();
        fundoTexture.dispose();
    }

    // ---------------------------------------------------------------------
    // Rodape (barra preta debaixo, Back + 1-2 botoes de acao) e cabecalho
    // ---------------------------------------------------------------------
    private void limparConteudo() {
        content.clear();
        content.center();
        // Reseta o alinhamento da CELULA pro padrao (centralizado) - telas
        // especificas (ex: telaSelecaoPersonagem) podem mudar isso de novo
        // depois de chamar limparConteudo().
        contentCell.align(Align.center).pad(0);
        // Reseta o titulo por padrao - telaSelecaoPersonagem/telaSelecaoClasse
        // escondem de novo logo em seguida (ver ocultarTitulo()).
        montarTituloBox(true);
    }

    /** Monta (ou remonta) o tituloBox - reconstroi de verdade em vez de so'
     * usar setVisible(false) no topBar porque um Label INVISIVEL continua
     * reservando a altura da linha da fonte dele no layout do Table (a fonte
     * do titulo e' 100px - "escondido" ainda empurrava tudo do mesmo jeito).
     * Removendo o ator da celula de vez e' a unica forma de recuperar esse
     * espaco de verdade. */
    private void montarTituloBox(boolean comTitulo) {
        tituloBox.clearChildren();
        if (comTitulo) {
            tituloBox.add(topBar).padTop(30).row();
        }
        // "Beta version" some junto com o titulo grande (ver ocultarTitulo) -
        // a pedido do usuario, o texto beta nao deve aparecer na tela de
        // slots/selecao de classe.
        if (comTitulo) {
            tituloBox.add(versaoLabel).right();
        }
    }

    /** So' as telas de selecao de personagem/classe escondem o titulo grande
     * ("MITERA ONLINE") - no mundo virtual reduzido (960x540, ver
     * MUNDO_VIRTUAL_*) ele sozinho ja' consumia espaco vertical suficiente
     * pra empurrar o rodape pra fora da tela (paineis de 320 + rodape de 110
     * nao cabiam mais sobrando so' ~110 pro cabecalho com o titulo de 100px -
     * achado pelo usuario testando tanto no mobile quanto no PC). As outras
     * telas (menu/login/etc) tem conteudo mais baixo e cabem com o titulo
     * normal. "Beta version" some junto (a pedido do usuario).
     */
    private void ocultarTitulo() {
        montarTituloBox(false);
    }

    /** BackBtn (sempre o 1o argumento) fica ancorado na borda esquerda com
     * uma margem pequena - igual ao BackBtn real de main_menu.tscn
     * (size_flags_horizontal=8, nao-expansivel, 1o filho do HBoxContainer).
     * Botoes "do meio" (ex: Delete) colam a direita dele com um gap fixo
     * pequeno; o ULTIMO botao (acao primaria - Play/Login/Create/etc) e'
     * empurrado pra borda direita por um espacador elastico, igual o OkayBtn
     * real (size_flags_horizontal=10, EXPAND+SHRINK_END - "gruda" na ponta
     * direita do espaco sobrando em vez de se espalhar por ele). */
    // 220x84 no mobile (bom la, a pedido do usuario); reduzido no PC (ficava
    // grande demais - mesmo ajuste ja feito no menu principal/WorldScreen).
    private final float LARGURA_BOTAO_RODAPE = mobile ? 220f : 170f;
    private final float ALTURA_BOTAO_RODAPE = mobile ? 84f : 64f;
    private static final float MARGEM_BORDA_RODAPE = 16f;
    private static final float GAP_BOTOES_RODAPE = 10f;

    private void definirRodape(com.badlogic.gdx.scenes.scene2d.Actor... botoes) {
        bottomBar.clear();
        bottomBar.setVisible(botoes.length > 0);
        if (botoes.length == 0) return;
        bottomBar.add(botoes[0]).width(LARGURA_BOTAO_RODAPE).height(ALTURA_BOTAO_RODAPE).padLeft(MARGEM_BORDA_RODAPE);
        for (int i = 1; i < botoes.length - 1; i++) {
            bottomBar.add(botoes[i]).width(LARGURA_BOTAO_RODAPE).height(ALTURA_BOTAO_RODAPE).padLeft(GAP_BOTOES_RODAPE);
        }
        if (botoes.length > 1) {
            bottomBar.add(new com.badlogic.gdx.scenes.scene2d.Actor()).expandX();
            bottomBar.add(botoes[botoes.length - 1]).width(LARGURA_BOTAO_RODAPE).height(ALTURA_BOTAO_RODAPE).padRight(MARGEM_BORDA_RODAPE);
        }
    }

    private Table criarPainel() {
        Table t = new Table();
        t.setBackground(skin.getDrawable("painel"));
        return t;
    }

    /** Porta de start_screen.gd::update_input_bg - pinta o campo de verde
     * quando o valor digitado e' valido. Cada campo precisa do seu PROPRIO
     * TextFieldStyle (senao trocar o fundo de um mudaria todos os campos que
     * compartilham o style "default" do skin). */
    private Runnable marcarValidacao(TextField campo, java.util.function.BooleanSupplier valido) {
        campo.setStyle(new TextField.TextFieldStyle(campo.getStyle()));
        Runnable atualizar = () -> campo.getStyle().background =
            valido.getAsBoolean() ? skin.getDrawable("campo-valido") : skin.getDrawable("campo-normal");
        campo.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { atualizar.run(); }
        });
        atualizar.run();
        return atualizar;
    }

    /** Registra um segundo campo pra tambem re-checar quando "campo" mudar -
     * usado pro "Confirm Email"/"Confirm Password" reagirem quando o campo
     * original muda (igual start_screen.gd::_on_email_text_changed tambem
     * chama _on_confirm_email_text_changed). */
    private void tambemAtualizarQuandoMudar(TextField campo, Runnable atualizarOutro) {
        campo.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { atualizarOutro.run(); }
        });
    }

    // ---------------------------------------------------------------------
    // Menu principal (Play / Register / Exit) - tela inicial de verdade,
    // ping/check_version so acontece quando aperta Play (igual start_screen.gd)
    // ---------------------------------------------------------------------
    private void telaMenuPrincipal() {
        limparConteudo();
        definirRodape();

        Table painel = criarPainel();
        TextButton playBtn = new TextButton("PLAY", skin, "verde");
        TextButton registerBtn = new TextButton("Register", skin, "laranja");
        TextButton exitBtn = new TextButton("Exit", skin, "vermelho");

        playBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { iniciarFluxo(); }
        });
        registerBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { telaRegistro(); }
        });
        exitBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { Gdx.app.exit(); }
        });

        // 240x72 no mobile (bom la, a pedido do usuario); reduzido no PC
        // (ficava grande demais).
        float largura = mobile ? 240f : 190f;
        float altura = mobile ? 72f : 56f;
        painel.pad(10);
        painel.add(playBtn).width(largura).height(altura).padBottom(5).row();
        painel.add(registerBtn).width(largura).height(altura).padBottom(5).row();
        painel.add(exitBtn).width(largura).height(altura);
        content.add(painel);
    }

    // ---------------------------------------------------------------------
    // Fluxo de conexao: ping -> check_version -> sessao salva? char select : login
    // ---------------------------------------------------------------------
    private void iniciarFluxo() {
        telaCarregando("Connecting...");
        ServerApi.ping(new ServerApi.ApiCallback() {
            @Override public void onResponse(int statusCode, JsonValue body) {
                if (statusCode == 200) {
                    verificarVersao();
                } else {
                    telaErro("Failed to connect to the server.", AuthScreen.this::telaMenuPrincipal);
                }
            }
            @Override public void onFailure(String motivo) {
                telaErro(motivo, AuthScreen.this::telaMenuPrincipal);
            }
        });
    }

    private void verificarVersao() {
        telaCarregando("Checking Version...");
        ServerApi.checkVersion(new ServerApi.ApiCallback() {
            @Override public void onResponse(int statusCode, JsonValue body) {
                if (statusCode == 200) {
                    int uid = Sessao.userIdSalvo();
                    if (uid != -1) {
                        currentUserId = uid;
                        atualizarLogout();
                        fetchCharacters();
                    } else {
                        telaLogin();
                    }
                } else {
                    String erro = body != null && body.has("erro") ? body.getString("erro") : "Client out of date.";
                    telaErro(erro, AuthScreen.this::telaMenuPrincipal);
                }
            }
            @Override public void onFailure(String motivo) {
                telaErro(motivo, AuthScreen.this::telaMenuPrincipal);
            }
        });
    }

    private void atualizarLogout() {
        logoutBtn.setVisible(currentUserId != -1);
    }

    private void telaCarregando(String texto) {
        limparConteudo();
        definirRodape();
        Table painel = criarPainel();
        painel.add(new Label(texto, skin)).pad(20);
        content.add(painel);
    }

    private void telaErro(String texto, Runnable aoOk) {
        limparConteudo();
        Label label = new Label(texto, skin);
        label.setWrap(true);
        label.setAlignment(Align.center);
        TextButton ok = new TextButton("OK", skin);
        ok.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { aoOk.run(); }
        });
        Table painel = criarPainel();
        painel.add(label).width(400).pad(20).row();
        // Altura igual aos outros botoes de painel avulso (Play/Register/Exit
        // em telaMenuPrincipal) - faltava, ficava baixinho/desproporcional
        // perto dos outros.
        painel.add(ok).width(150).height(72).padBottom(20);
        content.add(painel);
        definirRodape();
    }

    // ---------------------------------------------------------------------
    // Login / Registro / Esqueci senha
    // ---------------------------------------------------------------------
    private void telaLogin() {
        limparConteudo();

        TextField email = new TextField("", skin);
        TextField senha = new TextField("", skin);
        senha.setPasswordMode(true);
        senha.setPasswordCharacter('*');
        marcarValidacao(email, () -> EMAIL_REGEX.matcher(email.getText().trim()).matches());
        marcarValidacao(senha, () -> senha.getText().length() >= 5);
        TextButton esqueciBtn = new TextButton("Forgot Password?", skin, "link");
        esqueciBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { forgotStep = 0; telaEsqueciSenha(); }
        });

        Table grid = new Table();
        grid.add(new Label("Email", skin)).left().pad(3);
        grid.add(email).width(300).pad(3).row();
        grid.add(new Label("Password", skin)).left().pad(3);
        grid.add(senha).width(300).pad(3).row();
        grid.add(esqueciBtn).colspan(2).center().padTop(6);

        Table painel = criarPainel();
        painel.add(grid).pad(14);
        content.add(painel);

        TextButton voltarBtn = new TextButton("Back", skin);
        voltarBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { telaMenuPrincipal(); }
        });
        TextButton loginBtn = new TextButton("Login", skin, "verde");
        loginBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                String em = email.getText().trim();
                String pw = senha.getText();
                if (em.isEmpty() || pw.isEmpty() || !EMAIL_REGEX.matcher(em).matches()) {
                    telaErro("Fill all fields with a valid email.", AuthScreen.this::telaLogin);
                    return;
                }
                loginBtn.setDisabled(true);
                ServerApi.login(em, pw, new ServerApi.ApiCallback() {
                    @Override public void onResponse(int statusCode, JsonValue body) {
                        loginBtn.setDisabled(false);
                        if (statusCode == 200 && body != null && body.has("user_id")) {
                            currentUserId = body.getInt("user_id");
                            atualizarLogout();
                            Sessao.salvar(currentUserId, body.getString("email", em), pw);
                            fetchCharacters();
                        } else {
                            telaErro("Invalid email or password.", AuthScreen.this::telaLogin);
                        }
                    }
                    @Override public void onFailure(String motivo) { loginBtn.setDisabled(false); telaErro(motivo, AuthScreen.this::telaLogin); }
                });
            }
        });
        definirRodape(voltarBtn, loginBtn);
    }

    private void telaRegistro() {
        limparConteudo();

        TextField email = new TextField("", skin);
        TextField confirmEmail = new TextField("", skin);
        TextField senha = new TextField("", skin);
        senha.setPasswordMode(true);
        senha.setPasswordCharacter('*');
        TextField confirmSenha = new TextField("", skin);
        confirmSenha.setPasswordMode(true);
        confirmSenha.setPasswordCharacter('*');

        Runnable atualizarConfirmEmail = marcarValidacao(confirmEmail, () ->
            !confirmEmail.getText().isEmpty() && confirmEmail.getText().equals(email.getText())
                && EMAIL_REGEX.matcher(email.getText().trim()).matches());
        marcarValidacao(email, () -> EMAIL_REGEX.matcher(email.getText().trim()).matches());
        tambemAtualizarQuandoMudar(email, atualizarConfirmEmail);

        Runnable atualizarConfirmSenha = marcarValidacao(confirmSenha, () ->
            !confirmSenha.getText().isEmpty() && confirmSenha.getText().equals(senha.getText()) && senha.getText().length() >= 5);
        marcarValidacao(senha, () -> senha.getText().length() >= 5);
        tambemAtualizarQuandoMudar(senha, atualizarConfirmSenha);

        Table grid = new Table();
        grid.add(new Label("Email", skin)).left().pad(3);
        grid.add(email).width(300).pad(3).row();
        grid.add(new Label("Confirm Email", skin)).left().pad(3);
        grid.add(confirmEmail).width(300).pad(3).row();
        grid.add(new Label("Password", skin)).left().pad(3);
        grid.add(senha).width(300).pad(3).row();
        grid.add(new Label("Confirm Password", skin)).left().pad(3);
        grid.add(confirmSenha).width(300).pad(3);

        Table painel = criarPainel();
        painel.add(grid).pad(14);
        content.add(painel);

        TextButton voltarBtn = new TextButton("Back", skin);
        voltarBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { telaMenuPrincipal(); }
        });
        TextButton criarBtn = new TextButton("Create", skin, "verde");
        criarBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                String em = email.getText().trim();
                String cem = confirmEmail.getText().trim();
                String pw = senha.getText();
                String cpw = confirmSenha.getText();
                if (em.isEmpty() || cem.isEmpty() || pw.isEmpty() || cpw.isEmpty()) {
                    telaErro("You must fill in all fields.", AuthScreen.this::telaRegistro); return;
                }
                if (!EMAIL_REGEX.matcher(em).matches()) { telaErro("Invalid email address.", AuthScreen.this::telaRegistro); return; }
                if (!em.equals(cem)) { telaErro("Emails do not match.", AuthScreen.this::telaRegistro); return; }
                if (pw.length() < 5 || !pw.equals(cpw)) { telaErro("Password must match and have at least 5 chars.", AuthScreen.this::telaRegistro); return; }

                criarBtn.setDisabled(true);
                ServerApi.register(em, pw, new ServerApi.ApiCallback() {
                    @Override public void onResponse(int statusCode, JsonValue body) {
                        criarBtn.setDisabled(false);
                        if (statusCode == 200 || statusCode == 201) {
                            if (body != null && body.has("user_id")) {
                                currentUserId = body.getInt("user_id");
                                atualizarLogout();
                                Sessao.salvar(currentUserId, body.getString("email", em), pw);
                            }
                            telaErro("Account created successfully!", AuthScreen.this::fetchCharacters);
                        } else {
                            telaErro("Email already in use or server error.", AuthScreen.this::telaRegistro);
                        }
                    }
                    @Override public void onFailure(String motivo) { criarBtn.setDisabled(false); telaErro(motivo, AuthScreen.this::telaRegistro); }
                });
            }
        });
        definirRodape(voltarBtn, criarBtn);
    }

    private void telaEsqueciSenha() {
        limparConteudo();

        TextField email = new TextField("", skin);
        TextField codigo = new TextField("", skin);
        TextField novaSenha = new TextField("", skin);
        novaSenha.setPasswordMode(true);
        novaSenha.setPasswordCharacter('*');
        marcarValidacao(email, () -> EMAIL_REGEX.matcher(email.getText().trim()).matches());
        marcarValidacao(novaSenha, () -> novaSenha.getText().length() >= 5);

        Table grid = new Table();
        grid.add(new Label("Email", skin)).left().pad(3);
        grid.add(email).width(300).pad(3).row();
        if (forgotStep >= 1) {
            email.setDisabled(true);
            grid.add(new Label("Code", skin)).left().pad(3);
            grid.add(codigo).width(300).pad(3).row();
        }
        if (forgotStep >= 2) {
            grid.add(new Label("New Password", skin)).left().pad(3);
            grid.add(novaSenha).width(300).pad(3);
        }

        Table painel = criarPainel();
        painel.add(grid).pad(14);
        content.add(painel);

        TextButton voltarBtn = new TextButton("Back", skin);
        voltarBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { telaLogin(); }
        });

        TextButton acaoBtn;
        if (forgotStep == 0) {
            acaoBtn = new TextButton("Send Code", skin, "verde");
            acaoBtn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    String em = email.getText().trim();
                    if (!EMAIL_REGEX.matcher(em).matches()) { telaErro("Invalid email address.", AuthScreen.this::telaEsqueciSenha); return; }
                    acaoBtn.setDisabled(true);
                    ServerApi.forgotPassword(em, new ServerApi.ApiCallback() {
                        @Override public void onResponse(int statusCode, JsonValue body) {
                            acaoBtn.setDisabled(false);
                            if (statusCode == 200) { forgotStep = 1; telaEsqueciSenha(); }
                            else if (statusCode == 404) { telaErro("Email not found.", AuthScreen.this::telaEsqueciSenha); }
                            else { telaErro("Error sending reset code.", AuthScreen.this::telaEsqueciSenha); }
                        }
                        @Override public void onFailure(String motivo) { acaoBtn.setDisabled(false); telaErro(motivo, AuthScreen.this::telaEsqueciSenha); }
                    });
                }
            });
        } else if (forgotStep == 1) {
            acaoBtn = new TextButton("Verify Code", skin, "verde");
            acaoBtn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    if (codigo.getText().trim().isEmpty()) { telaErro("Enter the code sent to your email.", AuthScreen.this::telaEsqueciSenha); return; }
                    forgotStep = 2;
                    telaEsqueciSenha();
                }
            });
        } else {
            acaoBtn = new TextButton("Reset Password", skin, "verde");
            acaoBtn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    String np = novaSenha.getText();
                    if (np.length() < 5) { telaErro("Password must have at least 5 chars.", AuthScreen.this::telaEsqueciSenha); return; }
                    acaoBtn.setDisabled(true);
                    ServerApi.resetPassword(email.getText().trim(), codigo.getText().trim(), np, new ServerApi.ApiCallback() {
                        @Override public void onResponse(int statusCode, JsonValue body) {
                            acaoBtn.setDisabled(false);
                            if (statusCode == 200) {
                                telaErro("Password updated successfully! You can now login.", AuthScreen.this::telaLogin);
                            } else {
                                String erro = body != null && body.has("erro") ? body.getString("erro") : "Wrong code! Try again.";
                                telaErro(erro, AuthScreen.this::telaEsqueciSenha);
                            }
                        }
                        @Override public void onFailure(String motivo) { acaoBtn.setDisabled(false); telaErro(motivo, AuthScreen.this::telaEsqueciSenha); }
                    });
                }
            });
        }
        definirRodape(voltarBtn, acaoBtn);
    }

    // ---------------------------------------------------------------------
    // Selecao de personagem (carrossel de MAX_SLOTS, 2 paineis lado a lado)
    // ---------------------------------------------------------------------
    private void fetchCharacters() {
        telaCarregando("Loading characters...");
        ServerApi.getCharacters(currentUserId, new ServerApi.ApiCallback() {
            @Override public void onResponse(int statusCode, JsonValue body) {
                if (statusCode == 200) {
                    for (int i = 0; i < MAX_SLOTS; i++) slots[i] = null;
                    JsonValue lista = body;
                    if (body != null && body.isObject() && body.has("characters")) lista = body.get("characters");
                    if (lista != null && lista.isArray()) {
                        int i = 0;
                        for (JsonValue c : lista) {
                            if (i >= MAX_SLOTS) break;
                            slots[i] = CharacterData.deParsed(c);
                            i++;
                        }
                    }
                    currentSlotIndex = Math.max(0, Math.min(currentSlotIndex, MAX_SLOTS - 1));
                    telaSelecaoPersonagem();
                } else {
                    telaErro("Failed to load characters.", AuthScreen.this::telaMenuPrincipal);
                }
            }
            @Override public void onFailure(String motivo) {
                telaErro(motivo, AuthScreen.this::telaMenuPrincipal);
            }
        });
    }

    private String formatarTempo(int segundosTotais) {
        int h = segundosTotais / 3600;
        int m = (segundosTotais % 3600) / 60;
        int s = segundosTotais % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private String formatarNumero(long valor) {
        String texto = Long.toString(valor);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = texto.length() - 1; i >= 0; i--) {
            sb.insert(0, texto.charAt(i));
            count++;
            if (count % 3 == 0 && i != 0) sb.insert(0, '.');
        }
        return sb.toString();
    }

    private void telaSelecaoPersonagem() {
        limparConteudo();
        ocultarTitulo();
        deleteConfirmStage = 0;

        CharacterData atual = slots[currentSlotIndex];

        // 1f no mobile (bom la, a pedido do usuario); reduzido no PC (ficava
        // grande demais - mesma escala aplicada em toda a "linha" abaixo,
        // paineis/setas).
        float escalaSlots = mobile ? 1f : 0.82f;
        // Nome/Lv. (painel esquerdo) - teto de fontScale BEM menor que
        // escalaSlots nas 2 plataformas: no mobile escalaSlots=1 (tamanho
        // cru) vazava do painel com nomes/niveis mais longos; no PC o
        // usuario tambem pediu uma fonte menor aqui especificamente (achava
        // grande mesmo ja reduzido por escalaSlots).
        float escalaNomeNivel = 0.55f;
        // Indicador "x/4" (canto do painel direito) - idem, grande demais
        // nas 2 plataformas com o escalaSlots cru.
        float escalaPagina = 0.6f;
        // Icone de classe + os 4 icones de stat (painel direito) - tamanho
        // em pixel DIRETO (nao mais multiplicador de escalaSlots) pra poder
        // calibrar cada plataforma independente: mobile pediu "um pouco
        // maior" depois de uma rodada anterior ter cortado demais; PC pediu
        // "aumente um pouquinho" tambem (estava pequeno igual o mobile sem
        // motivo, as queixas eram sobre o TEXTO, nao o icone).
        float tamanhoIconeDireita = mobile ? 30f : 24f;
        // Teto de fontScale do texto da direita (classe/skill/defesa/tempo/
        // xp) - usuario reportou com print de verdade (PC e mobile) que
        // 0.85/0.78 ainda ficava enorme ("Musicality" estourando o painel e
        // encostando no icone ao lado) e pediu pra diminuir MUITO; cortado
        // bem mais forte aqui (base da fonte "info" e' 32px cru, entao isso
        // da' ~13-14px renderizado nas 2 plataformas).
        float escalaTextoDireita = mobile ? 0.45f : 0.4f;

        ImageButton esquerda = new ImageButton(new TextureRegionDrawable(texturas.get("seta-esq")));
        ImageButton direita = new ImageButton(new TextureRegionDrawable(texturas.get("seta-dir")));
        // Mesmo problema do botao de logout: ImageButton nao estica a imagem
        // pro tamanho da celula sozinho, ficava minuscula (17x32 cru).
        esquerda.getImageCell().size(56 * escalaSlots, 105 * escalaSlots);
        direita.getImageCell().size(56 * escalaSlots, 105 * escalaSlots);
        esquerda.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                currentSlotIndex = (currentSlotIndex - 1 + MAX_SLOTS) % MAX_SLOTS;
                telaSelecaoPersonagem();
            }
        });
        direita.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                currentSlotIndex = (currentSlotIndex + 1) % MAX_SLOTS;
                telaSelecaoPersonagem();
            }
        });

        Table painelEsquerdo = criarPainel();
        Table painelDireito = criarPainel();
        // Margem interna uniforme - sem isso o texto/icone da primeira coluna
        // ficava colado na borda esquerda do painel (achado comparando com o
        // print do jogo real, que sempre respira um pouco das bordas).
        painelDireito.pad(16);
        // Por padrao "linha" (no fim do metodo) desenha o painelDireito direto;
        // so' quando ha personagem (ver abaixo) ele e' trocado por uma versao
        // com o indicador "x/4" sobreposto via Stack, pra poder ficar quase
        // colado no canto sem herdar o pad(16) uniforme do painel.
        com.badlogic.gdx.scenes.scene2d.Actor painelDireitoFinal = painelDireito;
        TextButton voltarBtn = new TextButton("Back", skin);
        voltarBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { telaMenuPrincipal(); }
        });

        if (atual == null) {
            // "Empty Slot" usava escalaSlots (1f mobile / 0.82f PC) como
            // fontScale - esse numero e' a escala do PAINEL/layout (seta,
            // altura das celulas etc.), nao foi calibrado pra texto, e
            // ficava enorme comparado com o resto dos labels dessa mesma
            // tela (nome/nivel usam escalaNomeNivel=0.55). Reaproveitando
            // encaixarTexto() com a mesma largura usada pelo nome/nivel pra
            // ficar consistente e nunca vazar do painel.
            Label vazioLabel = new Label("Empty Slot", skin);
            encaixarTexto(vazioLabel, 194 * escalaSlots - 28, escalaNomeNivel);
            painelEsquerdo.add(vazioLabel).pad(30 * escalaSlots);
            TextButton criarBtn = new TextButton("Create", skin, "verde");
            criarBtn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    selectedClassKey = "";
                    telaSelecaoClasse();
                }
            });
            Label slotVazioLabel = new Label((currentSlotIndex + 1) + "/" + MAX_SLOTS, skin);
            slotVazioLabel.setFontScale(escalaPagina);
            painelDireito.add(slotVazioLabel).pad(20 * escalaSlots);
            definirRodape(voltarBtn, criarBtn);
        } else {
            ClasseInfo classe = CLASSES.getOrDefault(atual.className, CLASSES.get("Knight"));
            Label nome = new Label(atual.name, skin, "subtitulo");
            nome.setColor(classe.cor);
            // Preview do personagem com a pele de esqueleto, sem roupa.
            Image previewBase = new Image(new TextureRegionDrawable(quadroFrenteCorpo("corpo-base")));
            previewBase.setScaling(Scaling.fit);
            // Metade do tamanho (a pedido do usuario) - a CELULA continua
            // dando grow() (reserva o mesmo espaco/layout de antes), so' o
            // ator e' desenhado a 50% escalado a partir do proprio centro,
            // entao ele encolhe mas continua centralizado no meio do espaco
            // reservado. Origin recalculado em layout() porque na hora do
            // construtor o Stack ainda tem width/height=0 (a Table so' calcula
            // o tamanho de verdade depois) - setOrigin(Align.center) direto
            // aqui ficaria preso em 0,0 e encolheria pro canto errado.
            Stack previewPersonagem = new Stack(previewBase) {
                @Override
                public void layout() {
                    super.layout();
                    setOrigin(getWidth() / 2f, getHeight() / 2f);
                }
            };
            previewPersonagem.setTransform(true);
            // Era 0.5 (metade do tamanho, a pedido do usuario na epoca) -
            // aumentado um pouco pra 0.65 (mesmo pedido, ajuste fino depois
            // de ver em tela).
            previewPersonagem.setScale(0.65f);

            // Margem uniforme no proprio painel (nao mais pad individual por
            // linha) - o preview cresce (grow) pra ocupar todo o espaco que
            // sobra entre nome/nivel, em vez de ficar pequeno no meio de um
            // quadrado quase vazio.
            painelEsquerdo.pad(14);
            nome.setAlignment(Align.center);
            // 194/290 (ver "linha" no fim do metodo) sao os mesmos numeros
            // usados la pra calcular o espaco de verdade disponivel pro
            // texto, descontando pad(14)/pad(16) do painel e (pros labels da
            // direita) o icone+padRight que vem antes na mesma linha.
            float larguraEsquerda = 194 * escalaSlots - 28;
            float larguraDireitaComIcone = 290 * escalaSlots - 32 - tamanhoIconeDireita - 8;
            encaixarTexto(nome, larguraEsquerda, escalaNomeNivel);
            painelEsquerdo.add(nome).growX().center().row();
            painelEsquerdo.add(previewPersonagem).grow().pad(6).row();
            Label nivel = new Label("Lv. " + atual.level, skin, "nivel");
            nivel.setColor(new Color(1f, 0.85f, 0.2f, 1f));
            nivel.setAlignment(Align.center);
            encaixarTexto(nivel, larguraEsquerda, escalaNomeNivel);
            painelEsquerdo.add(nivel).growX().center();

            Table topoDireito = new Table();
            Image classeIcone = new Image(new TextureRegionDrawable(texturas.get("classe-" + atual.className)));
            classeIcone.setScaling(Scaling.fit);
            Label classeLabel = new Label(atual.className, skin, "info");
            classeLabel.setColor(classe.cor);
            encaixarTexto(classeLabel, larguraDireitaComIcone, escalaTextoDireita);
            // padTop so' na classeLabel (mesma fonte/tamanho da medicao que
            // motivou o ajuste - "info"/dejavu-sans.condensed 32px). O label
            // do slot usa a fonte "default" (dejavu-sans 20px), com metrica
            // de descendente diferente - aplicar o mesmo padTop nela so'
            // empurrava pra baixo sem motivo, tirando ela do canto superior
            // direito.
            topoDireito.add(classeIcone).size(tamanhoIconeDireita).padRight(8);
            topoDireito.add(classeLabel).left().expandX().padTop(mobile ? 8 : 3);

            // Magic e' excecao (icone de arma, ver comentario no construtor);
            // as outras 3 tem icone de skill de verdade.
            TextureRegion skillTex = classe.primeiraSkill.equals("Magic")
                ? texturas.get("arma-" + atual.className)
                : texturas.get("skill-" + classe.primeiraSkill);
            int skillValor = atual.skill(chaveSkillDoNome(classe.primeiraSkill), 10);
            Label skillLabel = new Label(skillValor + " " + classe.primeiraSkill, skin, "info");
            skillLabel.setColor(CORES_SKILL.getOrDefault(classe.primeiraSkill, classe.cor));
            Label defesaLabel = new Label(atual.skill("defense", 10) + " Defence", skin, "info");
            defesaLabel.setColor(new Color(0.4f, 0.7f, 1f, 1f));

            painelDireito.add(topoDireito).growX().colspan(2).padBottom(mobile ? 10 : 4).row();
            adicionarLinhaStat(painelDireito, skillTex, skillLabel, escalaTextoDireita, larguraDireitaComIcone, tamanhoIconeDireita);
            adicionarLinhaStat(painelDireito, texturas.get("defesa"), defesaLabel, escalaTextoDireita, larguraDireitaComIcone, tamanhoIconeDireita);
            adicionarLinhaStat(painelDireito, texturas.get("tempo"), new Label(formatarTempo(atual.timePlayed), skin, "info"), escalaTextoDireita, larguraDireitaComIcone, tamanhoIconeDireita);
            Label xpLabel = new Label(formatarNumero(atual.exp), skin, "info");
            xpLabel.setColor(new Color(1f, 0.7f, 0.15f, 1f));
            adicionarLinhaStat(painelDireito, texturas.get("xp"), xpLabel, escalaTextoDireita, larguraDireitaComIcone, tamanhoIconeDireita);

            TextButton playBtn = new TextButton("Play", skin, "verde");
            playBtn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    playListener.onPlay(currentUserId, atual);
                }
            });
            TextButton deleteBtn = new TextButton("Delete", skin, "vermelho");
            deleteBtn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    if (deleteConfirmStage == 0) {
                        deleteConfirmStage = 1;
                        // TextButton nao le style.font ao vivo pro label - ele
                        // copia o font pra UM Label.LabelStyle PROPRIO na hora
                        // da construcao (ja isolado do skin, nao precisa clonar
                        // nada aqui) e so' re-sincroniza dentro de setStyle().
                        // Mutar deleteBtn.getStyle().font sozinho (tentativa
                        // anterior) nao mudava nada na tela - o texto continuava
                        // saindo em martel, que nao tem os glifos de numero/"%"
                        // direito e engolia o "100% " de "100% sure?", sobrando
                        // so' "sure". Label.setStyle() precisa ser chamado de
                        // novo depois de mudar o campo - e' ele quem reconstroi
                        // o BitmapFontCache interno a partir de style.font;
                        // mutar o campo sozinho nao refaz esse cache (mesmo
                        // motivo do bug acima, um nivel abaixo).
                        Label.LabelStyle estiloLabel = deleteBtn.getLabel().getStyle();
                        estiloLabel.font = skin.getFont("versao-font");
                        deleteBtn.getLabel().setStyle(estiloLabel);
                        deleteBtn.setText("Are you sure?");
                    } else if (deleteConfirmStage == 1) { deleteConfirmStage = 2; deleteBtn.setText("100% sure?"); }
                    else { telaConfirmarDeleteComSenha(atual); }
                }
            });
            definirRodape(voltarBtn, deleteBtn, playBtn);

            // Indicador "x/4": precisa ficar quase colado no canto superior
            // direito do painel (so' 4px de margem), mas o painel inteiro tem
            // pad(16) uniforme (linha 718) - herdar esse pad deixava um vao
            // grande demais. Em vez de mexer no pad geral (usado por todo o
            // resto do conteudo), sobrepoe o label por CIMA do painel com um
            // Stack + Table propria, ancorada no canto com sua propria margem
            // independente do pad interno do painel.
            Label slotLabel = new Label((currentSlotIndex + 1) + "/" + MAX_SLOTS, skin);
            slotLabel.setFontScale(escalaPagina);
            Table overlaySlot = new Table();
            overlaySlot.setFillParent(true);
            overlaySlot.top().right();
            overlaySlot.add(slotLabel).pad(4);
            Stack painelDireitoComIndicador = new Stack(painelDireito, overlaySlot);
            painelDireitoFinal = painelDireitoComIndicador;
        }

        // Reduzido no PC (escalaSlots, ver topo do metodo) - o container
        // inteiro (setas + paineis) estava grande demais la, a pedido do
        // usuario. "content.top()" sozinho (tentativa anterior) nunca
        // funcionou de verdade pra subir o bloco - "content" sem grow()/
        // fill() sempre fica do tamanho exato do proprio conteudo (zero
        // sobra interna pra top()/center() reposicionar); quem de fato
        // decide a posicao vertical do bloco inteiro e' o alinhamento da
        // CELULA do content dentro do root (contentCell, ver construtor) -
        // e' isso que estava deixando o bloco colado perto da barra de
        // baixo (ficava centralizado verticalmente no espaco entre
        // cabecalho/rodape, que e' pequeno o bastante pra parecer "quase
        // colado" nela).
        Table linha = new Table();
        linha.add(esquerda).size(48 * escalaSlots, 114 * escalaSlots).padRight(18 * escalaSlots);
        linha.add(painelEsquerdo).width(194 * escalaSlots).height(282 * escalaSlots);
        linha.add(painelDireitoFinal).width(290 * escalaSlots).height(282 * escalaSlots).padLeft(2 * escalaSlots);
        linha.add(direita).size(48 * escalaSlots, 114 * escalaSlots).padLeft(18 * escalaSlots);
        content.add(linha);
        contentCell.align(Align.top).padTop(40);
    }

    /** SKILL_DISPLAY do start_screen.gd: label exibido -> chave no dict "skills" do servidor. */
    private String chaveSkillDoNome(String nomeExibicao) {
        switch (nomeExibicao) {
            case "Magic": return "magic";
            case "Melee": return "melee";
            case "Focus": return "distance";
            case "Musicality": return "musicality";
            default: return nomeExibicao.toLowerCase();
        }
    }

    /** Recorta o quadro "de frente parado" (indice 3) de um spritesheet de
     * corpo/roupa - mesmo layout usado no mundo (WorldScreen). */
    private TextureRegion quadroFrenteCorpo(String chaveTextura) {
        TextureRegion tex = texturas.get(chaveTextura);
        return new TextureRegion(tex, FRAME_INDICE_FRENTE * FRAME_LARGURA, 0, FRAME_LARGURA, tex.getRegionHeight());
    }

    private void adicionarLinhaStat(Table painel, TextureRegion icone, Label label, float escalaTexto, float larguraMaximaLabel, float tamanhoIcone) {
        Image img = new Image(new TextureRegionDrawable(icone));
        img.setScaling(Scaling.fit);
        encaixarTexto(label, larguraMaximaLabel, escalaTexto);
        painel.add(img).size(tamanhoIcone).padRight(8).left();
        // padBottom entre linhas - sem isso as stats ficavam grudadas umas
        // nas outras, ocupando so' o topo do painel em vez de se espalhar
        // (achado comparando com o print do jogo real). padTop compensa o
        // espaco de descendente do BitmapFont, que deixa texto sem "y/g/q"
        // centralizado uns pixels acima do centro visual do icone ao lado.
        // Reduzido no PC (!mobile) - o usuario achou cada linha "muito
        // afastada" uma da outra depois do texto/icone terem encolhido.
        float padV = mobile ? 8f : 3f;
        painel.add(label).left().expandX().minHeight(32).padTop(padV).padBottom(mobile ? 10f : 4f).row();
    }

    /** Encolhe o fontScale do label (a partir de escalaMaxima, nunca alem
     * dela) o minimo necessario pra caber em larguraMaxima - reportado 3x
     * (slots, chat +/-, popup de idioma) um numero fixo de escala/tamanho
     * SEMPRE vazava pra algum nome/valor mais longo em alguma combinacao de
     * plataforma/conteudo; medindo o texto de verdade (GlyphLayout) e
     * ajustando sozinho, qualquer nome/numero cabe daqui pra frente, sem
     * precisar adivinhar outra constante. Nunca estica alem de escalaMaxima
     * (so encolhe) pra nao desrespeitar o tamanho/proporcao ja calibrado
     * pra cada tela/plataforma. */
    private void encaixarTexto(Label label, float larguraMaxima, float escalaMaxima) {
        medidaTexto.setText(label.getStyle().font, label.getText().toString());
        float escala = escalaMaxima;
        while (medidaTexto.width * escala > larguraMaxima && escala > 0.4f) {
            escala -= 0.05f;
        }
        label.setFontScale(escala);
    }

    private void telaConfirmarDeleteComSenha(CharacterData personagem) {
        limparConteudo();

        TextField senha = new TextField("", skin);
        senha.setPasswordMode(true);
        senha.setPasswordCharacter('*');

        Table grid = new Table();
        grid.add(new Label("Password", skin)).left().pad(3);
        grid.add(senha).width(300).pad(3);

        Table painel = criarPainel();
        painel.add(grid).pad(14);
        content.add(painel);

        TextButton cancelar = new TextButton("Cancel", skin);
        cancelar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { telaSelecaoPersonagem(); }
        });
        TextButton confirmar = new TextButton("Delete", skin, "vermelho");
        confirmar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                String pw = senha.getText();
                if (pw.isEmpty()) { telaErro("Please enter your password.", () -> telaConfirmarDeleteComSenha(personagem)); return; }
                confirmar.setDisabled(true);
                ServerApi.deleteCharacter(currentUserId, personagem.name, pw, new ServerApi.ApiCallback() {
                    @Override public void onResponse(int statusCode, JsonValue body) {
                        confirmar.setDisabled(false);
                        if (statusCode == 200) {
                            fetchCharacters();
                        } else {
                            String erro = body != null && body.has("erro") ? body.getString("erro") : "Wrong password.";
                            telaErro(erro, () -> telaConfirmarDeleteComSenha(personagem));
                        }
                    }
                    @Override public void onFailure(String motivo) { confirmar.setDisabled(false); telaErro(motivo, () -> telaConfirmarDeleteComSenha(personagem)); }
                });
            }
        });
        definirRodape(cancelar, confirmar);
    }

    // ---------------------------------------------------------------------
    // Selecao de classe (grid 2x2) + nome do personagem
    // ---------------------------------------------------------------------
    private void telaSelecaoClasse() {
        limparConteudo();
        ocultarTitulo();

        Label descLabel = new Label("", skin);
        descLabel.setWrap(true);
        Label tituloLabel = new Label("", skin, "subtitulo");

        Table grade = new Table();
        java.util.List<Button> botoesClasse = new java.util.ArrayList<>();
        // Ordem da grade (Mage/Bard/Ranger/Knight) e' a ordem real dos nodes
        // MageSlot/BardSlot/RangerSlot/KnightSlot em main_menu.tscn - NAO e'
        // a mesma ordem alfabetica/de CLASSES, descoberto comparando print
        // antigo x novo (Bard estava aparecendo onde deveria ser Knight).
        String[] ordemGrade = {"Mage", "Bard", "Ranger", "Knight"};
        for (int i = 0; i < ordemGrade.length; i++) {
            String key = ordemGrade[i];
            // Grade de selecao usa o icone "grande" (Mage_Icon.png etc), NAO
            // o icone pequeno (MageIcon.png) do painel de stats do char-select.
            Image icone = new Image(new TextureRegionDrawable(texturas.get("classe-grande-" + key)));
            icone.setScaling(Scaling.fit);
            Button.ButtonStyle estiloBotao = new Button.ButtonStyle();
            estiloBotao.up = UiSkin.retangulo(UiSkin.COR_PAINEL, UiSkin.COR_BORDA_PAINEL, 1);
            Button b = new Button(estiloBotao);
            b.add(icone).size(96).pad(2);
            b.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    selectedClassKey = key;
                    ClasseInfo info = CLASSES.get(key);
                    tituloLabel.setText(info.titulo);
                    tituloLabel.setColor(info.cor);
                    descLabel.setText(info.desc);
                    for (Button outro : botoesClasse) {
                        String k = (String) outro.getUserObject();
                        outro.getStyle().up = UiSkin.retangulo(UiSkin.COR_PAINEL,
                            k.equals(key) ? info.cor : UiSkin.COR_BORDA_PAINEL, k.equals(key) ? 3 : 1);
                    }
                }
            });
            b.setUserObject(key);
            botoesClasse.add(b);
            grade.add(b).size(100).pad(6);
            if (i == 1) grade.row();
        }

        Table direita = criarPainel();
        direita.add(tituloLabel).padTop(16).row();
        direita.add(descLabel).width(280).pad(16);

        Table esquerda = criarPainel();
        esquerda.add(grade).pad(10);

        Table linha = new Table();
        linha.add(esquerda).padRight(4);
        linha.add(direita).height(esquerda.getPrefHeight());
        content.add(linha);

        TextButton voltar = new TextButton("Back", skin);
        voltar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { telaSelecaoPersonagem(); }
        });
        TextButton proximo = new TextButton("Create", skin, "verde");
        proximo.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (selectedClassKey.isEmpty()) return;
                telaNomePersonagem();
            }
        });
        definirRodape(voltar, proximo);

        // seleciona Mage por padrao, igual o Godot faz ao entrar nessa tela
        botoesClasse.get(0).fire(new ChangeListener.ChangeEvent());
    }

    private void telaNomePersonagem() {
        limparConteudo();

        TextField nome = new TextField("", skin);
        nome.setMaxLength(12);
        nome.setTextFieldFilter((textField, c) -> Character.isLetter(c) || c == '_');
        Label erro = new Label("", skin);

        Table grid = new Table();
        grid.add(new Label("Character Name", skin)).pad(6).row();
        grid.add(nome).width(300).pad(6).row();
        grid.add(erro).padTop(6);

        Table painel = criarPainel();
        painel.add(grid).pad(20);
        content.add(painel);

        TextButton voltar = new TextButton("Back", skin);
        voltar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { telaSelecaoClasse(); }
        });
        TextButton criar = new TextButton("Create", skin, "verde");
        criar.setDisabled(true);
        nome.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                String msg = validarNome(nome.getText());
                erro.setText(msg == null ? "" : msg);
                criar.setDisabled(msg != null);
            }
        });
        criar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                String charName = nome.getText().trim();
                if (validarNome(charName) != null) return;
                criar.setDisabled(true);
                ServerApi.createCharacter(currentUserId, charName, selectedClassKey, new ServerApi.ApiCallback() {
                    @Override public void onResponse(int statusCode, JsonValue body) {
                        criar.setDisabled(false);
                        if (statusCode == 201) {
                            fetchCharacters();
                        } else {
                            String msg = body != null && body.has("erro") ? body.getString("erro") : "Error creating character.";
                            erro.setText(msg);
                        }
                    }
                    @Override public void onFailure(String motivo) { criar.setDisabled(false); erro.setText(motivo); }
                });
            }
        });
        definirRodape(voltar, criar);
    }

    private String validarNome(String texto) {
        if (texto.length() < 3 || texto.length() > 12) {
            return "Name must be between 3 and 12 characters.";
        }
        String lower = texto.toLowerCase();
        for (String banido : BANNED_NAMES) {
            if (lower.contains(banido)) return "Name contains inappropriate words.";
        }
        return null;
    }
}
