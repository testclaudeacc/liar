package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.teste.game.ChatUI;
import com.teste.game.InvertedRealmsGame;
import com.teste.game.entidades.Jogador;
import com.teste.game.mapa.ColisaoGrid;
import com.teste.game.mapa.ConversorCoordenadas;
import com.teste.game.mapa.MapaIluminacao;
import com.teste.game.mapa.MapaMundo;
import com.teste.game.mapa.MapaPropriedades;
import com.teste.game.rede.GameSocket;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Mundo de verdade: mapa + jogador local com movimento em grade de SQM
 * (colisao real, ver ColisaoGrid) + outros jogadores sincronizados via
 * GameSocket (current_players/player_joined/player_left/"m" - ver
 * network_manager.gd). Cada jogador usa a spritesheet BaseSoul como pele
 * padrão, sem roupa.
 */
public class WorldScreen extends ScreenAdapter {

    // Spritesheet: cima 0-2, baixo 3-5, esquerda 6-7, direita 8-9, morte 10.
    private static final int FRAME_LARGURA = 16;
    private static final int FRAME_IDLE_UP = 0;
    private static final int FRAME_IDLE_DOWN = 3;
    private static final int FRAME_IDLE_ESQUERDA = 6;
    private static final int FRAME_IDLE_DIREITA = 8;
    private static final int[] FRAME_ANDAR_CIMA = {1, 2};
    private static final int[] FRAME_ANDAR_BAIXO = {4, 5};
    private static final int[] FRAME_ANDAR_ESQUERDA = {7, 6};
    private static final int[] FRAME_ANDAR_DIREITA = {9, 8};
    // World.tmx novo usa tile visual de 16px de verdade (sem o truque Ambient32
    // 2x do mapa antigo) - personagem desenhado 1:1 com o frame (16px), mesmo
    // continuando a se mover em passos de 32px (TILE do servidor, fixo).
    private static final float ESCALA_SPRITE = 1f;

    /** Quadros parado e andando nas quatro direcoes da spritesheet. */
    private static class AnimacaoCorpo {
        TextureRegion idleCima, idleBaixo, idleDireita, idleEsquerda;
        TextureRegion[] andarCima, andarBaixo, andarDireita, andarEsquerda;
    }

    // SpawnPoint (jogo/world.tscn, Marker2D "SpawnPoint") - posicao CRUA (antes
    // de converter/snapar), usada quando o servidor ainda nao tem uma posicao
    // salva pro personagem (pos_x/pos_y == -1, personagem novo).
    private static final float SPAWN_RAW_X = 304f;
    private static final float SPAWN_RAW_Y = 176f;

    private final MapaMundo mapa;
    private final ConversorCoordenadas conversor;
    private final ColisaoGrid colisao;
    private final MapaIluminacao iluminacao;
    private final GameSocket socket;
    private MapaPropriedades.Luz luzJogador;
    private final List<MapaPropriedades.Luz> todasAsLuzes = new ArrayList<>();

    private final float spawnX, spawnY;
    private final Jogador local;
    private final Map<String, Jogador> remotos = new HashMap<>();
    private final Map<String, NPCVisual> npcs = new LinkedHashMap<>();

    /** Mob do servidor (IA/HP/morte/respawn rodam la; aqui so' desenha) -
     * porta do visual de mob.gd. Criado a partir da camada MobSpawns do Tiled
     * (ver MapaPropriedades). */
    private static class MobVisual {
        final String id;
        final String nome;
        final AnimacaoCorpo animacao;
        final TextureRegion quadroMorto;
        float x, y;
        String direcao = "down";
        float hp = 1f, maxHp = 1f;
        boolean morto = false;
        boolean visivel = false;      // so' aparece depois da 1a posicao do servidor
        boolean voltando = false;     // voltando pra casa (flag do servidor)
        float tempoCorpo = 0f;        // segundos restantes do cadaver no chao
        float tempoTargetHit = 0f;    // quadradinho de "atacou" (mob.gd::_exibir_target_hit)

        // Passo em andamento + fila dos proximos (mob.gd::_fila_passos): cada
        // mob_pos vira um passo animado com a duracao que o SERVIDOR usou, um
        // atras do outro, em vez de teleportar pro destino.
        boolean movendo = false;
        float origemX, origemY, destinoX, destinoY, duracao = 1f, progresso = 1f;
        String direcaoPendente = null;
        // Depois de um passo, espera um tiquinho antes do quadro parado - o
        // proximo passo do servidor costuma chegar logo e o mob nao "pisca"
        // parado entre um SQM e outro (mob.gd: timer de 0.12s).
        float tempoDesdeParou = 1f;
        final java.util.ArrayDeque<Object[]> fila = new java.util.ArrayDeque<>(); // {x, y, dir, dur}

        MobVisual(String id, String nome, float x, float y, AnimacaoCorpo animacao, TextureRegion quadroMorto) {
            this.id = id;
            this.nome = nome;
            this.x = x;
            this.y = y;
            this.animacao = animacao;
            this.quadroMorto = quadroMorto;
        }

        float destinoFinalX() { return !fila.isEmpty() ? (Float) fila.peekLast()[0] : movendo ? destinoX : x; }
        float destinoFinalY() { return !fila.isEmpty() ? (Float) fila.peekLast()[1] : movendo ? destinoY : y; }

        /** Posicao absoluta (sync, morte, respawn): para tudo e vai direto. */
        void posicionar(float nx, float ny, String dir) {
            fila.clear();
            movendo = false;
            tempoDesdeParou = 1f;
            direcaoPendente = null;
            x = nx;
            y = ny;
            direcao = dir;
            progresso = 1f;
        }

        /** Um passo (ou so' virada/flag) decidido pelo servidor. */
        void receberPasso(float nx, float ny, String dir, float dur) {
            float ux = destinoFinalX(), uy = destinoFinalY();
            if (nx == ux && ny == uy) {
                // So' virou (ex: encarando o alvo pra atacar).
                if (!movendo && fila.isEmpty()) direcao = dir; else direcaoPendente = dir;
                return;
            }
            // Pulou passos (entrou na area agora / perdeu pacote): vai direto.
            if (Math.abs(nx - ux) + Math.abs(ny - uy) > Jogador.TILE * 1.5f || fila.size() >= 4) {
                posicionar(nx, ny, dir);
                return;
            }
            fila.add(new Object[]{nx, ny, dir, dur});
            direcaoPendente = null;
            if (!movendo) proximoPasso();
        }

        private void proximoPasso() {
            Object[] passo = fila.poll();
            if (passo == null) {
                if (movendo) tempoDesdeParou = 0f;
                movendo = false;
                if (direcaoPendente != null) {
                    direcao = direcaoPendente;
                    direcaoPendente = null;
                }
                return;
            }
            origemX = x;
            origemY = y;
            destinoX = (Float) passo[0];
            destinoY = (Float) passo[1];
            direcao = (String) passo[2];
            float dur = (Float) passo[3];
            duracao = dur > 0f ? dur : 1f;
            // Atrasado em relacao ao servidor: anda um pouco mais rapido.
            if (!fila.isEmpty()) duracao *= 0.75f;
            progresso = 0f;
            movendo = true;
        }

        boolean andandoVisual() { return movendo || tempoDesdeParou < 0.12f; }

        void atualizar(float delta) {
            if (morto && tempoCorpo > 0f) tempoCorpo -= delta;
            if (tempoTargetHit > 0f) tempoTargetHit -= delta;
            if (!movendo) tempoDesdeParou += delta;
            while (movendo && delta > 0f) {
                float restante = (1f - progresso) * duracao;
                if (delta < restante) {
                    progresso += delta / duracao;
                    x = origemX + (destinoX - origemX) * progresso;
                    y = origemY + (destinoY - origemY) * progresso;
                    return;
                }
                delta -= restante;
                x = destinoX;
                y = destinoY;
                progresso = 1f;
                proximoPasso();
            }
        }
    }

    /** Numero de dano subindo em cima de um mob (mob.gd::exibir_numero_dano). */
    private static class NumeroDano {
        final float x, y;
        final String texto;
        final boolean critico;
        float tempo = 0f;
        NumeroDano(float x, float y, String texto, boolean critico) {
            this.x = x; this.y = y; this.texto = texto; this.critico = critico;
        }
    }
    private final List<NumeroDano> numerosDano = new ArrayList<>();

    // ---- Combate (alvo, ataque automatico, efeitos, loot, morte) ----
    // Mob mirado (clique nele; clique de novo ou ESC tira). Ataca sozinho a
    // cada ~1s enquanto estiver no alcance (o servidor so' aceita 1 hit/s).
    private String alvoMob = null;
    private float esperaAtaque = 0f;
    private static final float INTERVALO_ATAQUE = 1.05f;
    private static final int ALCANCE_RANGED_SQM = 6; // igual servidor.py::ALCANCE_RANGED_SQM

    /** Animacao de efeito (tira de quadros de 16px) tocando uma vez num ponto. */
    private static class Efeito {
        final TextureRegion[] quadros;
        final float x, y;
        float tempo = 0f;
        Efeito(TextureRegion[] quadros, float x, float y) { this.quadros = quadros; this.x = x; this.y = y; }
    }
    private static final float DURACAO_QUADRO_EFEITO = 0.05f;
    private final List<Efeito> efeitos = new ArrayList<>();
    private final Map<String, TextureRegion[]> cacheEfeitos = new HashMap<>();

    /** Projetil (flecha/magia/nota) indo do atacante ate o mob; ao chegar toca o hit. */
    private static class Projetil {
        final TextureRegion regiao;
        final float x0, y0, x1, y1;
        final String efeitoHit;
        float tempo = 0f;
        final float duracao;
        Projetil(TextureRegion regiao, float x0, float y0, float x1, float y1, String efeitoHit) {
            this.regiao = regiao; this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.efeitoHit = efeitoHit;
            this.duracao = Math.max(0.12f, (float) Math.hypot(x1 - x0, y1 - y0) / 160f);
        }
    }
    private final List<Projetil> projeteis = new ArrayList<>();

    /** Bag de loot no chao (so' quem tem direito ao loot ve). */
    private static class BagChao {
        final String id;
        final float x, y;
        final boolean dourada;
        float restante;
        BagChao(String id, float x, float y, boolean dourada, float restante) {
            this.id = id; this.x = x; this.y = y; this.dourada = dourada; this.restante = restante;
        }
    }
    private final Map<String, BagChao> bags = new LinkedHashMap<>();
    private static final float TEMPO_BAG_VISIVEL = 120f; // servidor: LOOT_BAG_VISIVEL_SEG

    private TextureRegion regiaoAlvo, regiaoTargetHit, regiaoFlag, regiaoBag, regiaoBagDourada;
    private HudVitais hud;
    private JanelaLoot janelaLoot;
    private boolean localMorto = false;
    private Table painelMorte;
    private static final float DURACAO_NUMERO_DANO = 0.85f;

    private final Map<String, MobVisual> mobs = new LinkedHashMap<>();
    // Cadaver some depois disso (servidor: MOB_DESPAWN_CORPO_SEG).
    private static final float TEMPO_CORPO_MOB = 60f;
    private static final int FRAME_MORTE = 10;
    private Texture pixelBranco;

    private static class NPCVisual {
        final String id;
        final String tipo;
        final String nome;
        final Jogador movimento;
        final AnimacaoCorpo animacao;

        NPCVisual(String id, String tipo, String nome, Jogador movimento, AnimacaoCorpo animacao) {
            this.id = id;
            this.tipo = tipo;
            this.nome = nome;
            this.movimento = movimento;
            this.animacao = animacao;
        }
    }

    // Tipos de NPC ("npc_id" do servidor, ex: "kharon") com quem o jogador
    // ja ouviu o dialogo completo de 1a vez - preenchido a partir de
    // "npc_dialogue_state" no sync_local_player (persistido no servidor,
    // ver registrarEventosDeRede) pra nao repetir a fala/"quest" dele numa
    // proxima conversa, nem depois de relogar (a pedido do usuario).
    private final Set<String> npcDialogoVisto = new HashSet<>();

    // true em Android/iOS - esconde o controle de Fullscreen do Options (nao
    // faz sentido no celular) e some com o joystick quando chat/settings
    // abrem.
    private final boolean mobile = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
        || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;

    // 4 niveis de zoom - PORTADOS do Mirage Realms (outra MMORPG 2D feita em
    // libGDX, usada como referencia desde a decisao de migrar - ver
    // [[libgdx_migration]]): decompilado o enum de configuracao de zoom
    // (Settings) de um client .jar baixado pelo usuario, achado em
    // uk.co.harveydogs.mirage.client.*.hX8vspdxxdgMyMJ28 (nomes ofuscados por
    // ProGuard), com valores brutos x1=1.0/x2=2.0/x3=3.0/x4=4.0f. Assumi
    // PRIMEIRO que esse numero era camera.zoom direto (x4 = mais AFASTADO) -
    // errado: o usuario confirmou com prints reais do Mirage que x4 e' o
    // MAIS PERTO (sprite maior que x3), igual o "x4" ja sugeria como nome
    // ("4x de aproximacao/magnificacao", nao "camera.zoom=4"). Esse numero e'
    // um fator de MAGNIFICACAO, nao camera.zoom cru - camera.zoom real e' o
    // INVERSO (1/N), que e' exatamente o padrao "menor zoom = mais perto" do
    // libGDX (ver comentario generico sobre isso em outras partes do
    // arquivo). x1 (1/1=1.0) fica mais afastado, x4 (1/4=0.25) mais perto.
    //
    // O proprio Mirage usa "camera.viewportWidth * camera.zoom" cru pra
    // bounds/culling (mesmo padrao que ja existia aqui em
    // desenharOverlayColisao antes dessa troca) - confirma OrthographicCamera
    // direto, sem buffer intermediario nenhum do lado deles, igual o que foi
    // feito aqui ao remover o FBO pixel-perfeito (ver historico abaixo).
    //
    // O tremor/distorcao de pixel que motivou o FBO pixel-perfeito nunca foi
    // necessario nem no Godot (zoom fracionario 1.5/1.25/1.0, snap_2d_
    // vertices_to_pixel=true) nem no Mirage (sem snap nenhum visivel no
    // decompilado) - a causa raiz era o FBO em si: desenhar o mundo numa
    // textura PEQUENA e so' DEPOIS ampliar reintroduz exatamente o tipo de
    // erro de arredondamento que o FBO tentava evitar, so' que escondido num
    // passo extra. Ambos os jogos de referencia desenham direto na tela
    // real. O snap de posicao em desenharJogador()/render() (Math.round(j.x
    // / camera.zoom) * camera.zoom) foi mantido mesmo assim como seguranca
    // extra pro tremor de movimento (sub-pixel continuo seguindo local.x/y,
    // um problema diferente de distorcao de zoom) - so' funciona direito
    // agora que camera.zoom e' o zoom REAL (nao mais travado em 1 pelo FBO).
    //
    // 1/N (acima) ainda deixava tudo MUITO maior que o Mirage de verdade -
    // usuario confirmou com prints lado a lado que, mesmo com a direcao certa
    // (x4 mais perto), o personagem/tocha aqui saiam ~2-2.2x maiores em
    // pixel de tela que no Mirage no MESMO nivel "x4" (medido comparando o
    // sprite da tocha nas 2 prints, objeto estatico/neutro, menos sujeito a
    // erro de medicao que o boneco). Escala ajustada por esse fator -
    // formula agora e' 2/N em vez de 1/N (praticamente o mesmo 1/N, so' com
    // o numerador dobrado pra mostrar o dobro de mundo em qualquer nivel).
    // Nao e' uma medicao 100% exata (print comprimida, sprite com brilho/
    // contorno atrapalhando a borda) - pode ainda precisar de ajuste fino
    // depois de testar ao vivo de novo.
    // No Mirage de verdade, o desktop so' pode escolher x3/x4 (um if direto
    // em Gdx.app.getType()==Desktop no construtor da tela de Options deles,
    // reduzindo o array de opcoes pra so' 2 itens) - mobile recebe as 4. NAO
    // e' resolucao de tela nem performance (o usuario desconfiou disso, mas
    // o codigo deles e' literalmente so' uma checagem de plataforma).
    //
    // O usuario tambem testou esse range do Mirage no MOBILE de verdade (2
    // prints, "zoom mobile x1/x4") e achou o x1 deles exagerado pro celular
    // - pediu uma faixa SO' PRO MOBILE, mais fechada: o que era x4 no
    // desktop (zoom=0.5, ja' testado/aprovado - ver [[magnificacao limpa]]
    // abaixo) devia virar o TETO mais afastado do mobile (novo x1), com
    // x2/x3/x4 dai pra frente aproximando AINDA MAIS.
    //
    // Formula zoom=2/N (N=1..4 no Mirage original) - deu a escala certa
    // (validada medindo personagem/tocha pixel a pixel contra o Mirage).
    //
    // Essa familia de valores (N IMPAR, ex: 2/3=0.667 = magnificacao 1.5x)
    // CHEGOU a ser trocada por zoom=1/M (so' M inteiro, magnificacao sempre
    // 1x/2x/3x...) depois do usuario reportar distorcao de pixel no zoom
    // mais fino (print "Boneco.png") - a teoria na hora era que magnificacao
    // fracionaria (1.5x) sempre causa esse tipo de costura. ERRADA: medindo
    // os pixels de verdade daquele print (run-length das fileiras de
    // cor solida), as fileiras HORIZONTAIS saiam quase 100% limpas (multiplo
    // exato de 3), so' as VERTICAIS tinham problema (~18% fora do padrao) -
    // uma distorcao SO' NUM EIXO nao faz sentido vir de "magnificacao errada"
    // (isso afetaria os 2 eixos igual). A causa real era outra: o offset de
    // centralizacao vertical (ver offsetCentralizacao abaixo) nao estava
    // sendo arredondado pro grid de zoom antes de somar, tirando SO' a
    // camera.position.Y (nao o personagem, nao os tiles) do alinhamento de
    // pixel com o resto do mundo - exatamente um bug so'-no-eixo-Y, batendo
    // com o padrao medido. Corrigido ali; zoom=2/N (a escala fina de
    // verdade, mais proxima do Mirage) voltou aqui.
    // Valores cortados pela metade (2/N -> 1/N) - o mundo inteiro encolheu
    // pela metade junto com a mudanca de SQM/tile de 32 pra 16px (ver
    // Jogador.TILE), entao o zoom precisa compensar pra manter o mesmo
    // tamanho aparente na tela que tinha antes.
    private final float[] NIVEIS_ZOOM_DESKTOP = {1f / 3f, 1f / 4f};
    private final float[] NIVEIS_ZOOM_MOBILE = {1f / 4f, 1f / 5f, 1f / 6f, 1f / 7f};
    private final String[] NOMES_ZOOM_DESKTOP = {"x3", "x4"};
    private final String[] NOMES_ZOOM_MOBILE = {"x1", "x2", "x3", "x4"};
    private final float[] NIVEIS_ZOOM = mobile ? NIVEIS_ZOOM_MOBILE : NIVEIS_ZOOM_DESKTOP;
    private final String[] NOMES_ZOOM = mobile ? NOMES_ZOOM_MOBILE : NOMES_ZOOM_DESKTOP;
    // Comeca no ULTIMO nivel (x4, mais perto) nas 2 plataformas - "Default"
    // no enum original do Mirage e' justamente o x4 do desktop.
    private int indiceZoom = NIVEIS_ZOOM.length - 1;

    private final OrthographicCamera camera;
    private float zoom = NIVEIS_ZOOM[indiceZoom];

    private final SpriteBatch batch;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    // Atlas unico (graphics/graphics.atlas+.png, gerado por "gradlew
    // desktop:pack" a partir de core/assets-raw/graphics/**) - substitui os
    // ~15 Texture individuais que existiam aqui antes. Nome de cada regiao
    // e' o caminho relativo a assets-raw/graphics SEM extensao (ex:
    // "sprites/body/Knight") - ver mesmo comentario em AuthScreen.java.
    private final TextureAtlas atlas;
    private TextureRegion spriteBase;
    private AnimacaoCorpo animacaoBase;

    /** Uma camada de skin (base/body/helm/acc) ja recortada + cor. */
    private static class CamadaSkin {
        final AnimacaoCorpo animacao;
        final Color cor;
        CamadaSkin(AnimacaoCorpo animacao, Color cor) { this.animacao = animacao; this.cor = cor; }
    }
    // Camadas por nome de jogador (local e remotos), na ordem de desenho.
    private final Map<String, List<CamadaSkin>> skinsJogadores = new HashMap<>();
    private final Map<String, AnimacaoCorpo> cacheAnimacoesSkin = new HashMap<>();

    // Overlay de debug (tecla C) - desenha em vermelho translucido todo SQM
    // que ColisaoGrid considera parede, pra comparar visualmente com o mapa
    // de verdade e achar tile de colisao errado/faltando.
    private boolean mostrarColisao = false;
    private Texture pixelColisao;
    private boolean mapaRegistradoNoServidor = false;
    private boolean sincronizacaoInicialRecebida = false;
    private Runnable callbackSincronizacaoInicial = () -> {};

    // GUI de Settings/Options - porta de Settings.gd (SettingsMenu/OptionsMenu
    // do HUD real). ESC ou o botao de engrenagem na barra do topo abrem/fecham
    // Settings - Stage/Skin proprios (nao o mundo, que e' desenhado com
    // SpriteBatch cru) porque so' esse pedacinho da tela precisa de widgets
    // Scene2D.
    private final Runnable aoSair;
    private boolean saidaIniciada = false;
    private boolean saidaConcluida = false;
    private com.badlogic.gdx.utils.Timer.Task timeoutSalvarPosicao;
    private Stage uiStage;
    private Skin skin;
    // Chrome dos 3 botoes da barra do topo (CanvasLayer/TopMenu de
    // player.tscn: ChatButton/SettingsBtn/MenuButton) - sprites/gui/Button1*
    // porta certinho pro topo por ter a borda de cima reta (encosta no limite
    // da tela) e a de baixo arredondada/afunilada.
    private TextureRegion texBotaoTopo, texBotaoTopoHover, texBotaoTopoClick;
    private TextureRegion iconeChat, iconeConfig, iconeMenu;
    private TextureRegion iconeCheckOn, iconeCheckOff;
    private Table painelSettings, painelOptions;
    private SelectBox<String> botaoZoom;
    private Button botaoTopoChat, botaoTopoMenu, botaoTopoConfig;
    private CheckBox checkFullscreen;
    // GUI de chat portada 1:1 da simulacao de estresse (TesteGame/ChatUI) -
    // so' pra validar o layout/toque no mobile; ainda nao fala com o servidor
    // de verdade (ver ChatUI, sem mudanca nenhuma aqui).
    private ChatUI chat;
    private BookMenuUI bookMenu;
    private DialogoNPCUI dialogoNPC;
    private NPCVisual npcEmDialogo;
    // Banner "entrou numa area nova" (ver AreaNomeUI) - areaAtual guarda o
    // area_id (camada "AreasName" do Tiled) de onde o jogador esta AGORA,
    // null se fora de qualquer area nomeada; comparado a cada frame em
    // render() pra disparar o banner so' na TRANSICAO (entrar numa area
    // diferente da anterior), nunca repetido enquanto parado dentro dela.
    private AreaNomeUI areaNomeUI;
    private String areaAtual;
    // Balaozinho (ver sprites/Notifications) que aparece do lado do jogador
    // local enquanto a respectiva tela (chat/menu/settings) esta aberta - sai
    // so' quando ela fecha (a pedido do usuario; antes era um timer fixo de
    // 1.4s, que fechava sozinho mesmo com a tela ainda aberta). Menu nao tem
    // uma tela de verdade ainda (BookMenu nao foi portado), entao o proprio
    // clique no botao liga/desliga esse flag.
    private TextureRegion notifChat, notifConfig;
    // Fumaca de spawn (sprites/SFXs/Spawn/Smoke.png, 7 quadros de 16px) -
    // toca uma vez so' (sem loop) na posicao de spawn assim que o mundo abre.
    private TextureRegion spawnSmokeTex;
    private com.badlogic.gdx.graphics.g2d.Animation<TextureRegion> spawnSmokeAnim;
    private float spawnSmokeTempo = 0f;
    private float spawnSmokeX, spawnSmokeY;
    // FPS/ms embaixo dos 3 botoes do topo (debug/performance). FPS atualiza
    // todo frame (e' so' um int que a API ja' entrega pronto); ms atualiza
    // so' de 5 em 5 segundos (a pedido do usuario - o numero mudando todo
    // frame era mais ruido que informacao) e muda de cor (verde/amarelo/
    // laranja/vermelho) pra dar uma leitura rapida sem precisar ler o numero.
    private Label labelFps, labelMs;
    private float acumuladorMs = 0f;
    private static final float INTERVALO_LEITURA_MS = 5f;
    private static final Color COR_MS_BOA = new Color(0.2f, 0.9f, 0.2f, 1f);
    private static final Color COR_MS_OK = new Color(0.95f, 0.9f, 0.1f, 1f);
    private static final Color COR_MS_RUIM = new Color(1f, 0.55f, 0f, 1f);
    private static final Color COR_MS_PESSIMA = new Color(0.9f, 0.15f, 0.15f, 1f);
    private Joystick joystick;
    private TextureRegion texJoystickBase, texJoystickKnob;

    public WorldScreen(GameSocket socket, String nomePersonagem, String classePersonagem, Runnable aoSair) {
        this.socket = socket;
        this.aoSair = aoSair;
        mapa = new MapaMundo();
        conversor = mapa.conversor;
        colisao = new ColisaoGrid(mapa.propriedades.hitboxesMundo, mapa.propriedades.bordasFinas, conversor);
        iluminacao = new MapaIluminacao();

        // SpawnPoints do World.tmx (spec secao 5) - usa "initial" se existir;
        // senao NAO adivinha nome nenhum (o spawn_id cadastrado no mapa ja
        // trocou de nome durante essa conversao - "first_temple" -> "temple"
        // - chutar uma string especifica e' fragil) - pega qualquer spawn
        // que exista, ja que so' tem 1 cadastrado hoje. So' cai pro ponto
        // hardcoded antigo se o mapa nao tiver NENHUM SpawnPoint.
        Vector2 spawnMapa = mapa.propriedades.spawns.get("initial");
        if (spawnMapa == null && !mapa.propriedades.spawns.isEmpty()) {
            spawnMapa = mapa.propriedades.spawns.values().iterator().next();
        }
        if (spawnMapa != null) {
            // SpawnPoint do mapa tambem encaixado no SQM (ida e volta pelo
            // espaco cru, onde o snap e' definido).
            spawnX = conversor.rawParaMundoX(Jogador.snapCentroXCru(conversor.mundoParaRawX(spawnMapa.x)));
            spawnY = conversor.rawParaMundoY(Jogador.snapBaseYCru(conversor.mundoParaRawY(spawnMapa.y)));
        } else {
            // Mesma conta de player.gd::snap_to_tile_center - X centraliza no
            // tile, Y ancora no fundo dele (pes do personagem), feita no
            // espaco de coordenada CRU antes de converter pro mundo libGDX.
            spawnX = conversor.rawParaMundoX(Jogador.snapCentroXCru(SPAWN_RAW_X));
            spawnY = conversor.rawParaMundoY(Jogador.snapBaseYCru(SPAWN_RAW_Y));
        }

        local = new Jogador(nomePersonagem, classePersonagem, spawnX, spawnY);

        camera = new OrthographicCamera();
        // getBackBufferWidth/Height, nao getWidth/Height (mesma divergencia
        // HiDPI documentada em construirUiSettings()/resize()) - 1 unidade de
        // mundo == 1 pixel real da tela a zoom=1, sem esse casamento o jogo
        // inteiro desenha num tamanho errado em qualquer monitor com escala
        // fracionaria. atualizarCamera() (chamada todo frame em render())
        // recalibra isso sozinha se a janela for redimensionada.
        camera.setToOrtho(false, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        camera.zoom = zoom;
        camera.position.set(local.x, local.y, 0);
        camera.update();

        atlas = new TextureAtlas(Gdx.files.internal("graphics/graphics.atlas"));
        batch = new SpriteBatch();
        font = criarFonteNome();
        spriteBase = atlas.findRegion("sprites/base/BaseSoul");
        animacaoBase = criarAnimacao(spriteBase);

        notifChat = atlas.findRegion("sprites/notifications/Chat");
        notifConfig = atlas.findRegion("sprites/notifications/Settings");

        spawnSmokeTex = atlas.findRegion("sprites/spawn/Smoke");
        TextureRegion[] quadrosFumaca = new TextureRegion[7];
        for (int i = 0; i < 7; i++) quadrosFumaca[i] = new TextureRegion(spawnSmokeTex, i * 16, 0, 16, 16);
        spawnSmokeAnim = new com.badlogic.gdx.graphics.g2d.Animation<>(0.05f, quadrosFumaca);
        spawnSmokeAnim.setPlayMode(com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.RED);
        pm.fill();
        pixelColisao = new Texture(pm);
        pm.dispose();
        Pixmap pmBranco = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pmBranco.setColor(Color.WHITE);
        pmBranco.fill();
        pixelBranco = new Texture(pmBranco);
        pmBranco.dispose();
        regiaoAlvo = atlas.findRegion("ui/slots/Target");
        regiaoTargetHit = atlas.findRegion("ui/items/HitHitbox");
        regiaoFlag = atlas.findRegion("ui/items/Flag");
        regiaoBag = atlas.findRegion("ui/currency/BasicBag");
        regiaoBagDourada = atlas.findRegion("ui/currency/GoldBag");
        criarMobsDoMapa();

        registrarEventosDeRede();
        construirUiSettings();

        // Cor e transparencia originais do player.gd (0.868, 0.868, 0.868, 0.725)
        Color corLuzPlayer = new Color(0.868f, 0.868f, 0.868f, 0.725f);
        float raioLuzPlayer = 80f;
        luzJogador = new MapaPropriedades.Luz(local.x, local.y, corLuzPlayer, raioLuzPlayer);
        todasAsLuzes.addAll(mapa.propriedades.luzes);
        todasAsLuzes.add(luzJogador);

        // Stage primeiro (prioridade pros cliques nos botoes), depois o mundo
        // (scroll = zoom livre, C = overlay de colisao, ESC = abre/fecha
        // Settings) - movimento (WASD/setas) e' via isKeyPressed() direto em
        // processarEntrada(), nao passa por aqui, entao continua funcionando
        // com qualquer painel aberto (igual o jogo real: "so' o chat trava
        // movimento").
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (chat.isVisivel() || settingsAberta() || bookMenu.isVisible() || dialogoNPC.isVisible()) return false;
                
                com.badlogic.gdx.math.Vector3 coords = camera.unproject(new com.badlogic.gdx.math.Vector3(screenX, screenY, 0));
                float wx = coords.x;
                float wy = coords.y;
                
                if (!localMorto && cliqueEmBag(wx, wy)) return true;
                if (!localMorto && cliqueEmMob(wx, wy)) return true;

                NPCVisual npcClicado = null;
                for (NPCVisual npc : npcs.values()) {
                    float largura = FRAME_LARGURA * ESCALA_SPRITE;
                    float altura = npc.animacao.idleBaixo.getRegionHeight() * ESCALA_SPRITE;
                    float ancoraX = Math.round(npc.movimento.x / camera.zoom) * camera.zoom;
                    float ancoraY = Math.round(npc.movimento.y / camera.zoom) * camera.zoom;
                    
                    if (wx >= ancoraX - largura / 2f && wx <= ancoraX + largura / 2f && wy >= ancoraY && wy <= ancoraY + altura) {
                        npcClicado = npc;
                        break;
                    }
                }
                
                if (npcClicado != null) {
                    if (distanciaQuadradaAteNPC(npcClicado) <= ALCANCE_NPC_AO_QUADRADO) {
                        abrirDialogoNPC(npcClicado);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                // Scroll do mouse nao controla mais o zoom (a pedido do
                // usuario) - zoom agora so' muda pelo botao x1/x2/x3 do
                // Options. Ainda consome o evento (retorna true) pra nao
                // deixar nenhum scroll "vazar" pra outro processor.
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.C) {
                    mostrarColisao = !mostrarColisao;
                    return true;
                }
                if (keycode == Input.Keys.E) {
                    if (chat.estaDigitando()) return false;
                    if (dialogoNPC.isVisible()) {
                        dialogoNPC.avancarOuFechar();
                        return true;
                    }
                    NPCVisual npcProximo = npcMaisProximoParaConversar();
                    if (npcProximo != null) {
                        abrirDialogoNPC(npcProximo);
                        return true;
                    }
                    alternarBookMenu();
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    // Janela de loot e alvo saem antes de qualquer outra coisa.
                    if (janelaLoot.isVisible()) { janelaLoot.fechar(); return true; }
                    if (alvoMob != null) { alvoMob = null; return true; }
                    // Fecha a interface ativa antes de abrir Settings.
                    if (dialogoNPC.isVisible()) dialogoNPC.fechar();
                    else if (chat.isVisivel()) alternarChat();
                    else if (bookMenu.isVisible()) alternarBookMenu();
                    else alternarSettings();
                    return true;
                }
                // Espaco abre/fecha o chat - mas so' quando NAO se esta
                // digitando nele (senao cada espaco dentro de uma mensagem
                // fecharia o chat no meio da digitacao).
                if (keycode == Input.Keys.SPACE) {
                    if (chat.estaDigitando()) return false;
                    alternarChat();
                    return true;
                }
                // Enter com o chat ja aberto (mas ainda sem foco) comeca a
                // digitar - ver render()::isKeyJustPressed(ENTER), nao aqui
                // (com o campo ja focado, quem trata o Enter e' o
                // TextFieldListener do proprio campo, que envia a mensagem).
                // Tratado via polling direto (nao esse evento) porque esse
                // keyDown() so' roda se o uiStage (1o processor do
                // multiplexer) NAO tiver engolido o Enter antes - acontecia
                // as vezes dependendo de qual actor estava com foco de
                // teclado, fazendo o Enter "nao fazer nada" (bug reportado
                // pelo usuario). isKeyJustPressed() no render() nao depende
                // de nenhum processor/foco, sempre dispara.
                return false;
            }
        });
        Gdx.input.setInputProcessor(multiplexer);
    }

    /** Monta a barra do topo (Chat/Config/Menu, ver criarBarraTopo) e os 2
     * paineis de Settings - porta 1:1 de CanvasLayer/SettingsMenu (popup
     * pequeno) e CanvasLayer/OptionsMenu (tela cheia) de player.tscn, valores
     * de anchor/cor extraidos direto de la, nao inventados. */
    // Mundo virtual da uiStage (barra do topo/Settings/chat) - era ScreenViewport
    // (1 unidade = 1 pixel real), trocado por ExtendViewport igual AuthScreen
    // pra poder escalar tudo pra cima no mobile ("aumentar literalmente tudo",
    // a pedido do usuario) sem precisar tocar em cada tamanho individual -
    // mesma proporcao 960x540 usada la, pra ficar consistente entre as 2 telas.
    private static final float MUNDO_UI_LARGURA = 960f;
    private static final float MUNDO_UI_ALTURA = 540f;

    private void construirUiSettings() {
        // getBackBufferWidth/Height (nao getWidth/Height) - no desktop com
        // escala de tela HiDPI (Linux/Windows), getWidth() devolve unidades
        // LOGICAS da janela, nao pixels reais do framebuffer; gerar a fonte
        // nessa escala menor a deixava borrada quando o ExtendViewport
        // esticava ela pro framebuffer real, maior. No Android os dois ja
        // batem (sem essa divergencia), por isso so' aparecia borrado no PC.
        float escala = Math.min(Gdx.graphics.getBackBufferWidth() / MUNDO_UI_LARGURA, Gdx.graphics.getBackBufferHeight() / MUNDO_UI_ALTURA);
        skin = UiSkin.criar(escala);
        uiStage = new Stage(new ExtendViewport(MUNDO_UI_LARGURA, MUNDO_UI_ALTURA));

        criarBarraTopo();

        // Icones do CheckBox real (FullScreenCheck::theme_override_icons -
        // "unchecked" no Godot usa Gui_button_Press.png, um quadrado escuro
        // vazio, nao e' erro/placeholder, e' o desenho de fato).
        iconeCheckOn = atlas.findRegion("ui/Gui_checkbox_mark");
        iconeCheckOff = atlas.findRegion("ui/Gui_button_Press");

        painelSettings = new Table();
        painelSettings.setFillParent(true);
        painelSettings.center();
        painelSettings.add(criarConteudoSettings());
        painelSettings.setVisible(false);
        uiStage.addActor(painelSettings);

        painelOptions = new Table();
        painelOptions.setFillParent(true);
        painelOptions.add(criarConteudoOptions()).grow();
        painelOptions.setVisible(false);
        uiStage.addActor(painelOptions);

        chat = new ChatUI(uiStage, skin, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), nomeVisivel(local.nome), local.classe);
        chat.setVisivel(false);

        texJoystickBase = atlas.findRegion("ui/Joystick");
        texJoystickKnob = atlas.findRegion("ui/Joystick_Middle");
        joystick = new Joystick(uiStage, texJoystickBase, texJoystickKnob);
        bookMenu = new BookMenuUI(uiStage, skin, atlas, socket, local.classe);
        hud = new HudVitais(uiStage, skin, atlas);
        janelaLoot = new JanelaLoot(uiStage, skin, atlas, bookMenu::iconeDoItem, this::pegarLoot);
        criarPainelMorte();
        dialogoNPC = new DialogoNPCUI(uiStage, skin, atlas.findRegion("ui/currency/Silver"), escala);
        areaNomeUI = new AreaNomeUI(uiStage, skin, atlas.findRegion("sheet/r83_c11"), escala);
    }

    /** CanvasLayer/TopMenu de player.tscn: 3 botoes iguais (sprites/gui/
     * Button1 + hover/click) encostados no topo direito da tela, com o icone
     * trocando em cada um - ChatButton/ConfigButton/MenuButton nessa ordem
     * esquerda->direita (ChatButton/MenuButton/SettingsBtn, ordem real de
     * player.tscn::TopMenu - SettingsBtn e' o ULTIMO filho, encostado no
     * canto de verdade, MenuButton fica no meio). Encostada no topo (sem
     * padTop) a pedido do usuario - so' o lado direito tem uma margem, pra
     * nao cortar o botao atras do "notch"/barra de status em celulares. */
    private void criarBarraTopo() {
        // Nearest (nao Linear) - sao sprites de pixel art pequenos ampliados
        // bem mais que o tamanho original; Linear borrava (suaviza entre
        // pixels vizinhos), Nearest mantem a borda dura certa pra pixel art.
        texBotaoTopo = atlas.findRegion("ui/Button1");
        texBotaoTopoHover = atlas.findRegion("ui/Button1Hover");
        texBotaoTopoClick = atlas.findRegion("ui/Button1Click");
        iconeChat = atlas.findRegion("ui/ChatButton");
        // Invertidos a pedido do usuario (estavam trocados no jogo: o icone
        // de config e o da bag/menu apareciam um no lugar do outro).
        iconeConfig = atlas.findRegion("ui/MenuButton");
        iconeMenu = atlas.findRegion("ui/ConfigButton");

        Table barra = new Table();
        barra.setFillParent(true);
        barra.top().right().pad(0, 0, 0, 20);
        botaoTopoChat = criarBotaoTopo(iconeChat, this::alternarChat);
        barra.add(botaoTopoChat).size(TAMANHO_BOTAO_TOPO).padRight(12);
        botaoTopoMenu = criarBotaoTopo(iconeMenu, this::alternarBookMenu);
        barra.add(botaoTopoMenu).size(TAMANHO_BOTAO_TOPO).padRight(12);
        // Ultimo (mais a direita, no canto de verdade) e' quem abre Settings -
        // igual SettingsBtn no Godot, que e' o ultimo filho de TopMenu.
        botaoTopoConfig = criarBotaoTopo(iconeConfig, this::alternarSettings);
        barra.add(botaoTopoConfig).size(TAMANHO_BOTAO_TOPO).row();

        labelFps = new Label("0 fps", skin, "hud");
        labelMs = new Label("0.0 ms", skin, "hud");
        Table infoDesempenho = new Table();
        infoDesempenho.add(labelFps).right().row();
        infoDesempenho.add(labelMs).right();
        barra.add(infoDesempenho).colspan(3).right().padTop(6);

        uiStage.addActor(barra);
    }

    // Escala BASE do nome, em UNIDADE DE MUNDO (equivale a "8px de mundo" a
    // zoom 1.0 - cortado pela metade de 16, mesmo motivo do NIVEIS_ZOOM e do
    // Jogador.TILE: o mundo inteiro encolheu com a mudanca de SQM 32->16)
    // - desenhado direto no mesmo batch/projecao world-space do personagem
    // (ver desenharNome/render()), entao escala com o zoom AUTOMATICAMENTE
    // via camera.combined, igual o NameLabel do Godot (filho do mesmo
    // Node2D afetado pela Camera2D) - nao precisa mais recalcular nada por
    // frame.
    private static final float NOME_ESCALA_BASE = 0.2f;

    /** Nametag acima da cabeca - mesma fonte/cor/contorno do NameLabel real
     * (player.tscn::CanvasLayer/OverHeadUI/NameLabel): Tahoma Bold
     * (TAHOMAB0.TTF), tamanho 16 (LabelSettings_mgmjd nao tem font_size
     * proprio -> usa o default do Godot), verde ~(0, 0.94, 0) com contorno
     * preto (outline_size 6 no Godot, reduzido - mesma conversao ja usada em
     * UiSkin pros outros fonts, o valor bruto de outline do Godot sempre fica
     * grosso demais 1:1 no FreeType - e' 1, nao 2, porque o usuario ainda
     * achou 2 grosso). Gerada a 32px (2x) e escalada de volta pra 16
     * (setScale NOME_ESCALA_BASE) so' pra nao borrar - essa escala fica fixa
     * pra sempre (ver comentario do campo acima), camera.combined cuida do
     * resto quando o zoom muda. */
    private BitmapFont criarFonteNome() {
        FreeTypeFontGenerator gerador = new FreeTypeFontGenerator(Gdx.files.internal("fonts/TAHOMAB0.TTF"));
        FreeTypeFontGenerator.FreeTypeFontParameter parametros = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parametros.size = 32;
        parametros.color = new Color(0f, 0.94f, 0f, 1f);
        // 2 -> 1 -> 2: tinha ido pra 1 porque o usuario achou 2 grosso demais
        // numa rodada anterior de teste; pediu de volta mais grosso depois
        // (texto fino demais pra ler em movimento) - gerado a 32px (2x, ver
        // setScale abaixo) entao esse 2px de borda vira 1px na tela.
        parametros.borderWidth = 2;
        parametros.borderColor = Color.BLACK;
        parametros.minFilter = TextureFilter.Linear;
        parametros.magFilter = TextureFilter.Linear;
        BitmapFont fonte = gerador.generateFont(parametros);
        fonte.getData().setScale(NOME_ESCALA_BASE);
        // useIntegerPositions vem TRUE por padrao no libGDX - arredonda a
        // posicao de cada glifo pro WORLD-UNIT inteiro mais proximo, nao pro
        // PIXEL DE TELA mais proximo. Faz sentido pra texto de UI em
        // screen-space puro (1 unidade = 1 pixel), mas aqui o nome e'
        // desenhado em world-space atraves de uma camera com zoom != 1 (ver
        // desenharJogador) - o grid de "inteiros" da fonte nao bate com o
        // grid de "multiplos de zoom" que a camera/ancora usam pro proprio
        // snap, e a cada passo discreto da camera o texto "escorrega" um
        // pouco em relacao ao boneco (bug reportado pelo usuario: nome
        // tremendo ao andar, letras parecendo colidir - cada glifo
        // arredondava pro grid errado de um jeito ligeiramente diferente).
        // Desligado aqui - o snap pro pixel de tela certo ja' e' feito a
        // mao em desenharJogador() (nomeX/nomeY = Math.round(v/zoom)*zoom),
        // sem precisar (e sem poder conviver bem com) esse arredondamento
        // automatico e incompativel da fonte por baixo dos panos.
        fonte.setUseIntegerPositions(false);
        gerador.dispose();
        return fonte;
    }

    // Foi 64 -> 128 (pequeno demais pra tocar no mobile) -> 108 (128 ficou
    // grande demais, achado pelo usuario testando) - meio termo, mas so' faz
    // sentido pro mobile (precisa caber o dedo). No PC (mouse, sem exigencia
    // de area de toque) 108 ficava grande demais - reduzido pra 72 so' la,
    // mantendo a mesma proporcao icone/botao (76/108 ~= 0.70).
    private final float TAMANHO_BOTAO_TOPO = mobile ? 108f : 72f;
    private final float ICONE_BOTAO_TOPO = TAMANHO_BOTAO_TOPO * (76f / 108f);

    /** Botao Button1 (up/hover/down) com um icone centralizado por cima -
     * mesmo chrome pros 3 botoes do topo, so' o icone muda. */
    private Button criarBotaoTopo(TextureRegion icone, Runnable aoClicar) {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = new TextureRegionDrawable(texBotaoTopo);
        estilo.over = new TextureRegionDrawable(texBotaoTopoHover);
        estilo.down = new TextureRegionDrawable(texBotaoTopoClick);
        Button botao = new Button(estilo);
        Image imagemIcone = new Image(new TextureRegionDrawable(icone));
        imagemIcone.setScaling(Scaling.fit);
        botao.add(imagemIcone).size(ICONE_BOTAO_TOPO);
        botao.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { aoClicar.run(); }
        });
        return botao;
    }

    private void alternarChat() {
        chat.setVisivel(!chat.isVisivel());
        atualizarVisibilidadeJoystick();
    }

    private void alternarBookMenu() {
        if (!bookMenu.isVisible()) {
            if (chat.isVisivel()) chat.setVisivel(false);
            if (settingsAberta()) fecharSettings();
        }
        bookMenu.alternar();
        atualizarVisibilidadeJoystick();
    }

    /** Some com o joystick (so' existe/importa no mobile) enquanto chat ou
     * settings estao abertos - com um painel por cima, o jogador nao deveria
     * conseguir andar "por baixo" usando o joystick ao mesmo tempo (a pedido
     * do usuario) - volta a aparecer assim que os dois fecham. Chamado todo
     * frame (ver render()), nao so' nos pontos que abrem/fecham chat/settings
     * por aqui - o proprio botao "Close" de dentro do ChatUI tambem fecha o
     * chat, sem passar por alternarChat(), e deixava o joystick escondido pra
     * sempre se essa checagem so' rodasse nos callbacks daqui (bug reportado
     * pelo usuario). */
    private void atualizarVisibilidadeJoystick() {
        joystick.setVisivel(mobile && !chat.isVisivel() && !settingsAberta() && !bookMenu.isVisible());
    }

    /** Com uma das 3 telas (chat/menu/settings) aberta, os OUTROS 2 botoes da
     * barra do topo somem - so' o da tela aberta continua visivel, pra poder
     * fechar ela de novo clicando nele (a pedido do usuario). Chamado todo
     * frame, mesmo padrao de atualizarVisibilidadeJoystick() (robusto contra
     * fechar por um caminho que nao seja o botao, ex: ESC ou o Close de
     * dentro do ChatUI). */
    private void atualizarVisibilidadeBotoesTopo() {
        boolean bookMenuAberto = bookMenu.isVisible();
        boolean algumAberto = chat.isVisivel() || settingsAberta() || bookMenuAberto;
        botaoTopoChat.setVisible(!algumAberto);
        botaoTopoMenu.setVisible(!algumAberto);
        botaoTopoConfig.setVisible(!algumAberto);
    }

    /** Qual balaozinho (se algum) deve estar visivel agora - um por tela
     * aberta, nenhuma prioridade especial entre elas (na pratica so' uma fica
     * aberta por vez, ja que abrir settings fecha o chat e vice-versa não -
     * mas se as 2 abrissem ao mesmo tempo, settings venceria aqui). */
    private TextureRegion notificacaoAtual() {
        if (settingsAberta()) return notifConfig;
        if (chat.isVisivel()) return notifChat;
        return null;
    }

    /** Popup pequeno (220x260 no Godot) - 3 botoes empilhados sem titulo
     * separado: o botao de cima ja se chama "Settings" e abre a tela cheia
     * (CanvasLayer/SettingsMenu/MarginContainer/VBoxContainer: OptionsBtn/
     * BackBtn/ExitBtn, nessa ordem). */
    // 200x70 no mobile (bom la, achado testando); reduzido no PC (ficava
    // grande demais, a pedido do usuario) - mesma proporcao largura/altura.
    private final float LARGURA_BOTAO_SETTINGS = mobile ? 200f : 160f;
    private final float ALTURA_BOTAO_SETTINGS = mobile ? 70f : 56f;

    private Table criarConteudoSettings() {
        Table conteudo = new Table();
        conteudo.setBackground(skin.getDrawable("popup-painel"));
        conteudo.pad(15, 10, 15, 10);
        conteudo.defaults().width(LARGURA_BOTAO_SETTINGS).height(ALTURA_BOTAO_SETTINGS).padBottom(10);

        TextButton botaoSettings = new TextButton("Settings", skin, "cinza-popup");
        botaoSettings.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                painelSettings.setVisible(false);
                painelOptions.setVisible(true);
            }
        });
        conteudo.add(botaoSettings).row();

        // Mesmo estilo/fonte padrao de Settings/Exit (fonteBotao, martel 32px)
        // - "Back" tem o mesmo tanto de letras que "Exit", cabe sem precisar
        // de fonte/escala dedicada menor (a versao anterior usava uma fonte
        // 20px + scale 0.7 so' nesse botao, deixando o texto visivelmente
        // menor que os outros 2, a pedido do usuario pra ficarem iguais).
        TextButton botaoBack = new TextButton("Back", skin, "verde-popup");
        botaoBack.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { fecharSettings(); }
        });
        conteudo.add(botaoBack).row();

        TextButton botaoExit = new TextButton("Exit", skin, "vermelho-popup");
        botaoExit.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                botaoExit.setDisabled(true);
                salvarPosicaoESair();
            }
        });
        conteudo.add(botaoExit).padBottom(0);
        return conteudo;
    }

    /** Tela cheia (CanvasLayer/OptionsMenu): TopBar com titulo, conteudo com
     * scroll (secao "Screen" + grid 2 colunas Zoom/Fullscreen) e BottomBar
     * com Back - mesma estrutura/cores do painel real. */
    private Table criarConteudoOptions() {
        Table raiz = new Table();
        raiz.setBackground(skin.getDrawable("fundo-opcoes"));

        // TopBar e BottomBar do Godot reusam o MESMO StyleBoxFlat (borda so'
        // em cima) - por isso os dois usam aqui o mesmo drawable "barra-baixo".
        Table topBar = new Table();
        topBar.setBackground(skin.getDrawable("barra-baixo"));
        topBar.add(new Label("Settings", skin, "opcoes-titulo"));
        raiz.add(topBar).growX().height(80).row();

        Table corpo = new Table();
        corpo.top();
        corpo.add(new Label("Screen", skin, "secao")).left().padTop(10).row();
        corpo.add(new Label("Change your screen settings here", skin, "opcoes-label")).left().padTop(4).padBottom(20).row();

        Table grid = new Table();
        grid.add(new Label("Camera Zoom", skin, "opcoes-label")).align(Align.right).width(220).pad(6);
        // Dropdown de verdade (SelectBox), nao mais um botao quadrado que
        // ciclava no clique - a pedido do usuario, pra ficar igual ao
        // SelectBox real do Mirage Realms (print de referencia "Selecao de
        // zoom"; estilo "zoom-select" registrado em UiSkin com as cores
        // amostradas de la). NAO precisa mais do debounce de 250ms que o
        // botao antigo tinha (bug de duplo-evento especifico de ChangeListener
        // num Actor dentro de ScrollPane) - SelectBox so' dispara cada opcao
        // selecionada uma vez.
        botaoZoom = new SelectBox<>(skin, "zoom-select");
        // NOMES_ZOOM/NIVEIS_ZOOM ja' sao os arrays certos pra essa
        // plataforma (ver declaracao dos campos - PC so' tem x3/x4, mobile
        // tem x1-x4), entao os indices aqui batem direto com indiceZoom,
        // sem precisar de nenhuma tabela de conversao.
        botaoZoom.setItems(NOMES_ZOOM);
        botaoZoom.setSelectedIndex(indiceZoom);
        botaoZoom.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                indiceZoom = botaoZoom.getSelectedIndex();
                zoom = NIVEIS_ZOOM[indiceZoom];
            }
        });
        grid.add(botaoZoom).width(90).height(36).pad(6).row();

        // Linha de Full Screen so' existe no PC - no celular nao ha janela
        // pra alternar, a opcao nao faz sentido (a pedido do usuario).
        if (!mobile) {
            grid.add(new Label("Full Screen", skin, "opcoes-label")).align(Align.right).width(220).pad(6);
            CheckBox.CheckBoxStyle estiloCheck = new CheckBox.CheckBoxStyle();
            estiloCheck.checkboxOn = new TextureRegionDrawable(iconeCheckOn);
            estiloCheck.checkboxOff = new TextureRegionDrawable(iconeCheckOff);
            estiloCheck.font = skin.getFont("default-font");
            estiloCheck.fontColor = Color.WHITE;
            checkFullscreen = new CheckBox("", estiloCheck);
            checkFullscreen.setChecked(Gdx.graphics.isFullscreen());
            checkFullscreen.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    InvertedRealmsGame.alternarFullscreen();
                    checkFullscreen.setChecked(Gdx.graphics.isFullscreen());
                }
            });
            grid.add(checkFullscreen).size(32).pad(6).row();
        }
        corpo.add(grid).left();

        ScrollPane scroll = new ScrollPane(corpo);
        scroll.setFadeScrollBars(false);
        raiz.add(scroll).grow().pad(20, 40, 0, 40).row();

        Table bottomBar = new Table();
        bottomBar.left();
        bottomBar.setBackground(skin.getDrawable("barra-baixo"));
        TextButton botaoVoltar = new TextButton("Back", skin, "verde-rodape");
        botaoVoltar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                painelOptions.setVisible(false);
                painelSettings.setVisible(true);
            }
        });
        bottomBar.add(botaoVoltar).width(128).height(64).left().pad(18);
        raiz.add(bottomBar).growX().height(100);
        return raiz;
    }

    private void alternarSettings() {
        boolean abrindo = !painelSettings.isVisible() && !painelOptions.isVisible();
        painelSettings.setVisible(abrindo);
        painelOptions.setVisible(false);
        atualizarVisibilidadeJoystick();
    }

    /** Popup OU tela cheia aberta - trava o movimento (WASD/setas) igual o
     * chat ja fazia, pra nao andar por baixo do painel sem querer. */
    private boolean settingsAberta() {
        return painelSettings.isVisible() || painelOptions.isVisible();
    }

    private void fecharSettings() {
        painelSettings.setVisible(false);
        painelOptions.setVisible(false);
        atualizarVisibilidadeJoystick();
    }

    private void registrarEventosDeRede() {
        socket.on("sync_local_player", (nomeEvt, data) -> {
            if (data == null) return;
            float rawX = data.getFloat("pos_x", -1f);
            float rawY = data.getFloat("pos_y", -1f);
            if (rawX != -1f && rawY != -1f) {
                // Encaixa no SQM (mesmo snap do spawn) - posicao salva fora do
                // grid (ex: do tempo do TILE=32) deixava o player entre 2 SQMs.
                local.x = conversor.rawParaMundoX(Jogador.snapCentroXCru(rawX));
                local.y = conversor.rawParaMundoY(Jogador.snapBaseYCru(rawY));
            }
            spawnSmokeX = local.x;
            spawnSmokeY = local.y;
            spawnSmokeTempo = 0f;
            local.direcao = data.getString("direction", "down");
            // O servidor tambem manda sync_local_player PARCIAL (so' posicao,
            // ao esbarrar num NPC) - sem esse if, inventario/moedas/skills
            // eram zerados no client.
            if (data.has("inventory")) {
                definirSkins(local.nome, data.get("skins"));
                bookMenu.carregarSkins(data.get("skin_db"), data.get("skins"));
                // HP/MP: -1 no banco = cheio.
                float maxHp = data.getFloat("max_hp", -1f), maxMp = data.getFloat("max_mp", -1f);
                float hpJoin = data.getFloat("current_hp", -1f), mpJoin = data.getFloat("current_mp", -1f);
                hud.definir(hpJoin < 0f ? maxHp : hpJoin, maxHp, mpJoin < 0f ? maxMp : mpJoin, maxMp);
                bookMenu.carregarItemDb(data.get("item_db"));
                bookMenu.atualizarInventario(data.get("inventory"));
                bookMenu.atualizarMoedas(data.getLong("currency", 0L));
                bookMenu.atualizarEquipados(data.get("equipped_items"));
                bookMenu.atualizarSkills(data.get("skills"), data.getInt("level", 1), data.getInt("exp", 0), data.getInt("kills", 0));
            }
            JsonValue dialogosVistos = data.get("npc_dialogue_state");
            if (dialogosVistos != null) {
                for (JsonValue entrada = dialogosVistos.child; entrada != null; entrada = entrada.next) {
                    if (entrada.asBoolean()) npcDialogoVisto.add(entrada.name);
                }
            }
            if (!mapaRegistradoNoServidor) {
                mapaRegistradoNoServidor = true;
                registrarMapaNoServidor();
            }
            if (!sincronizacaoInicialRecebida) {
                sincronizacaoInicialRecebida = true;
                callbackSincronizacaoInicial.run();
            }
        });

        socket.on("current_players", (nomeEvt, data) -> {
            if (data == null) return;
            for (JsonValue p = data.child; p != null; p = p.next) {
                adicionarRemotoSeNovo(p);
            }
        });

        socket.on("player_joined", (nomeEvt, data) -> adicionarRemotoSeNovo(data));

        socket.on("npc_sync", (nomeEvt, data) -> {
            if (data != null && MAP_ID_SERVIDOR.equals(data.getString("map", MAP_ID_SERVIDOR))) {
                sincronizarNPCs(data);
            }
        });

        // ---- Mobs (ver servidor.py: mob_ai_loop / mob_cleanup_loop) ----
        socket.on("mob_pos", (nomeEvt, data) -> {
            // [mob_id, x, y, dir_int, owner, returning, duracao do passo]
            if (data == null || !data.isArray() || data.size < 4) return;
            MobVisual mob = mobs.get(data.get(0).asString());
            if (mob == null) return;
            mob.voltando = data.size > 5 && data.get(5).asInt() == 1;
            float dur = data.size > 6 ? data.get(6).asFloat() : 0f;
            moverMob(mob, data.get(1).asFloat(), data.get(2).asFloat(), direcaoDoInt(data.get(3).asInt()), dur, false);
        });
        socket.on("mob_damaged", (nomeEvt, data) -> {
            if (data == null) return;
            MobVisual mob = mobs.get(data.getString("mob_id", ""));
            if (mob == null) return;
            mob.maxHp = Math.max(1f, data.getFloat("max_hp", mob.maxHp));
            mob.hp = Math.max(0f, data.getFloat("new_hp", mob.hp));
            boolean critico = data.getBoolean("is_crit", false);
            numerosDano.add(new NumeroDano(mob.x, mob.y, data.getInt("damage", 0) + (critico ? "!" : ""), critico));
            // Efeito de hit; a distancia, primeiro o projetil sai do atacante.
            String efeito = data.getString("hit_type", "physical_hit");
            Jogador atacante = jogadorPorNome(data.getString("attacker_id", ""));
            TextureRegion projetil = regiaoDoCaminho(data.getString("proj", ""));
            if ("Ranged".equals(data.getString("w_type", "")) && projetil != null && atacante != null) {
                projeteis.add(new Projetil(projetil, atacante.x, atacante.y + 8f, mob.x, mob.y + 8f, efeito));
            } else {
                tocarEfeito(efeito, mob.x, mob.y);
            }
        });
        socket.on("player_damaged", (nomeEvt, data) -> {
            // Mob bateu num player: TargetHit no mob, numero + efeito no player.
            if (data == null) return;
            MobVisual mob = mobs.get(data.getString("attacker_mob_id", ""));
            if (mob != null) mob.tempoTargetHit = 1.1f;
            String alvo = data.getString("target_player", "");
            Jogador j = jogadorPorNome(alvo);
            if (j != null) {
                numerosDano.add(new NumeroDano(j.x, j.y + 4f, String.valueOf(data.getInt("damage", 0)), false));
                tocarEfeito(data.getString("hit_type", "physical_hit"), j.x, j.y);
            }
            if (alvo.equals(local.nome)) {
                float hpNovo = data.getFloat("new_hp", hud.hpAtual());
                hud.definir(hpNovo, data.getFloat("max_hp", -1f), -1f, -1f);
                if (hpNovo <= 0f) morrerLocal();
            }
        });
        socket.on("sync_vitals", (nomeEvt, data) -> {
            if (data == null) return;
            hud.definir(data.getFloat("current_hp", -1f), data.getFloat("max_hp", -1f),
                data.getFloat("current_mp", -1f), data.getFloat("max_mp", -1f));
        });
        socket.on("loot_result", (nomeEvt, data) -> {
            if (data != null) janelaLoot.mostrar(data);
        });
        socket.on("loot_taken", (nomeEvt, data) -> {
            if (data == null) return;
            removerBag(data.getString("loot_id", ""));
        });
        socket.on("mob_died", (nomeEvt, data) -> {
            if (data == null) return;
            MobVisual mob = mobs.get(data.getString("mob_id", ""));
            if (mob == null) return;
            mob.morto = true;
            mob.hp = 0f;
            mob.tempoCorpo = TEMPO_CORPO_MOB;
            mob.voltando = false;
            if (mob.id.equals(alvoMob)) alvoMob = null;
            // Bag de loot so' vem pra quem tem direito (loot_id nao vazio).
            String lootId = data.getString("loot_id", "");
            if (!lootId.isEmpty() && data.has("pos_x")) {
                bags.put(lootId, new BagChao(lootId, conversor.rawParaMundoX(data.getFloat("pos_x")),
                    conversor.rawParaMundoY(data.getFloat("pos_y")), data.getBoolean("has_items", false), TEMPO_BAG_VISIVEL));
            }
            if (data.has("pos_x")) {
                moverMob(mob, data.getFloat("pos_x"), data.getFloat("pos_y"), mob.direcao, 0f, true);
            } else {
                mob.posicionar(mob.x, mob.y, mob.direcao);
            }
        });
        socket.on("mob_vanish", (nomeEvt, data) -> {
            // Perseguiu longe demais e sumiu (conta como morto, sem cadaver).
            if (data == null) return;
            MobVisual mob = mobs.get(data.getString("mob_id", ""));
            if (mob == null) return;
            mob.morto = true;
            mob.tempoCorpo = 0f;
            if (mob.id.equals(alvoMob)) alvoMob = null;
            // Mesmo efeito de fumaca do spawn do player (sprites/spawn/Smoke).
            if (mob.visivel) efeitos.add(new Efeito(quadrosFumaca(), mob.x, mob.y));
        });
        socket.on("mob_respawn", (nomeEvt, data) -> {
            if (data == null) return;
            MobVisual mob = mobs.get(data.getString("mob_id", ""));
            if (mob == null) return;
            mob.morto = false;
            mob.hp = mob.maxHp;
            mob.tempoCorpo = 0f;
            mob.voltando = false;
            mob.visivel = false; // reaparece no mob_pos que vem logo em seguida (SQM onde renasceu)
        });
        socket.on("sync_area_data", (nomeEvt, data) -> {
            JsonValue listaBags = data == null ? null : data.get("bags");
            if (listaBags != null) {
                for (JsonValue b = listaBags.child; b != null; b = b.next) {
                    String id = b.getString("loot_id", "");
                    if (id.isEmpty() || bags.containsKey(id)) continue;
                    bags.put(id, new BagChao(id, conversor.rawParaMundoX(b.getFloat("pos_x", 0f)),
                        conversor.rawParaMundoY(b.getFloat("pos_y", 0f)), b.getBoolean("has_items", false),
                        TEMPO_BAG_VISIVEL - b.getFloat("idade", 0f)));
                }
            }
            JsonValue lista = data == null ? null : data.get("mobs");
            if (lista == null) return;
            for (JsonValue info = lista.child; info != null; info = info.next) {
                MobVisual mob = mobs.get(info.getString("mob_id", ""));
                if (mob == null) continue;
                mob.maxHp = Math.max(1f, info.getFloat("max_hp", mob.maxHp));
                mob.hp = info.getFloat("hp", mob.hp);
                mob.morto = info.getBoolean("is_dead", false);
                mob.tempoCorpo = mob.morto ? Math.max(0f, TEMPO_CORPO_MOB - info.getFloat("dead_for", 0f)) : 0f;
                mob.voltando = info.getBoolean("returning", false);
                if (info.has("pos_x")) {
                    moverMob(mob, info.getFloat("pos_x"), info.getFloat("pos_y"), info.getString("direction", "down"), 0f, true);
                }
            }
        });

        socket.on("npc_moved", (nomeEvt, data) -> {
            if (data != null && MAP_ID_SERVIDOR.equals(data.getString("map", MAP_ID_SERVIDOR))) {
                atualizarMovimentoNPC(data);
            }
        });

        socket.on("player_left", (nomeEvt, data) -> {
            if (data == null) return;
            remotos.remove(data.getString("name", ""));
            skinsJogadores.remove(data.getString("name", ""));
        });

        // Skins: confirmacao das minhas (depois do Equip na aba Vanity) e
        // troca de skin de outro jogador da area.
        socket.on("skins_synced", (nomeEvt, data) -> {
            if (data == null) return;
            definirSkins(local.nome, data.get("skins"));
            bookMenu.atualizarSkinsEquipadas(data.get("skins"));
        });
        socket.on("player_skins_updated", (nomeEvt, data) -> {
            if (data == null) return;
            definirSkins(data.getString("name", ""), data.get("skins"));
        });

        socket.on("position_saved", (nomeEvt, data) -> finalizarSaidaAposSalvar());

        socket.on("inventory_synced", (nomeEvt, data) -> {
            if (data == null) return;
            bookMenu.atualizarInventario(data.get("inventory"));
            bookMenu.atualizarEquipados(data.get("equipped_items"));
        });

        socket.on("loot_collected", (nomeEvt, data) -> {
            if (data == null) return;
            bookMenu.atualizarMoedas(data.getLong("currency_total", 0L));
            bookMenu.adicionarItens(data.get("items"));
            if (data.getBoolean("bag_esvaziada", false) || data.getBoolean("already_taken", false)) {
                removerBag(data.getString("loot_id", ""));
            }
        });

        socket.on("sync_stats", (nomeEvt, data) -> {
            if (data == null) return;
            bookMenu.atualizarCapacidade(data.getFloat("cap_atual", 0f), data.getFloat("cap_maximo", 100f));
            bookMenu.atualizarSkills(data.get("skills"), data.getInt("level", 1), data.getInt("exp", 0), data.getInt("kills", 0));
            hud.definir(data.getFloat("current_hp", -1f), data.getFloat("max_hp", -1f),
                data.getFloat("current_mp", -1f), data.getFloat("max_mp", -1f));
        });

        socket.on("trade_executed", (nomeEvt, data) -> {
            if (data != null) bookMenu.atualizarMoedas(data.getLong("new_currency", 0L));
        });

        socket.on("m", (nomeEvt, data) -> {
            if (data == null || !data.isArray() || data.size < 4) return;
            Jogador j = remotos.get(data.get(0).asString());
            if (j != null) {
                float mx = conversor.rawParaMundoX(data.get(1).asFloat());
                float my = conversor.rawParaMundoY(data.get(2).asFloat());
                j.definirAlvo(mx, my, direcaoDoInt(data.get(3).asInt()));
            }
        });

        // Igual network_manager.gd::_registrar_mapa_no_servidor() - o servidor
        // roda a IA dos mobs/NPCs e precisa da grade de colisao do mapa; so'
        // pede a grade pesada (map_grid) se nao tiver nenhuma versao
        // cacheada pra esse "fp" ainda (ver servidor.py::handle_register_map).
        socket.on("need_map_grid", (nomeEvt, data) -> enviarGradeDoMapaPeloServidor());
    }

    public void definirCallbackSincronizacaoInicial(Runnable callback) {
        callbackSincronizacaoInicial = callback;
    }

    // MAP_ID_SERVIDOR precisa ser EXATAMENTE o mesmo id que o Godot manda
    // hoje (res://Inverted Realms.scn) - e' a chave que o servidor usa pra
    // associar mobs/NPCs configurados a esse mapa (spawn_do_mob_id em
    // servidor.py); um id novo faria a IA simplesmente nao encontrar
    // nenhum mob pra esse mapa. O World.tmx novo e' tratado como "a mesma
    // sala" pro servidor, mesmo sendo uma colisao recalculada do .tsx.
    private static final String MAP_ID_SERVIDOR = "res://Inverted Realms.scn";

    /** Registro leve (so' fingerprint) - o servidor responde "need_map_grid"
     * se nao tiver (ou tiver uma versao antiga d)a grade de colisao desse
     * mapa cacheada ainda. */
    private void registrarMapaNoServidor() {
        String payload = GameSocket.obj(w -> {
            w.set("map", MAP_ID_SERVIDOR);
            w.set("fp", colisao.fingerprint());
            w.array("mobs");
            for (MapaPropriedades.MobSpawn spawn : mapa.propriedades.mobSpawns) {
                w.object();
                w.set("id", idDoMob(spawn));
                w.set("type", spawn.mobId);
                w.set("spawn_range", spawn.spawnRange);
                w.set("respawn_time", spawn.respawnTime);
                w.pop();
            }
            w.pop();
            w.array("npcs");
            for (MapaPropriedades.NPCSpawn npc : mapa.propriedades.npcSpawns) {
                w.object();
                w.set("id", npc.spawnKey);
                w.set("npc_id", npc.npcId);
                w.set("x", conversor.mundoParaRawX(npc.worldX));
                w.set("y", conversor.mundoParaRawY(npc.worldY));
                w.set("floor", 1);
                w.pop();
            }
            w.pop();
        });
        socket.emitRaw("register_map", payload);
    }

    private void sincronizarNPCs(JsonValue data) {
        if (data == null || !data.has("npcs") || !data.get("npcs").isArray()) return;
        for (JsonValue entrada = data.get("npcs").child; entrada != null; entrada = entrada.next) {
            adicionarOuAtualizarNPC(entrada);
        }
    }

    private void adicionarOuAtualizarNPC(JsonValue data) {
        String id = data.getString("id", "");
        String tipo = data.getString("npc_id", "");
        if (id.isEmpty() || tipo.isEmpty()) return;
        float x = conversor.rawParaMundoX(data.getFloat("x", -1f));
        float y = conversor.rawParaMundoY(data.getFloat("y", -1f));
        NPCVisual existente = npcs.get(id);
        if (existente != null) {
            existente.movimento.x = x;
            existente.movimento.y = y;
            existente.movimento.direcao = data.getString("direction", "down");
            existente.movimento.movendo = false;
            return;
        }
        String nomeSprite = Character.toUpperCase(tipo.charAt(0)) + tipo.substring(1).toLowerCase();
        TextureRegion sprite = atlas.findRegion("sprites/npcs/" + nomeSprite);
        if (sprite == null) {
            Gdx.app.error("WorldScreen", "Spritesheet de NPC nao encontrada: sprites/npcs/" + nomeSprite);
            return;
        }
        Jogador movimento = new Jogador(nomeSprite, "Knight", x, y);
        movimento.direcao = data.getString("direction", "down");
        npcs.put(id, new NPCVisual(id, tipo, nomeSprite, movimento, criarAnimacao(sprite)));
    }

    private void atualizarMovimentoNPC(JsonValue data) {
        if (data == null) return;
        String id = data.getString("id", "");
        NPCVisual npc = npcs.get(id);
        if (npc == null) {
            adicionarOuAtualizarNPC(data);
            npc = npcs.get(id);
        }
        if (npc == null) return;
        float x = conversor.rawParaMundoX(data.getFloat("x", -1f));
        float y = conversor.rawParaMundoY(data.getFloat("y", -1f));
        npc.movimento.definirAlvo(x, y, data.getString("direction", "down"));
    }

    /** Raio (em SQMs/tiles, ao quadrado) de interacao com NPCs - usado tanto
     * pra decidir quais NPCs podem ser abordados (E/clique) quanto pra
     * fechar o dialogo automaticamente se o jogador se afastar demais do
     * NPC com quem esta conversando. */
    private static final float ALCANCE_NPC_AO_QUADRADO = 3f * 3f;

    private int distanciaQuadradaAteNPC(NPCVisual npc) {
        int jogadorTileX = (int) Math.floor(local.x / Jogador.TILE);
        int jogadorTileY = (int) Math.floor((local.y - 1f) / Jogador.TILE);
        int npcTileX = (int) Math.floor(npc.movimento.x / Jogador.TILE);
        int npcTileY = (int) Math.floor((npc.movimento.y - 1f) / Jogador.TILE);
        int dx = npcTileX - jogadorTileX;
        int dy = npcTileY - jogadorTileY;
        return dx * dx + dy * dy;
    }

    private NPCVisual npcMaisProximoParaConversar() {
        NPCVisual maisProximo = null;
        float distanciaMinima = ALCANCE_NPC_AO_QUADRADO;
        for (NPCVisual npc : npcs.values()) {
            float distanciaQuadrada = distanciaQuadradaAteNPC(npc);
            if (distanciaQuadrada <= distanciaMinima) {
                distanciaMinima = distanciaQuadrada;
                maisProximo = npc;
            }
        }
        return maisProximo;
    }

    // "pedido" (icone Silver + "2 Silver Coins" em cinza, INLINE na mesma
    // linha do resto da fala - ver DialogoNPCUI.moeda()) reaproveitado nas
    // 2 situacoes abaixo.
    private static final String DIALOGO_NPC_PEDIDO =
        "Give me " + DialogoNPCUI.moeda("2 Silver Coins") + " to sail by the worlds";
    // 1a vez: discurso completo (2 paginas - Next na 1a, Close na 2a).
    // Depois de visto (ver npcDialogoVisto): so' o "pedido"/ultima pagina,
    // direto - a pedido do usuario ("ele sempre fala o ultimo dialogo").
    private static final String[] DIALOGO_NPC_INTRO = {
        "Hmm... by far an entity from the ancient realm that i was waiting for.",
        DIALOGO_NPC_PEDIDO
    };
    private static final String[] DIALOGO_NPC_LEMBRETE = { DIALOGO_NPC_PEDIDO };

    private void abrirDialogoNPC(NPCVisual npc) {
        if (chat.isVisivel()) chat.setVisivel(false);
        if (bookMenu.isVisible()) bookMenu.setVisible(false);
        if (settingsAberta()) fecharSettings();
        npcEmDialogo = npc;
        TextureRegion retrato = new TextureRegion(
            npc.animacao.idleBaixo.getTexture(),
            npc.animacao.idleBaixo.getRegionX(),
            npc.animacao.idleBaixo.getRegionY(),
            FRAME_LARGURA,
            npc.animacao.idleBaixo.getRegionHeight());
        String[] paginas = npcDialogoVisto.contains(npc.tipo)
            ? DIALOGO_NPC_LEMBRETE
            : DIALOGO_NPC_INTRO;
        dialogoNPC.abrir(npc.nome, retrato, paginas,
            () -> {
                // So' marca como "visto" se o fechamento aconteceu DEPOIS
                // de chegar na ultima pagina - fechar no meio (ESC na 1a
                // pagina, por ex) continua mostrando o discurso completo da
                // proxima vez.
                if (dialogoNPC.estaNaUltimaPagina()) marcarDialogoVisto(npc.tipo);
                npcEmDialogo = null;
                atualizarVisibilidadeJoystick();
            });
        atualizarVisibilidadeJoystick();
    }

    private void marcarDialogoVisto(String tipoNPC) {
        if (!npcDialogoVisto.add(tipoNPC)) return;
        String payload = GameSocket.obj(w -> w.set("npc_id", tipoNPC));
        socket.emitRaw("npc_dialogue_seen", payload);
    }

    /** Dispara o banner (ver AreaNomeUI) so' na TRANSICAO pra uma area
     * nomeada diferente da atual - cobre tanto andar pra dentro dela quanto
     * "relogar ja' dentro" (areaAtual comeca null, entao a 1a chamada apos
     * o spawn ja' conta como transicao se o spawn cair dentro de alguma
     * area, a pedido do usuario). Sair da area (novaArea == null) so' limpa
     * o estado, sem banner - andar de volta pra ela de novo depois RE-
     * dispara o banner (nao e' "so' uma vez pra sempre", e' por entrada). */
    private void atualizarAreaNomeada() {
        String novaArea = mapa.propriedades.areaEm(local.x, local.y);
        if (java.util.Objects.equals(novaArea, areaAtual)) return;
        areaAtual = novaArea;
        if (novaArea != null) areaNomeUI.mostrar(novaArea);
    }

    /** Fecha o dialogo sozinho se o jogador andar pra fora do raio de
     * interacao do NPC com quem esta conversando (a pedido do usuario: andar
     * e mexer em outras GUIs com o dialogo aberto agora e' permitido, mas
     * sair dos SQMs do NPC encerra a conversa). Chamado todo frame. */
    private void fecharDialogoNPCSeForaDeAlcance() {
        if (!dialogoNPC.isVisible() || npcEmDialogo == null) return;
        if (distanciaQuadradaAteNPC(npcEmDialogo) > ALCANCE_NPC_AO_QUADRADO) {
            dialogoNPC.fechar();
        }
    }

    private void desenharBalaoInteracaoNPC(NPCVisual npc) {
        if (npc == null || notifChat == null) return;
        float ancoraX = Math.round(npc.movimento.x / camera.zoom) * camera.zoom;
        float ancoraY = Math.round(npc.movimento.y / camera.zoom) * camera.zoom;
        batch.draw(notifChat, ancoraX + 2f, ancoraY + 9.5f,
            notifChat.getRegionWidth(), notifChat.getRegionHeight());
    }

    /** Grade de colisao completa (32px/SQM, mesmo formato que
     * servidor.py::handle_map_grid espera) - so' mandada quando o servidor
     * pede via "need_map_grid". */
    private void enviarGradeDoMapaPeloServidor() {
        String payload = GameSocket.obj(w -> {
            w.set("map", MAP_ID_SERVIDOR);
            w.set("fp", colisao.fingerprint());
            w.set("x0", colisao.x0());
            w.set("y0", colisao.y0());
            w.set("w", colisao.w());
            w.set("h", colisao.h());
            w.set("bits", colisao.bitsBase64());
            w.set("bits_leste", colisao.bitsLesteBase64());
            w.set("bits_baixo", colisao.bitsBaixoBase64());
        });
        socket.emitRaw("map_grid", payload);
    }

    private void adicionarRemotoSeNovo(JsonValue p) {
        if (p == null) return;
        String nome = p.getString("name", "");
        if (nome.isEmpty() || nome.equals(local.nome) || remotos.containsKey(nome)) return;
        String classe = p.has("class_name") ? p.getString("class_name") : "Knight";
        float rawX = p.getFloat("pos_x", -1f);
        float rawY = p.getFloat("pos_y", -1f);
        float mx = rawX != -1f ? conversor.rawParaMundoX(rawX) : spawnX;
        float my = rawY != -1f ? conversor.rawParaMundoY(rawY) : spawnY;
        remotos.put(nome, new Jogador(nome, classe, mx, my));
        definirSkins(nome, p.get("skins"));
    }

    /** Mantem o viewport da camera do tamanho real da tela (chamada todo
     * frame, mesma rechecagem defensiva que o FBO removido ja fazia pra
     * janela redimensionada/Android immersive mode) - zoom continua sendo
     * so' camera.zoom (ver NIVEIS_ZOOM), igual o Camera2D do Godot, sem
     * nenhum buffer intermediario. getBackBufferWidth/Height (NAO getWidth/
     * Height) pelo mesmo motivo documentado em construirUiSettings()/
     * resize(): no PC com escala de tela fracionaria (HiDPI), getWidth()
     * devolve unidades LOGICAS da janela, nao pixels reais do framebuffer. */
    private void atualizarCamera() {
        int larguraTela = Math.max(1, Gdx.graphics.getBackBufferWidth());
        int alturaTela = Math.max(1, Gdx.graphics.getBackBufferHeight());
        if (camera.viewportWidth != larguraTela || camera.viewportHeight != alturaTela) {
            camera.setToOrtho(false, larguraTela, alturaTela);
        }
    }

    @Override
    public void render(float delta) {
        atualizarCamera();
        // Atualiza ANTES de checar entrada (ordem importa): se checassemos
        // "!local.movendo" antes de atualizar(), estariamos lendo o estado do
        // frame ANTERIOR - o passo que termina durante este atualizar() só
        // seria notado no frame seguinte, perdendo 1 frame inteiro de folga a
        // cada SQM cruzado (a gagueira ao andar que o usuario reportou).
        // Reaplicar a sobra devolvida por atualizar() no mesmo frame (igual o
        // tween_callback do Godot, que encadeia o proximo passo de forma
        // sincrona no instante em que o anterior termina) fecha esse buraco.
        float sobraLocal = local.atualizar(delta);
        for (Jogador j : remotos.values()) j.atualizar(delta);
        for (NPCVisual npc : npcs.values()) npc.movimento.atualizar(delta);
        for (MobVisual mob : mobs.values()) mob.atualizar(delta);
        atualizarCombate(delta);
        for (int i = numerosDano.size() - 1; i >= 0; i--) {
            NumeroDano n = numerosDano.get(i);
            n.tempo += delta;
            if (n.tempo >= DURACAO_NUMERO_DANO) numerosDano.remove(i);
        }
        dialogoNPC.atualizar();
        atualizarAreaNomeada();

        // Direto por polling (nao via InputProcessor/keyDown) - ver
        // comentario no keyDown(ENTER) do construtor pra entender o motivo.
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) && chat.isVisivel() && !chat.estaDigitando()) {
            chat.focarCampoTexto();
        }

        // !chat.isVisivel() trava o movimento com a JANELA do chat aberta,
        // nao so' enquanto se digita nela (a pedido do usuario - abrir o chat
        // pelo espaco/botao, sem nem focar o campo de texto ainda, ja' deveria
        // travar, igual Settings ja' fazia).
        // Dialogo de NPC NAO trava mais o movimento (a pedido do usuario: da
        // pra andar e mexer em outras GUIs com ele aberto) - em vez disso,
        // fecharDialogoNPCSeForaDeAlcance() encerra a conversa sozinha se o
        // jogador sair dos SQMs de alcance do NPC (ver abaixo).
        if (!local.movendo && !localMorto && !settingsAberta() && !chat.isVisivel() && !bookMenu.isVisible()) {
            processarEntrada();
            if (local.movendo && sobraLocal > 0f) {
                local.atualizar(sobraLocal);
            }
        }
        fecharDialogoNPCSeForaDeAlcance();

        // "zoom" (campo) e' trocado pelo botao x1-x4 (ver NIVEIS_ZOOM) mas
        // so' escreve nesse campo, nao em camera.zoom diretamente - sincroniza
        // aqui, todo frame (igual Camera2D.zoom do Godot, sem buffer
        // intermediario nenhum pra "travar" esse valor em 1 como antes).
        camera.zoom = zoom;
        // Snap da posicao pro pixel de TELA mais proximo - generaliza pra
        // qualquer zoom porque camera.viewportWidth/Height ja' sao pixels
        // reais da tela (ver atualizarCamera()): dividir por zoom converte
        // unidade-de-mundo pra pixel-de-tela, arredonda, converte de volta.
        // Sem isso o sprite/nome tremia ao seguir local.x/y continuo (bug
        // antigo, ver historico) - equivalente ao snap_2d_vertices_to_pixel=
        // true do Godot (project.godot), so' que so' na ANCORA (pes do
        // personagem) em vez de cada vertice, suficiente pra sprite/nome nao
        // tremerem.
        //
        // local.x/y sao os PES do personagem (mesma ancora usada em
        // desenharJogador/colisao), nao o centro visual do sprite - centrar a
        // camera direto nisso deixa mais chao visivel ABAIXO do personagem
        // que ACIMA (o corpo inteiro "sobra" pra cima a partir da ancora,
        // sem nada equivalente sobrando pra baixo) - reportado pelo usuario
        // comparando contra o Mirage em SQMs de verdade (2 pra cima/3 pra
        // baixo aqui, Mirage bem simetrico). Corrigido subindo o ALVO da
        // camera (so' o alvo - a ancora do sprite/nome continua nos pes,
        // ver desenharJogador) por metade da altura do corpo, centralizando
        // o MEIO do personagem na tela em vez dos pes.
        //
        // IMPORTANTE: o offset e' somado DEPOIS de arredondar local.y, nao
        // antes (round(local.y/zoom)*zoom + offset, nao round((local.y+
        // offset)/zoom)*zoom) - as 2 formas dao quase sempre o mesmo
        // resultado, mas local.y continuo caindo perto de uma fronteira de
        // arredondamento as vezes faz local.y E (local.y+offset) ARREDONDAREM
        // PRA LADOS DIFERENTES (cada um pro multiplo de zoom mais proximo
        // DELE, que pode diferir em 1 passo) - a camera.position.y resultante
        // destoa da ancora que desenharJogador calcula pro proprio personagem
        // (que arredonda so' local.y, sem esse offset) em ±1 pixel de tela
        // nesses instantes, e como e' so' a CAMERA que destoa (nao o
        // personagem em si), o efeito e' o personagem/nome TREMEREM na tela
        // mesmo parados/andando reto - bug reportado pelo usuario (nome
        // tremendo sempre, boneco tremendo mais perceptivel no zoom mais
        // afastado, onde 1 pixel de tela e' uma fracao maior do sprite).
        // Arredondar local.y PRIMEIRO (igual desenharJogador) e so' DEPOIS
        // somar o offset (constante, nao introduz arredondamento novo)
        // garante que camera.position.y e a ancora do personagem andem
        // SEMPRE em sincrono, diferindo so' por essa constante fixa.
        // offsetCentralizacao tambem precisa ser arredondado pro multiplo de
        // zoom mais proximo antes de somar - sem isso camera.position.y sai
        // do GRID de zoom que o resto do mundo (tiles, sprites) usa (a
        // ancora do personagem fica em sincronia com a camera, ver acima,
        // mas os TILES do mapa nao sabem desse offset e continuam no grid
        // cru). Isso deixava a camera "entre pixels" em relacao ao mapa -
        // nao tremia (offset e' constante, nao muda frame a frame), mas
        // produzia costuras/serrilhado estatico no mundo inteiro, mais
        // visivel em zoom mais fino (reportado pelo usuario com print,
        // "Boneco.png" - medido pixel a pixel: ~18% das fileiras verticais
        // com largura fora do padrao, 0 nas horizontais, exatamente o perfil
        // de um offset so' no eixo Y quebrando o grid so' nesse eixo).
        float alturaPersonagem = animacaoBase.idleBaixo.getRegionHeight() * ESCALA_SPRITE;
        float offsetCentralizacao = Math.round((alturaPersonagem / 2f) / camera.zoom) * camera.zoom;
        camera.position.x = Math.round(local.x / camera.zoom) * camera.zoom;
        camera.position.y = Math.round(local.y / camera.zoom) * camera.zoom + offsetCentralizacao;
        camera.update();

        mapa.atualizar(delta);

        // 1. Renderiza o framebuffer da iluminação ANTES da cena real
        // O Godot usava light_offset Y de 22; com a escala pela metade, usamos 11.
        luzJogador.x = local.x;
        luzJogador.y = local.y + 11f;
        iluminacao.renderizar(batch, camera, todasAsLuzes);

        // Mundo inteiro desenhado DIRETO na tela real, num passo so' - igual
        // o Godot (Camera2D comum, sem viewport/buffer intermediario nenhum).
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        mapa.desenharMapa(camera);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        desenharJogador(local);
        for (Jogador j : remotos.values()) desenharJogador(j);
        for (NPCVisual npc : npcs.values()) desenharNPC(npc);
        // Cadaveres primeiro (ficam por baixo dos mobs vivos), bags por cima deles.
        for (MobVisual mob : mobs.values()) if (mob.morto) desenharMob(mob);
        desenharBags();
        for (MobVisual mob : mobs.values()) if (!mob.morto) desenharMob(mob);
        desenharEfeitos();
        if (spawnSmokeTempo >= 0f) {
            spawnSmokeTempo += delta;
            if (!spawnSmokeAnim.isAnimationFinished(spawnSmokeTempo)) {
                TextureRegion quadro = spawnSmokeAnim.getKeyFrame(spawnSmokeTempo);
                float tamanho = 16f * ESCALA_SPRITE;
                batch.draw(quadro, spawnSmokeX - tamanho / 2f, spawnSmokeY, tamanho, tamanho);
            } else {
                spawnSmokeTempo = -1f;
            }
        }
        if (mostrarColisao) desenharOverlayColisao();
        // Celulas overlap_transparency=true por cima do player (a pedido do
        // usuario: player sempre acima de QUALQUER tile, exceto os marcados
        // overlay - esses ficam na frente, e so' a celula exata que o
        // jogador esta ocupando fica semi-transparente). Mesmo batch (sem
        // precisar do renderer/batch interno do OrthogonalTiledMapRenderer,
        // ja que essas celulas foram removidas das layers normais).
        mapa.desenharOverlays(batch, local.x, local.y);
        batch.end();

        // Roofs/Pillars (camadas INTEIRAS, nao celula por celula) tambem na
        // frente do player - usa o renderer/batch interno do
        // OrthogonalTiledMapRenderer (igual desenharMapa), por isso fora do
        // batch.begin()/end() do WorldScreen.
        mapa.desenharTelhados(camera, local.x, local.y);

        // Nomes e baloes dos jogadores e NPCs recebem a mesma iluminacao.
        batch.begin();
        desenharNome(local);
        for (Jogador j : remotos.values()) desenharNome(j);
        for (NPCVisual npc : npcs.values()) desenharNomeNPC(npc);
        for (MobVisual mob : mobs.values()) desenharNomeMob(mob);
        desenharNumerosDano();
        desenharBalaoInteracaoNPC(npcMaisProximoParaConversar());
        TextureRegion notifAtual = notificacaoAtual();
        if (notifAtual != null) {
            float nx = local.x + 2f;
            float ny = local.y + 9.5f;
            batch.draw(notifAtual, nx, ny, notifAtual.getRegionWidth(), notifAtual.getRegionHeight());
        }
        batch.end();

        // 2. Aplica a iluminação multiplicativa SOBRE a cena renderizada
        // A iluminação vai escurecer os roofs, pillars, tiles do mapa e os jogadores
        iluminacao.compositar(batch, camera);

        labelFps.setText(Gdx.graphics.getFramesPerSecond() + " fps");
        acumuladorMs += delta;
        if (acumuladorMs >= INTERVALO_LEITURA_MS) {
            acumuladorMs = 0f;
            int ms = Math.round(delta * 1000f);
            labelMs.setText(ms + " ms");
            Color cor;
            if (ms <= 17) cor = COR_MS_BOA;
            else if (ms <= 25) cor = COR_MS_OK;
            else if (ms <= 40) cor = COR_MS_RUIM;
            else cor = COR_MS_PESSIMA;
            labelMs.setColor(cor);
        }

        if (chat.isVisivel()) {
            java.util.List<String> nomes = new java.util.ArrayList<>();
            nomes.add(nomeVisivel(local.nome));
            for (String nome : remotos.keySet()) nomes.add(nomeVisivel(nome));
            chat.atualizarJogadores(nomes);
        }
        atualizarVisibilidadeJoystick();
        atualizarVisibilidadeBotoesTopo();

        uiStage.act(delta);
        uiStage.draw();
    }

    /** Debug (tecla C): pinta de vermelho translucido cada SQM que ColisaoGrid
     * considera parede, dentro do que a camera esta vendo agora - compara
     * visualmente contra o mapa de verdade pra achar colisao errada/faltando. */
    private void desenharOverlayColisao() {
        float meiaLarguraMundo = camera.viewportWidth * camera.zoom / 2f;
        float meiaAlturaMundo = camera.viewportHeight * camera.zoom / 2f;
        float mundoEsq = camera.position.x - meiaLarguraMundo;
        float mundoDir = camera.position.x + meiaLarguraMundo;
        float mundoBaixo = camera.position.y - meiaAlturaMundo;
        float mundoCima = camera.position.y + meiaAlturaMundo;

        float rawEsq = conversor.mundoParaRawX(mundoEsq);
        float rawDir = conversor.mundoParaRawX(mundoDir);
        float rawCima = conversor.mundoParaRawY(mundoCima); // menor rawY (Y cresce pra baixo no Godot)
        float rawBaixo = conversor.mundoParaRawY(mundoBaixo);

        int sqmXIni = (int) Math.floor(Math.min(rawEsq, rawDir) / Jogador.TILE) - 1;
        int sqmXFim = (int) Math.floor(Math.max(rawEsq, rawDir) / Jogador.TILE) + 1;
        int sqmYIni = (int) Math.floor(rawCima / Jogador.TILE) - 1;
        int sqmYFim = (int) Math.floor(rawBaixo / Jogador.TILE) + 1;

        // Bordas finas (leste/baixo) desenhadas por cima, em amarelo - linha
        // fina bem em cima do limite entre 2 SQMs, pra distinguir de celula
        // inteira solida (vermelho) - ver ColisaoGrid::movimentoBloqueado.
        float espessuraBorda = 3f;
        for (int sqmY = sqmYIni; sqmY <= sqmYFim; sqmY++) {
            for (int sqmX = sqmXIni; sqmX <= sqmXFim; sqmX++) {
                int ix = sqmX - colisao.x0(), iy = sqmY - colisao.y0();
                float wx0 = conversor.rawParaMundoX(sqmX * Jogador.TILE);
                float wx1 = conversor.rawParaMundoX(sqmX * Jogador.TILE + Jogador.TILE);
                float wyTop = conversor.rawParaMundoY(sqmY * Jogador.TILE);
                float wyBottom = conversor.rawParaMundoY(sqmY * Jogador.TILE + Jogador.TILE);
                if (colisao.ehParedeIndice(ix, iy)) {
                    batch.setColor(1f, 0f, 0f, 0.4f);
                    batch.draw(pixelColisao, Math.min(wx0, wx1), Math.min(wyTop, wyBottom),
                        Math.abs(wx1 - wx0), Math.abs(wyTop - wyBottom));
                }
                if (colisao.bordaLesteIndice(ix, iy)) {
                    batch.setColor(1f, 1f, 0f, 0.9f);
                    batch.draw(pixelColisao, wx1 - espessuraBorda / 2f, Math.min(wyTop, wyBottom),
                        espessuraBorda, Math.abs(wyTop - wyBottom));
                }
                if (colisao.bordaBaixoIndice(ix, iy)) {
                    batch.setColor(1f, 1f, 0f, 0.9f);
                    batch.draw(pixelColisao, Math.min(wx0, wx1), wyBottom - espessuraBorda / 2f,
                        Math.abs(wx1 - wx0), espessuraBorda);
                }
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void processarEntrada() {
        String direcao = null;
        float dx = 0, dy = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W) || joystick.isCima()) {
            direcao = "up"; dy = Jogador.TILE;
        } else if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S) || joystick.isBaixo()) {
            direcao = "down"; dy = -Jogador.TILE;
        } else if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A) || joystick.isEsquerda()) {
            direcao = "left"; dx = -Jogador.TILE;
        } else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D) || joystick.isDireita()) {
            direcao = "right"; dx = Jogador.TILE;
        }
        if (direcao == null) return;

        float alvoX = local.x + dx;
        float alvoY = local.y + dy;
        if (!colisao.ehParede(alvoX, alvoY)
            && !colisao.movimentoBloqueado(local.x, local.y, alvoX, alvoY)
            && !npcOcupaTile(alvoX, alvoY)) {
            local.iniciarPasso(dx, dy, direcao);
            enviarMove(alvoX, alvoY, direcao);
        } else {
            local.direcao = direcao;
        }
    }

    private boolean npcOcupaTile(float mundoX, float mundoY) {
        int tileX = (int) Math.floor(mundoX / Jogador.TILE);
        int tileY = (int) Math.floor((mundoY - 1f) / Jogador.TILE);
        for (NPCVisual npc : npcs.values()) {
            int npcTileX = (int) Math.floor(npc.movimento.x / Jogador.TILE);
            int npcTileY = (int) Math.floor((npc.movimento.y - 1f) / Jogador.TILE);
            if (tileX == npcTileX && tileY == npcTileY) return true;
        }
        return false;
    }

    private void enviarMove(float mundoX, float mundoY, String direcao) {
        if (!socket.isConnected()) return;
        float rawX = conversor.mundoParaRawX(mundoX);
        float rawY = conversor.mundoParaRawY(mundoY);
        int dirInt = intDaDirecao(direcao);
        StringWriter sw = new StringWriter();
        JsonWriter jw = new JsonWriter(sw);
        try {
            jw.array();
            jw.value(local.nome);
            jw.value(rawX);
            jw.value(rawY);
            jw.value(dirInt);
            jw.pop();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        socket.emitRaw("m", sw.toString());
    }

    private void salvarPosicaoESair() {
        if (saidaIniciada) return;
        saidaIniciada = true;
        if (!socket.isConnected()) {
            finalizarSaidaAposSalvar();
            return;
        }
        float rawX = conversor.mundoParaRawX(local.posicaoSalvarX());
        float rawY = conversor.mundoParaRawY(local.posicaoSalvarY());
        String payload = GameSocket.obj(w -> {
            w.set("x", rawX);
            w.set("y", rawY);
            w.set("direction", local.direcao);
        });
        socket.emitRaw("save_position", payload);
        timeoutSalvarPosicao = com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task() {
            @Override public void run() { finalizarSaidaAposSalvar(); }
        }, 2f);
    }

    private void finalizarSaidaAposSalvar() {
        if (!saidaIniciada || saidaConcluida) return;
        saidaConcluida = true;
        if (timeoutSalvarPosicao != null) timeoutSalvarPosicao.cancel();
        aoSair.run();
    }

    private static int intDaDirecao(String direcao) {
        switch (direcao) {
            case "up": return 1;
            case "left": return 2;
            case "right": return 3;
            default: return 0; // down
        }
    }

    private static String direcaoDoInt(int valor) {
        switch (valor) {
            case 1: return "up";
            case 2: return "left";
            case 3: return "right";
            default: return "down";
        }
    }

    /** Recorta 1 quadro nativo do spritesheet (16px de largura, altura toda). */
    private TextureRegion regiao(TextureRegion tex, int indice) {
        return new TextureRegion(tex, indice * FRAME_LARGURA, 0, FRAME_LARGURA, tex.getRegionHeight());
    }

    private TextureRegion[] regioes(TextureRegion tex, int[] indices) {
        TextureRegion[] out = new TextureRegion[indices.length];
        for (int i = 0; i < indices.length; i++) out[i] = regiao(tex, indices[i]);
        return out;
    }

    private AnimacaoCorpo criarAnimacao(TextureRegion tex) {
        AnimacaoCorpo a = new AnimacaoCorpo();
        a.idleCima = regiao(tex, FRAME_IDLE_UP);
        a.idleBaixo = regiao(tex, FRAME_IDLE_DOWN);
        a.idleEsquerda = regiao(tex, FRAME_IDLE_ESQUERDA);
        a.idleDireita = regiao(tex, FRAME_IDLE_DIREITA);
        a.andarCima = regioes(tex, FRAME_ANDAR_CIMA);
        a.andarBaixo = regioes(tex, FRAME_ANDAR_BAIXO);
        a.andarEsquerda = regioes(tex, FRAME_ANDAR_ESQUERDA);
        a.andarDireita = regioes(tex, FRAME_ANDAR_DIREITA);
        return a;
    }

    /** Escolhe o quadro certo (parado/andando x 4 direcoes) pro instante
     * atual. "Andando" usa a FRACAO do passo atual (Jogador::progresso), nao
     * um relogio proprio desligado do movimento - e' a mesma ideia de
     * player.gd::_move_in_direction, que toca a walk_* animation com
     * anim_speed_scale = anim_length/step_time (escala o ciclo inteiro pra
     * caber exatamente em 1 passo) e sempre reinicia do frame 0 a cada passo
     * (seek(0.0, true)). Um relogio continuo independente do progresso do
     * passo desalinha frame e movimento (frame muda em momentos que nao
     * batem com o cruzar de SQM) e pode "vazar" quadros de andar por cima do
     * instante de parada - com o indice preso ao progresso do passo, virar
     * "parado" e' sempre um corte limpo pro quadro idle, nunca um quadro de
     * andar fora de hora. */
    private TextureRegion quadroAtual(AnimacaoCorpo a, Jogador j) {
        return quadroAtual(a, j.movendo, j.direcao, j.progresso());
    }

    private TextureRegion quadroAtual(AnimacaoCorpo a, boolean movendo, String direcao, float progresso) {
        if (!movendo) {
            switch (direcao) {
                case "up": return a.idleCima;
                case "left": return a.idleEsquerda;
                case "right": return a.idleDireita;
                default: return a.idleBaixo;
            }
        }
        int indiceFrame = Math.min(1, (int) (progresso * 2f));
        switch (direcao) {
            case "up": return a.andarCima[indiceFrame];
            case "left": return a.andarEsquerda[indiceFrame];
            case "right": return a.andarDireita[indiceFrame];
            default: return a.andarBaixo[indiceFrame];
        }
    }

    // (j.x, j.y) e a ancora do personagem no chao (pes) - mesma convencao do
    // Godot (player.gd::snap_to_tile_center ancora Y no fundo do tile, nao no
    // centro) - por isso o sprite cresce PRA CIMA a partir daqui, nunca
    // centralizado, senao ele fica visualmente "no meio de 2 SQMs".
    private void desenharJogador(Jogador j) {
        TextureRegion quadroBase = quadroAtual(animacaoBase, j);
        float largura = FRAME_LARGURA * ESCALA_SPRITE;
        float altura = quadroBase.getRegionHeight() * ESCALA_SPRITE;
        // Ancora (pes do jogador) snapada pra grade da camera PRIMEIRO, antes
        // de desenhar OU somar qualquer offset (meia largura do sprite/texto,
        // altura etc) - precisa valer tanto pro sprite do personagem quanto
        // pro nome, senao so' o nome fica estavel e o boneco volta a tremer
        // sozinho (bug reportado pelo usuario depois do zoom ter ido pra
        // 0.5x - mais "zoom-in" torna qualquer sub-pixel de posicao continua
        // bem mais visivel na tela). A 1a versao deste fix so' arredondava
        // pro nome e so' DEPOIS de somar o offset (Math.round(j.x -
        // layout.width/2f, ...)), o que so' corrigia o eixo onde o offset
        // somado era um numero inteiro por coincidencia (altura+14 sempre e',
        // ja que "altura" vem de regiao em pixel inteiro - por isso o eixo Y
        // parecia resolvido enquanto X, com o offset fracionario de
        // layout.width/2f, continuava tremendo so' ao andar de lado).
        // Arredondando a ancora ANTES (valor que so' muda em saltos discretos
        // ao cruzar de SQM, nunca durante o proprio passo) e so' depois
        // somando qualquer offset (constante dentro do passo, nao reintroduz
        // variacao continua), sprite e nome ficam igualmente estaveis nos 2
        // eixos.
        float ancoraX = Math.round(j.x / camera.zoom) * camera.zoom;
        float ancoraY = Math.round(j.y / camera.zoom) * camera.zoom;
        float x = ancoraX - largura / 2f;
        List<CamadaSkin> camadas = skinsJogadores.get(j.nome);
        if (camadas == null || camadas.isEmpty()) {
            batch.draw(quadroBase, x, ancoraY, largura, altura);
            return;
        }
        // Camadas de skin (base, roupa, cabeca, acessorio) uma por cima da
        // outra, cada uma com a cor escolhida na aba Vanity.
        for (CamadaSkin camada : camadas) {
            TextureRegion quadro = quadroAtual(camada.animacao, j);
            batch.setColor(camada.cor);
            batch.draw(quadro, x, ancoraY, largura, quadro.getRegionHeight() * ESCALA_SPRITE);
        }
        batch.setColor(Color.WHITE);
    }

    /** Monta as camadas de skin de um jogador a partir do formato do servidor
     * ({categoria: {caminho, cor}}, ver servidor.py::validar_skins). */
    private void definirSkins(String nome, JsonValue skins) {
        if (nome == null || nome.isEmpty()) return;
        List<CamadaSkin> camadas = new ArrayList<>();
        for (String cat : SkinsUtil.ORDEM_CAMADAS) {
            String caminho = SkinsUtil.caminho(skins, cat);
            if (caminho == null && "base".equals(cat)) caminho = SkinsUtil.BASE_PADRAO;
            TextureRegion tira = SkinsUtil.regiao(atlas, caminho);
            if (tira == null) continue;
            AnimacaoCorpo anim = cacheAnimacoesSkin.get(caminho);
            if (anim == null) {
                anim = criarAnimacao(tira);
                cacheAnimacoesSkin.put(caminho, anim);
            }
            camadas.add(new CamadaSkin(anim, SkinsUtil.cor(SkinsUtil.corHex(skins, cat))));
        }
        skinsJogadores.put(nome, camadas);
    }

    private void desenharNPC(NPCVisual npc) {
        TextureRegion quadro = quadroAtual(npc.animacao, npc.movimento);
        float largura = FRAME_LARGURA * ESCALA_SPRITE;
        float altura = quadro.getRegionHeight() * ESCALA_SPRITE;
        float ancoraX = Math.round(npc.movimento.x / camera.zoom) * camera.zoom;
        float ancoraY = Math.round(npc.movimento.y / camera.zoom) * camera.zoom;
        batch.draw(quadro, ancoraX - largura / 2f, ancoraY, largura, altura);
    }

    /** Id que o servidor usa pro mob: "<mob_id>_<x cru>_<y cru>" do ponto do
     * Tiled (servidor.py::spawn_do_mob_id tira o SQM de spawn daqui). */
    private String idDoMob(MapaPropriedades.MobSpawn spawn) {
        return spawn.mobId + "_" + Math.round(conversor.mundoParaRawX(spawn.worldX))
            + "_" + Math.round(conversor.mundoParaRawY(spawn.worldY));
    }

    private void criarMobsDoMapa() {
        for (MapaPropriedades.MobSpawn spawn : mapa.propriedades.mobSpawns) {
            TextureRegion sprite = spriteDoMob(spawn.mobId);
            if (sprite == null) {
                Gdx.app.error("WorldScreen", "Spritesheet de mob nao encontrada: sprites/mobs/" + spawn.mobId);
                continue;
            }
            String id = idDoMob(spawn);
            String nome = Character.toUpperCase(spawn.mobId.charAt(0)) + spawn.mobId.substring(1);
            mobs.put(id, new MobVisual(id, nome, spawn.worldX, spawn.worldY, criarAnimacao(sprite), regiao(sprite, FRAME_MORTE)));
        }
    }

    /** sprites/mobs/<nome> - aceita o nome como esta no atlas (ex: "Rotworm")
     * ou todo minusculo ("rat"), ja' que o mob_id do Tiled vem minusculo. */
    private TextureRegion spriteDoMob(String mobId) {
        TextureRegion sprite = atlas.findRegion("sprites/mobs/" + mobId);
        if (sprite == null && !mobId.isEmpty()) {
            sprite = atlas.findRegion("sprites/mobs/" + Character.toUpperCase(mobId.charAt(0)) + mobId.substring(1));
        }
        return sprite;
    }

    /** posicaoAbsoluta: sync/morte (vai direto); senao e' um passo do mob_pos. */
    private void moverMob(MobVisual mob, float rawX, float rawY, String direcao, float duracaoPasso, boolean posicaoAbsoluta) {
        float mx = conversor.rawParaMundoX(rawX);
        float my = conversor.rawParaMundoY(rawY);
        if (posicaoAbsoluta || !mob.visivel) {
            mob.posicionar(mx, my, direcao);
        } else {
            mob.receberPasso(mx, my, direcao, duracaoPasso);
        }
        mob.visivel = true;
    }

    /** Mesmo alinhamento do player (desenharJogador): X centralizado no SQM,
     * pes no fundo do SQM, ancora snapada pra grade da camera antes de somar
     * qualquer offset. */
    private void desenharMob(MobVisual mob) {
        if (!mob.visivel || (mob.morto && mob.tempoCorpo <= 0f)) return;
        TextureRegion quadro = mob.morto ? mob.quadroMorto
            : quadroAtual(mob.animacao, mob.andandoVisual(), mob.direcao, mob.progresso);
        float largura = FRAME_LARGURA * ESCALA_SPRITE;
        float altura = quadro.getRegionHeight() * ESCALA_SPRITE;
        float ancoraX = Math.round(mob.x / camera.zoom) * camera.zoom;
        float ancoraY = Math.round(mob.y / camera.zoom) * camera.zoom;
        float x = ancoraX - largura / 2f;
        // Quadrado de alvo (ui/slots/Target, 18x18) em volta do SQM do mob mirado.
        if (!mob.morto && mob.id.equals(alvoMob) && regiaoAlvo != null) {
            batch.draw(regiaoAlvo, ancoraX - regiaoAlvo.getRegionWidth() / 2f, ancoraY - 1f);
        }
        batch.draw(quadro, x, ancoraY, largura, altura);
        if (!mob.morto && mob.tempoTargetHit > 0f && regiaoTargetHit != null) {
            batch.draw(regiaoTargetHit, ancoraX - regiaoTargetHit.getRegionWidth() / 2f, ancoraY);
        }
        // Bandeira enquanto volta pra casa (desistiu do alvo).
        if (!mob.morto && mob.voltando && regiaoFlag != null) {
            batch.draw(regiaoFlag, ancoraX + 2f, ancoraY + altura - 2f, 8f, 8f);
        }
        // Barra de vida (logo acima da cabeca; o nome vai por cima dela).
        if (!mob.morto) {
            float barraLargura = 14f;
            float barraX = ancoraX - barraLargura / 2f;
            float barraY = ancoraY + altura + 1f;
            float pct = Math.max(0f, Math.min(1f, mob.hp / mob.maxHp));
            batch.setColor(0f, 0f, 0f, 0.8f);
            batch.draw(pixelBranco, barraX - 0.5f, barraY - 0.5f, barraLargura + 1f, 3f);
            batch.setColor(corDaVida(pct));
            batch.draw(pixelBranco, barraX, barraY, barraLargura * pct, 2f);
            batch.setColor(Color.WHITE);
        }
    }

    // ===================== COMBATE =====================

    private Jogador jogadorPorNome(String nome) {
        if (nome == null || nome.isEmpty()) return null;
        if (nome.equals(local.nome)) return local;
        return remotos.get(nome);
    }

    /** "res://ui/items/Arrow1.png" -> regiao "ui/items/Arrow1" do atlas. */
    private TextureRegion regiaoDoCaminho(String caminho) {
        if (caminho == null || caminho.isEmpty()) return null;
        String nome = caminho.startsWith("res://") ? caminho.substring(6) : caminho;
        int ponto = nome.lastIndexOf('.');
        if (ponto > nome.lastIndexOf('/')) nome = nome.substring(0, ponto);
        return atlas.findRegion(nome);
    }

    /** Efeito de hit pelo nome que vai no hit_type (o mesmo pra todo client):
     * Sword/Mana/Music1/Arrow (armas das classes) ou Physical (padrao, mob). */
    private TextureRegion[] quadrosEfeito(String nome) {
        String chave = nome == null ? "Physical" : nome;
        TextureRegion[] quadros = cacheEfeitos.get(chave);
        if (quadros != null) return quadros;
        TextureRegion tira;
        switch (chave) {
            case "Sword": tira = atlas.findRegion("ui/items/Sword"); break;
            case "Mana": tira = atlas.findRegion("ui/Mana"); break;
            case "Music1": tira = atlas.findRegion("ui/items/Music1"); break;
            case "Arrow": tira = atlas.findRegion("ui/items/Arrow"); break;
            default: tira = atlas.findRegion("ui/items/Physical"); break;
        }
        if (tira == null) tira = atlas.findRegion("ui/items/Physical");
        if (tira == null) return null;
        int n = Math.max(1, tira.getRegionWidth() / 16);
        quadros = new TextureRegion[n];
        for (int i = 0; i < n; i++) quadros[i] = new TextureRegion(tira, i * 16, 0, 16, tira.getRegionHeight());
        cacheEfeitos.put(chave, quadros);
        return quadros;
    }

    private TextureRegion[] quadrosFumaca() {
        TextureRegion[] quadros = cacheEfeitos.get("__fumaca");
        if (quadros == null) {
            quadros = new TextureRegion[7];
            for (int i = 0; i < 7; i++) quadros[i] = new TextureRegion(spawnSmokeTex, i * 16, 0, 16, 16);
            cacheEfeitos.put("__fumaca", quadros);
        }
        return quadros;
    }

    private void tocarEfeito(String nome, float x, float y) {
        TextureRegion[] quadros = quadrosEfeito(nome);
        if (quadros != null) efeitos.add(new Efeito(quadros, x, y));
    }

    /** Efeito e projetil de cada classe (o servidor repassa pros outros clients). */
    private String efeitoDaClasse() {
        switch (local.classe) {
            case "Mage": return "Mana";
            case "Bard": return "Music1";
            case "Ranger": return "Arrow";
            default: return "Sword";
        }
    }

    private String projetilDaClasse() {
        switch (local.classe) {
            case "Mage": return "res://ui/items/Mana_2.png";
            case "Bard": return "res://ui/items/Music1_1.png";
            case "Ranger": return "res://ui/items/Arrow1.png";
            default: return "";
        }
    }

    private boolean classeRanged() {
        return !"Knight".equals(local.classe);
    }

    private static int tileX(float mundoX) { return (int) Math.floor(mundoX / Jogador.TILE); }
    private static int tileY(float mundoY) { return Math.round(mundoY / Jogador.TILE); }

    /** Distancia em SQMs (diagonal conta 1, igual o servidor). */
    private static int distanciaSqm(float x0, float y0, float x1, float y1) {
        return Math.max(Math.abs(tileX(x0) - tileX(x1)), Math.abs(tileY(y0) - tileY(y1)));
    }

    /** Clique num mob vivo: mira nele (ou tira a mira se ja era ele). */
    private boolean cliqueEmMob(float wx, float wy) {
        for (MobVisual mob : mobs.values()) {
            if (mob.morto || !mob.visivel) continue;
            float largura = FRAME_LARGURA * ESCALA_SPRITE;
            float altura = mob.animacao.idleBaixo.getRegionHeight() * ESCALA_SPRITE;
            if (wx >= mob.x - largura / 2f && wx <= mob.x + largura / 2f && wy >= mob.y && wy <= mob.y + altura) {
                alvoMob = mob.id.equals(alvoMob) ? null : mob.id;
                esperaAtaque = Math.min(esperaAtaque, 0.1f);
                return true;
            }
        }
        return false;
    }

    /** Clique numa bag do lado (ou embaixo) do player: pede o conteudo. */
    private boolean cliqueEmBag(float wx, float wy) {
        for (BagChao bag : bags.values()) {
            if (Math.abs(wx - bag.x) > 8f || wy < bag.y || wy > bag.y + 16f) continue;
            if (distanciaSqm(local.x, local.y, bag.x, bag.y) > 1) return true; // longe: so' consome o clique
            String id = bag.id;
            socket.emitRaw("request_loot", GameSocket.obj(jw -> jw.set("loot_id", id)));
            return true;
        }
        return false;
    }

    private void pegarLoot(String lootId) {
        socket.emitRaw("collect_loot", GameSocket.obj(jw -> jw.set("loot_id", lootId)));
    }

    private void removerBag(String lootId) {
        bags.remove(lootId);
        janelaLoot.bagRemovida(lootId);
    }

    private void atualizarCombate(float delta) {
        for (int i = efeitos.size() - 1; i >= 0; i--) {
            Efeito e = efeitos.get(i);
            e.tempo += delta;
            if (e.tempo >= e.quadros.length * DURACAO_QUADRO_EFEITO) efeitos.remove(i);
        }
        for (int i = projeteis.size() - 1; i >= 0; i--) {
            Projetil pr = projeteis.get(i);
            pr.tempo += delta;
            if (pr.tempo >= pr.duracao) {
                projeteis.remove(i);
                tocarEfeito(pr.efeitoHit, pr.x1, pr.y1 - 8f);
            }
        }
        java.util.Iterator<BagChao> it = bags.values().iterator();
        while (it.hasNext()) {
            BagChao bag = it.next();
            bag.restante -= delta;
            if (bag.restante <= 0f) {
                it.remove();
                janelaLoot.bagRemovida(bag.id);
            }
        }

        // Ataque automatico no mob mirado.
        esperaAtaque -= delta;
        if (alvoMob == null) return;
        MobVisual alvo = mobs.get(alvoMob);
        if (alvo == null || alvo.morto || !alvo.visivel) {
            alvoMob = null;
            return;
        }
        if (localMorto || esperaAtaque > 0f || !socket.isConnected()) return;
        int alcance = classeRanged() ? ALCANCE_RANGED_SQM : 1;
        if (distanciaSqm(local.x, local.y, alvo.x, alvo.y) > alcance) return;
        String id = alvo.id;
        // "<tipo>_<x>_<y>": tipo e' tudo antes dos 2 ultimos "_" (pode ter "_", ex cave_spider).
        int ultimo = id.lastIndexOf('_');
        int penultimo = ultimo > 0 ? id.lastIndexOf('_', ultimo - 1) : -1;
        String tipo = penultimo > 0 ? id.substring(0, penultimo) : id;
        String efeito = efeitoDaClasse();
        String projetil = projetilDaClasse();
        boolean ranged = classeRanged();
        socket.emitRaw("hit_mob", GameSocket.obj(jw -> {
            jw.set("mob_id", id);
            jw.set("mob_type_id", tipo);
            jw.set("hit_type", efeito);
            jw.set("w_type", ranged ? "Ranged" : "Melee");
            jw.set("proj", projetil);
        }));
        esperaAtaque = INTERVALO_ATAQUE;
    }

    private void desenharBags() {
        for (BagChao bag : bags.values()) {
            TextureRegion r = bag.dourada && regiaoBagDourada != null ? regiaoBagDourada : regiaoBag;
            if (r == null) continue;
            float ancoraX = Math.round(bag.x / camera.zoom) * camera.zoom;
            float ancoraY = Math.round(bag.y / camera.zoom) * camera.zoom;
            batch.draw(r, ancoraX - r.getRegionWidth() / 2f, ancoraY);
        }
    }

    private void desenharEfeitos() {
        for (Projetil pr : projeteis) {
            float t = Math.min(1f, pr.tempo / pr.duracao);
            float x = pr.x0 + (pr.x1 - pr.x0) * t;
            float y = pr.y0 + (pr.y1 - pr.y0) * t;
            float angulo = (float) Math.toDegrees(Math.atan2(pr.y1 - pr.y0, pr.x1 - pr.x0));
            float w = pr.regiao.getRegionWidth(), h = pr.regiao.getRegionHeight();
            batch.draw(pr.regiao, x - w / 2f, y - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, angulo);
        }
        for (Efeito e : efeitos) {
            int quadro = Math.min(e.quadros.length - 1, (int) (e.tempo / DURACAO_QUADRO_EFEITO));
            TextureRegion r = e.quadros[quadro];
            float ancoraX = Math.round(e.x / camera.zoom) * camera.zoom;
            float ancoraY = Math.round(e.y / camera.zoom) * camera.zoom;
            batch.draw(r, ancoraX - r.getRegionWidth() / 2f, ancoraY);
        }
    }

    // ---- Morte / renascer do player local ----

    private void criarPainelMorte() {
        painelMorte = new Table();
        painelMorte.setFillParent(true);
        Table caixa = new Table();
        caixa.setBackground(UiSkin.retangulo(new Color(0.1f, 0.02f, 0.02f, 0.92f), new Color(0.6f, 0.1f, 0.1f, 1f), 1));
        caixa.pad(18);
        Label titulo = new Label("You are dead", skin, "subtitulo");
        titulo.setColor(new Color(1f, 0.3f, 0.3f, 1f));
        TextButton renascer = new TextButton("Respawn", skin, "default");
        renascer.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                renascerLocal();
            }
        });
        caixa.add(titulo).padBottom(14).row();
        caixa.add(renascer).width(200).height(56);
        painelMorte.add(caixa);
        painelMorte.setVisible(false);
        uiStage.addActor(painelMorte);
    }

    private void morrerLocal() {
        if (localMorto) return;
        localMorto = true;
        alvoMob = null;
        janelaLoot.fechar();
        painelMorte.setVisible(true);
    }

    /** Volta pro ponto de spawn com HP/MP cheios (o servidor devolve os vitais
     * quando recebe is_dead=false; ver servidor.py::handle_update_status). */
    private void renascerLocal() {
        if (!localMorto) return;
        localMorto = false;
        painelMorte.setVisible(false);
        local.x = spawnX;
        local.y = spawnY;
        local.movendo = false;
        local.direcao = "down";
        float rawX = conversor.mundoParaRawX(spawnX);
        float rawY = conversor.mundoParaRawY(spawnY);
        socket.emitRaw("request_area_sync", GameSocket.obj(jw -> {
            jw.set("x", rawX);
            jw.set("y", rawY);
            jw.set("floor", 1);
        }));
        socket.emitRaw("update_status", GameSocket.obj(jw -> {
            jw.set("key", "is_dead");
            jw.set("value", false);
        }));
        hud.definir(hud.hpMax(), -1f, -1f, -1f);
        spawnSmokeX = local.x;
        spawnSmokeY = local.y;
        spawnSmokeTempo = 0f;
    }

    /** Nome do mob acima da barra de vida, na cor da vida (verde -> vermelho). */
    private void desenharNomeMob(MobVisual mob) {
        if (!mob.visivel || mob.morto) return;
        TextureRegion quadro = quadroAtual(mob.animacao, mob.andandoVisual(), mob.direcao, mob.progresso);
        float altura = quadro.getRegionHeight() * ESCALA_SPRITE;
        float ancoraX = Math.round(mob.x / camera.zoom) * camera.zoom;
        float ancoraY = Math.round(mob.y / camera.zoom) * camera.zoom;
        layout.setText(font, mob.nome);
        float nomeX = Math.round((ancoraX - layout.width / 2f) / camera.zoom) * camera.zoom;
        float nomeY = Math.round((ancoraY + altura + 10f) / camera.zoom) * camera.zoom;
        Color anterior = new Color(font.getColor());
        font.setColor(corDaVida(mob.hp / mob.maxHp));
        font.draw(batch, mob.nome, nomeX, nomeY);
        font.setColor(anterior);
    }

    private static Color corDaVida(float pct) {
        if (pct > 0.75f) return new Color(0.2f, 0.85f, 0.2f, 1f);
        if (pct > 0.5f) return new Color(0.6f, 0.85f, 0.2f, 1f);
        if (pct > 0.25f) return Color.ORANGE;
        return Color.RED;
    }

    /** Dano subindo ~24px e sumindo no fim (mob.gd::exibir_numero_dano). */
    private void desenharNumerosDano() {
        Color anterior = new Color(font.getColor());
        for (NumeroDano n : numerosDano) {
            float t = n.tempo / DURACAO_NUMERO_DANO;
            float subida = 24f * (1f - (1f - t) * (1f - t)); // ease-out
            float alfa = t < 0.55f ? 1f : 1f - (t - 0.55f) / 0.45f;
            layout.setText(font, n.texto);
            float x = Math.round((n.x - layout.width / 2f) / camera.zoom) * camera.zoom;
            float y = Math.round((n.y + 20f + subida) / camera.zoom) * camera.zoom;
            Color cor = n.critico ? new Color(1f, 0.6f, 0f, 1f) : new Color(1f, 0.2f, 0.2f, 1f);
            cor.a = Math.max(0f, alfa);
            font.setColor(cor);
            font.draw(batch, n.texto, x, y);
        }
        font.setColor(anterior);
    }

    private void desenharNomeNPC(NPCVisual npc) {
        TextureRegion quadro = quadroAtual(npc.animacao, npc.movimento);
        float ancoraX = Math.round(npc.movimento.x / camera.zoom) * camera.zoom;
        float ancoraY = Math.round(npc.movimento.y / camera.zoom) * camera.zoom;
        float altura = quadro.getRegionHeight() * ESCALA_SPRITE;
        layout.setText(font, npc.nome);
        float x = Math.round((ancoraX - layout.width / 2f) / camera.zoom) * camera.zoom;
        float y = Math.round((ancoraY + altura + 7f) / camera.zoom) * camera.zoom;
        font.draw(batch, npc.nome, x, y);
    }

    /** Nome SEMPRE por cima de tudo, incluindo as celulas overlap_transparency
     * - por isso desenhado DEPOIS de mapa.desenharOverlays() (ver render()),
     * nao junto do sprite em desenharJogador(). O sprite do personagem
     * continua podendo ficar atras de tile overlay - so' o nome e' sempre
     * legivel. */
    private void desenharNome(Jogador j) {
        float altura = quadroAtual(animacaoBase, j).getRegionHeight() * ESCALA_SPRITE;
        // Mesmo snap de ancoraX/Y que desenharJogador() usa (Math.round(v/zoom)*zoom)
        // - precisa bater exatamente, senao o nome treme independente do sprite.
        float ancoraX = Math.round(j.x / camera.zoom) * camera.zoom;
        float ancoraY = Math.round(j.y / camera.zoom) * camera.zoom;
        // font usa Linear (ver criarFonteNome) e fica anti-aliased normalmente;
        // escala com o zoom sozinho (layout.width ja' sai em unidade de
        // mundo, na escala NOME_ESCALA_BASE fixa definida em criarFonteNome -
        // camera.combined cuida do resto).
        // Math.round(v) sozinho (sem dividir/multiplicar por camera.zoom)
        // arredonda pro world-unit INTEIRO mais proximo - so' coincide com o
        // pixel de tela mais proximo quando zoom==1. Em qualquer outro zoom
        // (0.5, 0.667...) isso arredonda pro grid ERRADO, destoando do grid
        // que o sprite usa (ancoraX/Y, que corretamente arredonda pro
        // MULTIPLO DE ZOOM mais proximo) - o nome tremia sozinho em QUALQUER
        // zoom por causa disso (bug reportado pelo usuario; so' nao
        // acontecia por coincidencia nos raros frames em que o resto caia
        // num multiplo exato). Mesmo padrao Math.round(v/zoom)*zoom usado em
        // todo o resto do arquivo (ancoraX/Y, camera.position).
        String nomeVisivel = nomeVisivel(j.nome);
        layout.setText(font, nomeVisivel);
        float nomeXBruto = ancoraX - layout.width / 2f;
        // Gap acima da cabeca reduzido de 14 pra 4 - o +14 antigo era
        // proporcional ao personagem de 32px/ESCALA_SPRITE=2x; com o
        // personagem agora em 16px (ESCALA_SPRITE=1x) o nome ficava flutuando
        // longe demais da cabeca (usuario reportou "muito em cima").
        float nomeYBruto = ancoraY + altura + 7f;
        float nomeX = Math.round(nomeXBruto / camera.zoom) * camera.zoom;
        float nomeY = Math.round(nomeYBruto / camera.zoom) * camera.zoom;
        font.draw(batch, nomeVisivel, nomeX, nomeY);
    }

    private static String nomeVisivel(String nome) {
        if (nome == null) return "";
        return "?".repeat(nome.codePointCount(0, nome.length()));
    }

    @Override
    public void resize(int width, int height) {
        // camera nao precisa ser tocada aqui diretamente - atualizarCamera()
        // (chamada todo frame em render()) ja' recalibra sozinha ao detectar
        // que o tamanho mudou, com a mesma rechecagem defensiva que antes so'
        // existia aqui.
        //
        // getBackBufferWidth/Height, nao os parametros width/height recebidos
        // aqui - no LWJGL3 eles vem em unidades LOGICAS da janela (GLFW), que
        // divergem dos pixels reais do framebuffer com escala de tela HiDPI/
        // fracionaria no desktop (ja documentado em construirUiSettings() pra
        // geracao de fonte, mas o viewport continuava usando os parametros
        // logicos aqui). A fonte do chat/skin e' gerada casando com o
        // framebuffer real - atualizar o viewport com unidades logicas
        // descasava a escala de desenho da escala em que a fonte foi
        // rasterizada, causando o texto do chat borrado/com espaçamento
        // irregular reportado no PC (nao acontecia no Android, onde os dois
        // ja' batem).
        uiStage.getViewport().update(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), true);
    }

    @Override
    public void dispose() {
        mapa.dispose();
        batch.dispose();
        font.dispose();
        pixelColisao.dispose();
        pixelBranco.dispose();
        hud.dispose();
        uiStage.dispose();
        skin.dispose();
        atlas.dispose();
        if(iluminacao != null) iluminacao.dispose();
    }
}