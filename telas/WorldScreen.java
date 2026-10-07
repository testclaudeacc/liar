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
        TextureRegion morte; // quadro 10; null se a tira nao tiver (skin sem quadro de morte)
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
    private final List<MapaPropriedades.Luz> luzesDoFrame = new ArrayList<>();
    private final Map<String, MapaPropriedades.Luz> luzesRemotos = new HashMap<>();

    private final float spawnX, spawnY;
    private final Jogador local;
    private HotbarUI hotbar;
    private final Map<String, Jogador> remotos = new HashMap<>();
    // Players online mas fora do raio da tela (o servidor so' manda movimento
    // de quem esta perto, ver servidor.py::_flush_visao). Ficam guardados aqui
    // (skin/classe) e voltam pra "remotos" quando o servidor manda a posicao
    // deles de novo ('mb'). Fora de "remotos" nao sao desenhados, nao colidem,
    // nao contam na lista do chat - nada.
    private final Map<String, Jogador> remotosForaDeVisao = new HashMap<>();
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
        boolean bloqueio = false;
        Color cor = null; // null = vermelho do dano
        float tempo = 0f;
        NumeroDano(float x, float y, String texto, boolean critico) {
            this.x = x; this.y = y; this.texto = texto; this.critico = critico;
        }
    }
    private final List<NumeroDano> numerosDano = new ArrayList<>();

    /** Aviso subindo em cima de um player ("Level 8", "Magic 19") - segue o
     * player enquanto ele anda. */
    private static class TextoFlutuante {
        final Jogador alvo;   // so' pra empilhar varios do mesmo player
        final float x, y;     // posicao fixa: onde o player estava quando subiu
        final String texto;
        final Color cor;
        float tempo = 0f;
        TextoFlutuante(Jogador alvo, String texto, Color cor) {
            this.alvo = alvo; this.x = alvo.x; this.y = alvo.y; this.texto = texto; this.cor = cor;
        }
    }
    // Fonte propria (gerada grande e reduzida) pros avisos de level/skill:
    // escalar a fonte dos nomes pra cima deixava o texto borrado.
    private BitmapFont fonteDestaque;
    private static final float ESCALA_DESTAQUE = 1.5f; // em relacao ao nome
    private final List<TextoFlutuante> textosFlutuantes = new ArrayList<>();

    /** Textinho de acao (servidor.py::texto_de_acao, ex: "Om Noom"): parado em
     * cima da cabeca do player (acompanha ele andando), um pouco menor que o
     * nome, e some sozinho - sem a animacao de subir dos avisos de level. */
    private static class TextoAcao {
        final Jogador alvo;
        final String texto;
        final Color cor;
        float tempo = 0f;
        TextoAcao(Jogador alvo, String texto, Color cor) { this.alvo = alvo; this.texto = texto; this.cor = cor; }
    }
    private final List<TextoAcao> textosAcao = new ArrayList<>();
    private static final float DURACAO_TEXTO_ACAO = 1.6f, SUMINDO_TEXTO_ACAO = 0.4f;
    private static final float ESCALA_TEXTO_ACAO = 0.85f; // em relacao ao nome

    /** Fala do chat Local em cima da cabeca: "Nome: mensagem" (nome na cor da
     * classe, mensagem em branco). Uma por jogador - a nova substitui a velha. */
    private static class Fala {
        final String nome, texto;
        final Color corNome;
        Color corTexto = null; // null = branco (Local); amarelo = party
        final float duracao;
        float tempo = 0f;
        Fala(String nome, String texto, Color corNome) {
            this.nome = nome; this.texto = texto; this.corNome = corNome;
            // Mensagem longa fica mais tempo pra dar tempo de ler.
            this.duracao = Math.min(10f, 4f + texto.length() * 0.06f);
        }
    }
    private final Map<String, Fala> falas = new HashMap<>(); // nome real do jogador -> fala
    private static final float LARGURA_FALA = 110f; // quebra de linha (unidades de mundo)
    // Sobe por 1.2s, fica parado 5s (da' tempo de tirar print) e some em 0.5s.
    private static final float TEXTO_SUBIDA = 1.2f, TEXTO_PARADO = 5f, TEXTO_SUMINDO = 0.5f;
    private static final float DURACAO_TEXTO_FLUTUANTE = TEXTO_SUBIDA + TEXTO_PARADO + TEXTO_SUMINDO;

    // ---- Combate (alvo, ataque automatico, efeitos, loot, morte) ----
    // Mob mirado (clique nele; clique de novo ou ESC tira). Ataca sozinho a
    // cada ~1s enquanto estiver no alcance (o servidor so' aceita 1 hit/s).
    private String alvoMob = null;
    // Outro player clicado: so' o FriendTarget em volta do SQM dele, sem ataque.
    private String amigoMarcado = null;
    private TextureRegion regiaoAlvoAmigo;
    private float esperaAtaque = 0f;
    // Cadencia de ataque (servidor.py::ATAQUE_COOLDOWN_SEG = 2.385s, mesmo valor
    // pro player e pros mobs) + uma folga pra latencia.
    private static final float INTERVALO_ATAQUE = 2.4f;
    // HP {atual, maximo} dos players remotos, pra cor do nome.
    private final Map<String, float[]> vidaRemotos = new HashMap<>();
    // Igual servidor.py::ALCANCE_RANGED_SQM = DETECCAO_SQM: arma a distancia
    // alcanca o mesmo raio (em linha reta) em que o mob detecta o player.
    private static final int ALCANCE_RANGED_SQM = 5;

    /** Animacao de efeito (tira de quadros de 16px) tocando uma vez num ponto. */
    private static class Efeito {
        final TextureRegion[] quadros;
        final float x, y;
        // Se tiver alvo, o efeito acompanha o sprite dele (mob/player andando).
        MobVisual mobAlvo;
        Jogador jogadorAlvo;
        float tempo = 0f;
        // Hits usam DURACAO_QUADRO_HIT (mais lento); fumaca de spawn segue no ritmo antigo.
        float duracaoQuadro = DURACAO_QUADRO_EFEITO;
        Efeito(TextureRegion[] quadros, float x, float y) { this.quadros = quadros; this.x = x; this.y = y; }
        float posX() { return mobAlvo != null ? mobAlvo.x : jogadorAlvo != null ? jogadorAlvo.x : x; }
        float posY() { return mobAlvo != null ? mobAlvo.y : jogadorAlvo != null ? jogadorAlvo.y : y; }
    }
    private static final float DURACAO_QUADRO_EFEITO = 0.05f;
    // Hit no mob/player: 0.05s por quadro passava rapido demais (a pedido do usuario).
    private static final float DURACAO_QUADRO_HIT = 0.1f;
    // Hit fisico (mobs, ex: worm) e de flecha: um pouco mais rapido que os outros.
    private static final float DURACAO_QUADRO_HIT_RAPIDO = 0.07f;
    private static final float DURACAO_QUADRO_HIT_FLECHA = 0.05f;
    private final List<Efeito> efeitos = new ArrayList<>();
    private final Map<String, TextureRegion[]> cacheEfeitos = new HashMap<>();

    /** Projetil (flecha/magia/nota) indo do atacante ate o mob; ao chegar toca o hit. */
    private static class Projetil {
        final TextureRegion regiao;
        final float x0, y0;
        final MobVisual alvo;   // persegue o mob (ele pode estar andando)
        final String efeitoHit;
        NumeroDano numero;      // dano que aparece quando o projetil chega
        float tempo = 0f;
        // Tempo fixo (a velocidade cresce com a distancia): sempre chega logo,
        // e so' entao aparecem o hit e o numero de dano.
        final float duracao = DURACAO_PROJETIL;
        Projetil(TextureRegion regiao, float x0, float y0, MobVisual alvo, String efeitoHit) {
            this.regiao = regiao; this.x0 = x0; this.y0 = y0; this.alvo = alvo; this.efeitoHit = efeitoHit;
        }
        float x1() { return alvo.x; }
        float y1() { return alvo.y + 8f; }
    }
    private final List<Projetil> projeteis = new ArrayList<>();
    private static final float DURACAO_PROJETIL = 0.12f;

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
    private static final float TEMPO_BAG_VISIVEL = 300f; // 5 min (servidor: LOOT_BAG_VISIVEL_SEG = LOOT_EXPIRA_SEG)

    private TextureRegion regiaoAlvo, regiaoTargetHit, regiaoFlag, regiaoBag, regiaoBagDourada;
    private HudVitais hud;
    /** Loot pego subindo em cima do player (estilo Kakele): uma linha por
     * item / moeda, com icone e quantidade. Fica onde o player estava. */
    private static class LootFlutuante {
        final float x, y;
        final List<TextureRegion> icones = new ArrayList<>();
        final List<String> textos = new ArrayList<>();
        final List<Color> cores = new ArrayList<>();
        boolean semCapacidade = false;
        float tempo = 0f;
        LootFlutuante(float x, float y) { this.x = x; this.y = y; }
        void adicionar(TextureRegion icone, String texto, Color cor) { icones.add(icone); textos.add(texto); cores.add(cor); }
    }
    private final List<LootFlutuante> lootsFlutuantes = new ArrayList<>();
    private static final float DURACAO_LOOT_FLUTUANTE = 1.4f;
    private static final int ALCANCE_BAG_SQM = 4;
    private boolean localMorto = false;
    // Outros players mortos (esperando renascer) - desenhados no quadro de morte.
    private final java.util.Set<String> remotosMortos = new java.util.HashSet<>();
    private Table painelMorte;
    private static final float DURACAO_NUMERO_DANO = 0.85f;

    private final Map<String, MobVisual> mobs = new LinkedHashMap<>();
    // Cadaver some depois disso (servidor: MOB_DESPAWN_CORPO_SEG).
    private static final float TEMPO_CORPO_MOB = 60f;
    private static final float FADE_CORPO = 2f; // segundos ficando transparente antes de sumir
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
    /** Corpo de player que renasceu: fica no chao TEMPO_CADAVER_PLAYER
     * segundos, igual o cadaver dos mobs. So' visual (ninguem colide com ele,
     * da' pra passar por cima). */
    private static class CadaverPlayer {
        final float x, y;
        final List<TextureRegion> quadros = new ArrayList<>();
        final List<Color> cores = new ArrayList<>();
        float restante = TEMPO_CADAVER_PLAYER;
        CadaverPlayer(float x, float y) { this.x = x; this.y = y; }
    }
    private static final float TEMPO_CADAVER_PLAYER = 120f; // 2 minutos

    /** SpawnWarning (4 quadros) tocando 3x no SQM onde um mob vai nascer,
     * com uma luz azul pequena (so' o SQM). */
    private static class AvisoSpawn {
        final float x, y;          // ancora (centro em X, base em Y), igual mob
        final float duracao;       // ate o mob nascer
        final MapaPropriedades.Luz luz;
        float tempo = 0f;
        AvisoSpawn(float x, float y, float duracao) {
            this.x = x; this.y = y; this.duracao = duracao;
            this.luz = new MapaPropriedades.Luz(x, y + 8f, COR_LUZ_AVISO_SPAWN, RAIO_LUZ_AVISO_SPAWN);
        }
    }
    private static final int REPETICOES_AVISO_SPAWN = 3;
    private static final Color COR_LUZ_AVISO_SPAWN = new Color(0.25f, 0.5f, 1f, 1f);
    private static final float RAIO_LUZ_AVISO_SPAWN = 12f;
    private final List<AvisoSpawn> avisosSpawn = new ArrayList<>();
    private TextureRegion[] quadrosAvisoSpawn;
    private final List<CadaverPlayer> cadaveres = new ArrayList<>();

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
    private TextureRegion iconeChatNovo;
    private Image imagemIconeChat;
    private boolean chatComNovidade = false;
    private TextureRegion iconeCheckOn, iconeCheckOff;
    private Table painelSettings, painelOptions;
    private SelectBox<String> botaoZoom;
    private Button botaoTopoChat, botaoTopoMenu, botaoTopoConfig;
    // 4o botao do topo: mostra o sprite (animado) do mob/player mirado, com o
    // fundo na cor da vida dele (cinza normal sem alvo).
    private Button botaoTopoAlvo;
    private CheckBox checkFullscreen;
    // GUI de chat portada 1:1 da simulacao de estresse (TesteGame/ChatUI) -
    // so' pra validar o layout/toque no mobile; ainda nao fala com o servidor
    // de verdade (ver ChatUI, sem mudanca nenhuma aqui).
    private ChatUI chat;
    // Se o campo do chat estava focado no fim do frame anterior - ver o
    // polling do ENTER em render() (evita refocar logo apos enviar).
    private boolean chatDigitandoFrameAnterior;
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
    private TextureRegion notifChat, notifConfig, notifMenu;
    private TextureRegion iconeLiderParty, iconeMembroParty; // ui/party/PT_Crown / PT_Shield
    private TextureRegion iconeConviteParty; // Pt_Invite: em cima de quem eu convidei (so' eu vejo)
    private final java.util.Set<String> convitesPartyEnviados = new java.util.HashSet<>();

    /** Primeira regiao que existir no atlas (nome do arquivo pode variar). */
    private TextureRegion regiaoQualquer(String... nomes) {
        for (String n : nomes) {
            TextureRegion r = atlas.findRegion(n);
            if (r != null) return r;
        }
        return null;
    }
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
        fonteDestaque = criarFonteDestaque();
        spriteBase = atlas.findRegion("sprites/base/BaseSoul");
        animacaoBase = criarAnimacao(spriteBase);

        notifChat = atlas.findRegion("sprites/notifications/Chat");
        iconeLiderParty = atlas.findRegion("ui/party/PT_Crown");
        balaoTrade = atlas.findRegion("ui/items/Trade");
        iconeMembroParty = atlas.findRegion("ui/party/PT_Shield");
        iconeConviteParty = regiaoQualquer("ui/party/Pt_Invite", "ui/party/PT_Invite", "ui/Pt_Invite", "ui/PT_Invite",
            "ui/party/PtInvite", "ui/party/PT_invite");
        notifConfig = atlas.findRegion("sprites/notifications/Settings");
        notifMenu = atlas.findRegion("sprites/notifications/Menu");

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
        regiaoAlvoAmigo = atlas.findRegion("ui/slots/FriendTarget");
        TextureRegion tiraAviso = atlas.findRegion("ui/SpawnWarning");
        if (tiraAviso != null) {
            int n = Math.max(1, tiraAviso.getRegionWidth() / 16);
            quadrosAvisoSpawn = new TextureRegion[n];
            for (int i = 0; i < n; i++) quadrosAvisoSpawn[i] = new TextureRegion(tiraAviso, i * 16, 0, 16, tiraAviso.getRegionHeight());
        }
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
                // Clique no proprio SQM: desfoca o alvo travado (e consome o
                // clique - o sprite de um mob logo abaixo invade o SQM do player).
                if (tileX(wx) == tileX(local.x) && (int) Math.floor(wy / Jogador.TILE) == tileY(local.y)) {
                    if (!localMorto) alvoMob = null;
                    return true;
                }
                if (!localMorto && cliqueEmMob(wx, wy)) return true;
                if (cliqueEmJogador(wx, wy)) return true;

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
                // Atalhos da barra (teclas configuraveis, padrao 1-9 - Controles).
                int slotHotbar = Controles.slotDaTecla(keycode);
                if (slotHotbar >= 0) {
                    if (chat.estaDigitando() || localMorto || settingsAberta() || bookMenu.isVisible()) return false;
                    return hotbar.usarTecla(slotHotbar + 1);
                }
                if (keycode == Input.Keys.C) {
                    mostrarColisao = !mostrarColisao;
                    return true;
                }
                // Open Bag (padrao B): abre o livro direto na Bag (fecha se ja' esta aberto).
                if (keycode == Controles.tecla("bag")) {
                    if (chat.estaDigitando()) return false;
                    if (bookMenu.isVisible()) alternarBookMenu();
                    else bookMenu.abrirSecao("Bag");
                    return true;
                }
                // Open Menu (padrao E): fala com NPC perto, senao abre/fecha o livro.
                if (keycode == Controles.tecla("menu")) {
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
                    // Janela do player sai antes de qualquer outra coisa. ESC nao
                    // tira mais o alvo (so' clicar no proprio SQM ou no mob).
                    if (painelJogador.isVisivel()) { painelJogador.fechar(); return true; }
                    if (mapaGrandeAberto()) { fecharMapaGrande(); return true; }
                    if (amigoMarcado != null) { amigoMarcado = null; return true; }
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
                if (keycode == Controles.tecla("chat")) {
                    if (chat.estaDigitando()) return false;
                    alternarChat();
                    return true;
                }
                // Open Map (padrao M): abre/fecha o mapa grande.
                if (keycode == Controles.tecla("mapa")) {
                    if (chat.estaDigitando()) return false;
                    if (mapaGrandeAberto()) fecharMapaGrande();
                    else abrirMapaGrande();
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
        conteudoSettingsNormal = criarConteudoSettings();
        conteudoConfirmarSaida = criarConfirmacaoSaidaEmBatalha();
        painelSettings.add(conteudoSettingsNormal);
        painelSettings.setVisible(false);
        uiStage.addActor(painelSettings);

        painelOptions = new Table();
        painelOptions.setFillParent(true);
        painelOptions.add(criarConteudoOptions()).grow();
        painelOptions.setVisible(false);
        uiStage.addActor(painelOptions);

        chat = new ChatUI(uiStage, skin, atlas, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), nomeVisivel(local.nome), local.classe);
        chat.setOuvinteCanais(new ChatUI.OuvinteCanais() {
            @Override public void entrou(String canal) { emitirCanalChat("chat_join", canal); }
            @Override public void saiu(String canal) { emitirCanalChat("chat_leave", canal); }
            @Override public boolean enviouLocal(String texto) {
                if (!socket.isConnected()) return false;
                // Formato que servidor.py::handle_c espera: ["mensagem"].
                JsonValue lista = new JsonValue(JsonValue.ValueType.array);
                lista.addChild(new JsonValue(texto));
                socket.emitRaw("c", lista.toJson(JsonWriter.OutputType.json));
                return true;
            }
            @Override public boolean enviouPrivado(String destino, String texto) {
                if (!socket.isConnected()) return false;
                socket.emitRaw("pm", GameSocket.obj(jw -> {
                    jw.set("to", destino);
                    jw.set("msg", texto);
                }));
                return true;
            }
            @Override public boolean enviouParty(String texto) {
                if (!socket.isConnected()) return false;
                socket.emitRaw("pc", GameSocket.obj(jw -> jw.set("msg", texto)));
                return true;
            }
            @Override public boolean enviouCanal(String canal, String texto) {
                if (!socket.isConnected()) return false;
                socket.emitRaw("cc", GameSocket.obj(jw -> {
                    jw.set("channel", canal);
                    jw.set("msg", texto);
                }));
                return true;
            }
        });
        chat.setVisivel(false);

        texJoystickBase = atlas.findRegion("ui/Joystick");
        texJoystickKnob = atlas.findRegion("ui/Joystick_Middle");
        joystick = new Joystick(uiStage, texJoystickBase, texJoystickKnob);
        bookMenu = new BookMenuUI(uiStage, skin, atlas, socket, local.classe);
        hud = new HudVitais(uiStage, atlas, escala);
        hud.definirColunaEsquerda(colunaRetrato, 10f);
        // Barra de atalhos (9 slots livres, teclas 1-9) - o conteudo vem da aba Spells.
        hotbar = new HotbarUI(uiStage, skin, bookMenu, indice -> {
            if (localMorto || !socket.isConnected()) return;
            socket.emitRaw("use_hotbar", GameSocket.obj(jw -> jw.set("index", indice)));
        });
        bookMenu.definirAoMudarAtalhos(hotbar::atualizar);
        hotbar.definirJoystick(joystick); // editor de controles: joystick + botoes juntos
        bookMenu.definirNomeLocal(local.nome);
        painelJogador = new PainelJogadorUI(uiStage, skin, atlas, new PainelJogadorUI.Ouvinte() {
            @Override public void alternarAmigo(String nome) {
                socket.emitRaw("toggle_friend", GameSocket.obj(w -> w.set("friend_name", nome)));
            }
            @Override public void alternarIgnorar(String nome) {
                boolean agora = !ignorados.contains(nome);
                if (agora) ignorados.add(nome); else ignorados.remove(nome);
                salvarIgnorados();
                chat.adicionarMensagemSistema(agora ? "You are now ignoring " + nomeVisivel(nome) + "."
                    : "You stopped ignoring " + nomeVisivel(nome) + ".");
                atualizarEstadoPainel();
            }
            @Override public void convidarParty(String nome) {
                socket.emitRaw("invite_party", GameSocket.obj(w -> w.set("target_name", nome)));
                painelJogador.fechar();
            }
            @Override public void convidarTrade(String nome) {
                // Se ele ja' tinha me mandado trade, o servidor trata como aceitar.
                socket.emitRaw("trade_invite", GameSocket.obj(w -> w.set("target_name", nome)));
                painelJogador.fechar();
            }
            @Override public void abrirChat(String nome) {
                fecharOutrasJanelas();
                chat.abrirConversaPrivada(nome, nomeVisivel(nome), BookMenuUI.iconeClasse(classeDe(nome)));
                atualizarVisibilidadeJoystick();
            }
            @Override public void alternarIconeAmigo(String nome, String icone) {
                socket.emitRaw("set_friend_icon", GameSocket.obj(w -> {
                    w.set("friend_name", nome);
                    w.set("icon", icone);
                }));
            }
        });
        bookMenu.definirAoMudarAmigos(this::atualizarEstadoPainel);
        // Aba Party do chat acompanha a party (aparece ao entrar, some ao sair).
        bookMenu.definirAoMudarParty(() -> {
            List<String> nomes = new ArrayList<>();
            for (BookMenuUI.MembroParty m : bookMenu.membrosParty()) nomes.add(m.nome);
            chat.definirParty(nomes);
        });
        // Clicar num amigo na aba Friends: fecha o livro e abre a janela dele.
        bookMenu.definirAoClicarAmigo(nome -> {
            bookMenu.setVisible(false);
            abrirPainelDe(nome);
        });
        // Aba de PV no chat mostra o player (sprite animado, ou icone da classe).
        chat.setFornecedorRetrato((nome, iconeReserva) ->
            new AtorAlvo(nome, iconeReserva != null ? atlas.findRegion(iconeReserva) : null));
        carregarIgnorados();
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

        // Alvo, chat e bag/livro (os mais usados): sempre a mostra, a
        // esquerda do minimapa, pendurados no topo (sem girar). Config e o
        // botao do minimapa ficam atras do retrato.
        botaoTopoAlvo = criarBotaoTopo(null, this::abrirPainelDoAlvo, false);
        botaoTopoAlvo.add(new AtorAlvo()).size(ICONE_BOTAO_TOPO);
        botaoTopoChat = criarBotaoTopo(iconeChat, () -> { recolherBotoesTopo(); alternarChat(); }, false);
        imagemIconeChat = (Image) botaoTopoChat.getChildren().first();
        // Icone de "mensagem nova" (chat fechado). Sem ele no atlas, usa o
        // NotificationIcon no lugar.
        iconeChatNovo = atlas.findRegion("ui/ChatNotify");
        if (iconeChatNovo == null) iconeChatNovo = atlas.findRegion("ui/NotificationIcon");
        botaoTopoMenu = criarBotaoTopo(iconeMenu, () -> { recolherBotoesTopo(); alternarBookMenu(); }, false);
        botaoTopoConfig = criarBotaoTopo(iconeConfig, () -> { recolherBotoesTopo(); alternarSettings(); }, true);

        // Config e mapa ficam escondidos atras do retrato do player (canto de
        // cima a esquerda, ver criarRetrato): tocar nele abre/fecha a coluna.
        colunaBotoesTopo = new Table();
        colunaBotoesTopo.top();
        // Mapa: liga/desliga o minimapa (verde = ligado), embaixo de menu/config.
        botaoTopoMapa = criarBotaoTopo(atlas.findRegion("ui/buttons/MapBtn"), () -> {
            Controles.definirMinimapaVisivel(!Controles.minimapaVisivel());
            montarBarraMiniMapa();
        }, true);
        for (Button b : new Button[]{botaoTopoConfig, botaoTopoMapa}) {
            colunaBotoesTopo.add(b).size(TAMANHO_BOTAO_TOPO).padTop(8).row();
        }
        colunaBotoesTopo.setVisible(false);
        colunaRetrato = new Table();
        colunaRetrato.top().left();
        botaoRetrato = criarRetrato();
        colunaRetrato.add(botaoRetrato).size(TAMANHO_RETRATO).row();
        // Botoes centralizados embaixo do retrato, em linha reta.
        colunaRetrato.add(colunaBotoesTopo).top().center();

        // Canto de cima a direita: minimapa (tocar abre o mapa grande) e
        // fps/ms embaixo dele.
        pinturaMiniMapa = new MiniMapa.Pintura(mapa.gerarPixmapMiniMapa(MiniMapa.COR_VOID));
        miniMapa = new MiniMapa(mapa, pinturaMiniMapa, fonteMiniMapa, TILES_MINIMAPA, TILES_MAPA_MIN, TILES_MAPA_MAX, false, 2f);
        miniMapa.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                abrirMapaGrande();
            }
        });
        labelFps = new Label("0 fps", skin, "hud");
        labelMs = new Label("0.0 ms", skin, "hud");
        Table infoDesempenho = new Table();
        infoDesempenho.add(labelFps).right().row();
        infoDesempenho.add(labelMs).right();
        this.infoDesempenho = infoDesempenho;
        Table barra = new Table();
        barra.setFillParent(true);
        barra.top().right().pad(0, 0, 0, 20);
        barraMiniMapa = barra;
        montarBarraMiniMapa();
        // Por baixo das janelas (livro/chat/settings), igual o HUD.
        uiStage.getRoot().addActorAt(0, barra);

        criarMapaGrande();
    }

    // ---- Retrato do player + botoes / minimapa ----
    private Table colunaRetrato, colunaBotoesTopo, barraMiniMapa, infoDesempenho;
    private Button botaoTopoMapa;
    private static final Color COR_BOTAO_LIGADO = new Color(0.35f, 1f, 0.35f, 1f);

    /** [alvo][chat][bag/livro][minimapa], fps/ms embaixo. Minimapa desligado (botao do
     * mapa no retrato): sai da linha e os botoes encostam na direita. Mesma
     * margem de cima pra todos. */
    private void montarBarraMiniMapa() {
        Table barra = barraMiniMapa;
        barra.clearChildren();
        boolean comMinimapa = Controles.minimapaVisivel();
        float margemTopo = 10f;
        barra.add(botaoTopoAlvo).size(TAMANHO_BOTAO_TOPO).top().padTop(margemTopo).padRight(12);
        barra.add(botaoTopoChat).size(TAMANHO_BOTAO_TOPO).top().padTop(margemTopo).padRight(12);
        barra.add(botaoTopoMenu).size(TAMANHO_BOTAO_TOPO).top().padTop(margemTopo).padRight(comMinimapa ? 14 : 0);
        if (comMinimapa) barra.add(miniMapa).size(TAMANHO_MINIMAPA).top().padTop(margemTopo);
        barra.row();
        barra.add(infoDesempenho).colspan(comMinimapa ? 4 : 3).right().padTop(4);
        // Ligado: o botao fica verde (borda verde, fundo verde escuro).
        botaoTopoMapa.setColor(comMinimapa ? COR_BOTAO_LIGADO : Color.WHITE);
    }
    private Button botaoRetrato;
    private com.badlogic.gdx.scenes.scene2d.ui.Container<com.badlogic.gdx.scenes.scene2d.Actor> fotoRetrato;
    private Image avisoRetrato;
    private Texture texBolinha;
    private MiniMapa miniMapa, mapaGrande;
    private MiniMapa.Pintura pinturaMiniMapa;
    private Table painelMapaGrande;
    private final float TAMANHO_MINIMAPA = mobile ? 165f : 150f;
    private static final float TILES_MINIMAPA = 34f, TILES_MAPA_GRANDE = 80f, TILES_MAPA_MIN = 20f, TILES_MAPA_MAX = 300f;

    /** Pontinhos do minimapa: players azul, NPC amarelo, mob vermelho (voce = branco, no MiniMapa). */
    private final MiniMapa.Fonte fonteMiniMapa = new MiniMapa.Fonte() {
        @Override public float jogadorX() { return local.x; }
        @Override public float jogadorY() { return local.y; }
        @Override public void pontos(MiniMapa.Coletor c) {
            for (NPCVisual n : npcs.values()) c.ponto(n.movimento.x, n.movimento.y, MiniMapa.COR_NPC);
            for (MobVisual m : mobs.values()) if (m.visivel && !m.morto) c.ponto(m.x, m.y, MiniMapa.COR_MOB);
            // Outros players: so' quem esta na party (e perto, em "remotos"), na cor da classe.
            for (BookMenuUI.MembroParty m : bookMenu.membrosParty()) {
                Jogador j = remotos.get(m.nome);
                if (j != null) c.ponto(j.x, j.y, MiniMapa.corDaClasse(m.classe));
            }
        }
    };

    /** Retrato (rosto do personagem, com a skin atual) no estilo dos botoes
     * do topo. Bolinha vermelha = mensagem nova no chat. */
    private Button criarRetrato() {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = new TextureRegionDrawable(texBotaoTopo);
        estilo.over = new TextureRegionDrawable(texBotaoTopoHover);
        estilo.down = new TextureRegionDrawable(texBotaoTopoClick);
        Button botao = new Button(estilo);
        fotoRetrato = new com.badlogic.gdx.scenes.scene2d.ui.Container<>();
        fotoRetrato.setClip(true);
        fotoRetrato.top();
        fotoRetrato.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);

        Pixmap pm = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        pm.setColor(Color.BLACK);
        pm.fillCircle(16, 16, 15);
        pm.setColor(Color.WHITE); // cor vem do Image (aviso vermelho / legenda do mapa)
        pm.fillCircle(16, 16, 12);
        texBolinha = new Texture(pm);
        texBolinha.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pm.dispose();
        avisoRetrato = new Image(texBolinha);
        avisoRetrato.setColor(0.9f, 0.1f, 0.1f, 1f);
        avisoRetrato.setVisible(false);
        Table cantoAviso = new Table();
        cantoAviso.top().right();
        cantoAviso.add(avisoRetrato).size(TAMANHO_RETRATO * 0.26f);
        cantoAviso.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);

        float interno = TAMANHO_RETRATO * 0.70f;
        com.badlogic.gdx.scenes.scene2d.ui.Stack pilha = new com.badlogic.gdx.scenes.scene2d.ui.Stack();
        Table centro = new Table();
        centro.add(fotoRetrato).size(interno).padBottom(TAMANHO_RETRATO * 0.12f);
        pilha.add(centro);
        pilha.add(cantoAviso);
        botao.add(pilha).grow();
        botao.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                colunaBotoesTopo.setVisible(!colunaBotoesTopo.isVisible());
            }
        });
        return botao;
    }

    private void recolherBotoesTopo() {
        colunaBotoesTopo.setVisible(false);
    }

    /** Troca a foto do retrato pela skin atual (so' a parte de cima: cabeca/ombros). */
    private void atualizarRetrato(JsonValue skins) {
        if (fotoRetrato == null) return;
        SkinsUtil.Preview p = new SkinsUtil.Preview(atlas, skins, SkinsUtil.FRAME_BAIXO);
        float interno = TAMANHO_RETRATO * 0.70f;
        float largura = interno * 1.25f;
        float altura = largura * p.getPrefHeight() / SkinsUtil.FRAME_LARGURA;
        fotoRetrato.setActor(p);
        fotoRetrato.size(largura, altura);
    }

    private void criarMapaGrande() {
        mapaGrande = new MiniMapa(mapa, pinturaMiniMapa, fonteMiniMapa, TILES_MAPA_GRANDE, TILES_MAPA_MIN, TILES_MAPA_MAX, true, 2f);
        painelMapaGrande = new Table();
        painelMapaGrande.setFillParent(true);
        painelMapaGrande.setBackground(skin.getDrawable("fundo-opcoes"));
        painelMapaGrande.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);

        Table topo = new Table();
        Label titulo = new Label("Map", skin, "secao");
        topo.add(titulo).left().expandX();
        float tb = mobile ? 52f : 40f;
        TextButton menos = new TextButton("-", skin, "cinza-popup");
        TextButton mais = new TextButton("+", skin, "cinza-popup");
        TextButton centro = new TextButton("Center", skin, "cinza-popup");
        TextButton fechar = new TextButton("X", skin, "vermelho-popup");
        menos.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, com.badlogic.gdx.scenes.scene2d.Actor a) { mapaGrande.zoom(1.25f); }
        });
        mais.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, com.badlogic.gdx.scenes.scene2d.Actor a) { mapaGrande.zoom(0.8f); }
        });
        centro.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, com.badlogic.gdx.scenes.scene2d.Actor a) { mapaGrande.centralizar(); }
        });
        fechar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, com.badlogic.gdx.scenes.scene2d.Actor a) { fecharMapaGrande(); }
        });
        topo.add(menos).size(tb).padRight(8);
        topo.add(mais).size(tb).padRight(8);
        topo.add(centro).height(tb).padRight(16);
        topo.add(fechar).size(tb);

        Table legenda = new Table();
        adicionarLegenda(legenda, MiniMapa.COR_VOCE, "You");
        adicionarLegenda(legenda, MiniMapa.COR_KNIGHT, "Knight");
        adicionarLegenda(legenda, MiniMapa.COR_RANGER, "Ranger");
        adicionarLegenda(legenda, MiniMapa.COR_MAGE, "Mage");
        adicionarLegenda(legenda, MiniMapa.COR_BARD, "Bard");
        adicionarLegenda(legenda, MiniMapa.COR_NPC, "NPCs");
        adicionarLegenda(legenda, MiniMapa.COR_MOB, "Monsters");

        painelMapaGrande.pad(14, 24, 14, 24);
        painelMapaGrande.add(topo).growX().row();
        painelMapaGrande.add(mapaGrande).grow().padTop(10).row();
        painelMapaGrande.add(legenda).left().padTop(8);
        painelMapaGrande.setVisible(false);
        uiStage.addActor(painelMapaGrande);
    }

    private void adicionarLegenda(Table legenda, Color cor, String texto) {
        Image quadrado = new Image(new TextureRegionDrawable(new TextureRegion(texBolinha)));
        quadrado.setColor(cor);
        legenda.add(quadrado).size(14).padRight(5);
        legenda.add(new Label(texto, skin, "hud")).padRight(18);
    }

    private boolean mapaGrandeAberto() {
        return painelMapaGrande != null && painelMapaGrande.isVisible();
    }

    private void abrirMapaGrande() {
        fecharOutrasJanelas();
        if (chat.isVisivel()) chat.setVisivel(false);
        recolherBotoesTopo();
        mapaGrande.centralizar();
        painelMapaGrande.setVisible(true);
        painelMapaGrande.toFront();
        uiStage.setScrollFocus(mapaGrande);
        atualizarVisibilidadeJoystick();
    }

    private void fecharMapaGrande() {
        painelMapaGrande.setVisible(false);
        if (uiStage.getScrollFocus() == mapaGrande) uiStage.setScrollFocus(null);
        atualizarVisibilidadeJoystick();
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
    /** Mesma Tahoma Bold dos nomes, gerada a 64px (2x) e reduzida pra 1.5x o
     * tamanho do nome - reduzir textura fica nitido, ampliar borrava. */
    private BitmapFont criarFonteDestaque() {
        FreeTypeFontGenerator gerador = new FreeTypeFontGenerator(Gdx.files.internal("fonts/TAHOMAB0.TTF"));
        FreeTypeFontGenerator.FreeTypeFontParameter parametros = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parametros.size = 64;
        parametros.color = Color.WHITE;
        parametros.borderWidth = 4;
        parametros.borderColor = Color.BLACK;
        parametros.minFilter = TextureFilter.Linear;
        parametros.magFilter = TextureFilter.Linear;
        BitmapFont fonte = gerador.generateFont(parametros);
        fonte.getData().setScale(NOME_ESCALA_BASE * 32f / 64f * ESCALA_DESTAQUE);
        fonte.setUseIntegerPositions(false);
        gerador.dispose();
        return fonte;
    }

    private BitmapFont criarFonteNome() {
        FreeTypeFontGenerator gerador = new FreeTypeFontGenerator(Gdx.files.internal("fonts/TAHOMAB0.TTF"));
        FreeTypeFontGenerator.FreeTypeFontParameter parametros = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parametros.size = 32;
        // Glifos BRANCOS (antes vinham verdes): a cor final vem de
        // font.setColor(), que multiplica a cor do glifo - com glifo verde,
        // vermelho/amarelo da vida saiam pretos/esverdeados.
        parametros.color = Color.WHITE;
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
        // Cor padrao dos nomes (NPCs etc) continua o verde de antes.
        fonte.setColor(0f, 0.94f, 0f, 1f);
        gerador.dispose();
        return fonte;
    }

    // Foi 64 -> 128 (pequeno demais pra tocar no mobile) -> 108 (128 ficou
    // grande demais, achado pelo usuario testando) - meio termo, mas so' faz
    // sentido pro mobile (precisa caber o dedo). No PC (mouse, sem exigencia
    // de area de toque) 108 ficava grande demais - reduzido pra 72 so' la,
    // mantendo a mesma proporcao icone/botao (76/108 ~= 0.70).
    // Mobile reduzido de 108 pra 80 (a pedido do usuario, abre espaco pra hotbar).
    private final float TAMANHO_BOTAO_TOPO = mobile ? 80f : 72f;
    private final float TAMANHO_RETRATO = TAMANHO_BOTAO_TOPO * 1.3f;
    private final float ICONE_BOTAO_TOPO = TAMANHO_BOTAO_TOPO * (76f / 108f);

    /** Botao Button1 (up/hover/down) com um icone centralizado por cima -
     * mesmo chrome pros 3 botoes do topo, so' o icone muda. */
    private Button criarBotaoTopo(TextureRegion icone, Runnable aoClicar, boolean girar) {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        // girar: 90 graus, a parte que encaixa na borda (em cima) fica pra
        // ESQUERDA - os da coluna do retrato.
        estilo.up = girar ? girado90(texBotaoTopo) : new TextureRegionDrawable(texBotaoTopo);
        estilo.over = girar ? girado90(texBotaoTopoHover) : new TextureRegionDrawable(texBotaoTopoHover);
        estilo.down = girar ? girado90(texBotaoTopoClick) : new TextureRegionDrawable(texBotaoTopoClick);
        Button botao = new Button(estilo);
        if (icone != null) {
            Image imagemIcone = new Image(new TextureRegionDrawable(icone));
            imagemIcone.setScaling(Scaling.fit);
            botao.add(imagemIcone).size(ICONE_BOTAO_TOPO);
        }
        botao.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { aoClicar.run(); }
        });
        return botao;
    }

    /** Desenha a regiao girada 90 graus (anti-horario: o lado de cima vai pra esquerda). */
    private static com.badlogic.gdx.scenes.scene2d.utils.Drawable girado90(TextureRegion regiao) {
        com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable d = new com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable() {
            @Override public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float x, float y, float largura, float altura) {
                batch.draw(regiao, x, y, largura / 2f, altura / 2f, largura, altura, 1f, 1f, 90f);
            }
        };
        d.setMinWidth(regiao.getRegionHeight());
        d.setMinHeight(regiao.getRegionWidth());
        return d;
    }

    /** Mensagem de outro jogador chegou: com o chat fechado, troca o icone
     * do botao pro de notificacao ate abrir o chat (ver render()). */
    private void avisarMensagemNova(String remetente) {
        if (remetente.equals(local.nome) || chat.isVisivel() || iconeChatNovo == null) return;
        chatComNovidade = true;
        imagemIconeChat.setDrawable(new TextureRegionDrawable(iconeChatNovo));
    }

    private void alternarChat() {
        if (!chat.isVisivel()) fecharOutrasJanelas();
        chat.setVisivel(!chat.isVisivel());
        atualizarVisibilidadeJoystick();
    }

    /** Abrir o chat fecha o que estiver aberto (livro, settings, janela do player). */
    private void fecharOutrasJanelas() {
        if (bookMenu.isVisible()) bookMenu.setVisible(false);
        if (settingsAberta()) fecharSettings();
        if (painelJogador != null && painelJogador.isVisivel()) painelJogador.fechar();
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
        joystick.setVisivel(mobile && !chat.isVisivel() && !settingsAberta() && !bookMenu.isVisible() && !mapaGrandeAberto());
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
        // Abriu outra janela (tecla/atalho) com o mapa grande aberto: fecha o mapa.
        if (algumAberto && mapaGrandeAberto()) fecharMapaGrande();
        algumAberto = algumAberto || mapaGrandeAberto();
        hotbar.setVisivel(!algumAberto && !localMorto);
        colunaRetrato.setVisible(!algumAberto);
        barraMiniMapa.setVisible(!algumAberto);
        // Fundo do botao de alvo na cor da vida do alvo (cinza normal sem alvo).
        float pct = vidaDoAlvo();
        botaoTopoAlvo.setColor(pct < 0f ? Color.WHITE : corDaVida(pct));
    }

    /** % de vida do mob/player mirado; -1 sem alvo. */
    private float vidaDoAlvo() {
        if (alvoMob != null) {
            MobVisual mob = mobs.get(alvoMob);
            if (mob != null && !mob.morto) return mob.maxHp > 0f ? mob.hp / mob.maxHp : 1f;
        }
        if (amigoMarcado != null && remotos.containsKey(amigoMarcado)) {
            float[] vida = vidaRemotos.get(amigoMarcado);
            return vida != null && vida[1] > 0f ? vida[0] / vida[1] : 1f;
        }
        return -1f;
    }

    // Quem me mandou trade (balaozinho "Trade" em cima dele, so' eu vejo).
    private final java.util.Set<String> convitesTrade = new java.util.HashSet<>();
    private TextureRegion balaoTrade;
    private static final int DISTANCIA_TRADE_SQM = 5; // servidor.py::TRADE_DISTANCIA_SQM

    /** Botoes da janela do jogador: Party so' sem party ou sendo o lider;
     * Trade so' com ele a ate 5 SQMs (e vivo). Todo frame com a janela aberta. */
    private void atualizarBotoesPainel() {
        String nome = painelJogador != null ? painelJogador.nomeAberto() : null;
        if (nome == null) return;
        String lider = bookMenu.liderParty();
        boolean party = (lider == null || lider.equals(local.nome)) && !bookMenu.estaNaParty(nome);
        Jogador j = remotos.get(nome);
        boolean trade = j != null && !estaMorto(j) && !localMorto
            && distanciaSqm(local.x, local.y, j.x, j.y) <= DISTANCIA_TRADE_SQM;
        painelJogador.definirBotoesVisiveis(party, trade);
    }

    // ---- Notificacoes (HudVitais.notificar) ----
    private static final Color COR_NOTIF_ON = new Color(0.45f, 1f, 0.45f, 1f);
    private static final Color COR_NOTIF_OFF = new Color(0.75f, 0.75f, 0.75f, 1f);
    private static final Color COR_NOTIF_PARTY = Color.valueOf("f5e02a");
    /** Mesmos valores de servidor.py::FOME_LIMITE / FOME_MULT_VELOCIDADE. */
    private static final float FOME_LIMITE = 10f, FOME_MULT_VELOCIDADE = 0.9f;
    /** Igual servidor.py: DETECCAO_SQM (5) + PERSISTE_SQM (8) - o mob perde o player. */
    private static final int DISTANCIA_PERDE_ALVO_SQM = 13;
    private float multFome = 1f;
    private static final Color COR_NOTIF_AVISO = new Color(1f, 0.35f, 0.35f, 1f);

    /** Clique na notificacao de convite: abre o livro na aba Party. */
    private void abrirAbaParty() {
        fecharOutrasJanelas();
        if (chat.isVisivel()) chat.setVisivel(false);
        bookMenu.abrirSecao("Party");
        atualizarVisibilidadeJoystick();
    }

    // ---- Janela do jogador / ignorados / convite de party ----
    private PainelJogadorUI painelJogador;
    private final java.util.Set<String> ignorados = new java.util.HashSet<>();

    /** Botao de alvo do topo: com um player marcado, abre a janela dele. */
    private void abrirPainelDoAlvo() {
        if (amigoMarcado == null || !remotos.containsKey(amigoMarcado)) return;
        abrirPainelDe(amigoMarcado);
    }

    /** Janela de um player pelo nome (alvo do topo ou clique na lista de amigos). */
    private void abrirPainelDe(String nome) {
        TextureRegion reserva = atlas.findRegion(BookMenuUI.iconeClasse(classeDe(nome)));
        painelJogador.abrir(nome, nomeVisivel(nome), new AtorAlvo(nome, reserva));
        atualizarEstadoPainel();
    }

    /** Boneco "de foto" de um amigo que nao esta no jogo/na area: usa a skin
     * da lista de amigos (servidor.py::get_friends_list). null se nao tiver. */
    private Jogador jogadorDeAmigoOffline(String nome) {
        if (remotos.containsKey(nome) || remotosForaDeVisao.containsKey(nome)) return null;
        BookMenuUI.AmigoInfo a = bookMenu.amigo(nome);
        if (a == null || a.skins == null) return null;
        definirSkins(nome, a.skins);
        return new Jogador(nome, a.classe != null ? a.classe : "Knight", 0f, 0f);
    }

    /** Classe de um player que pode nem estar na tela (amigo offline/longe). */
    private String classeDe(String nome) {
        Jogador j = remotos.get(nome);
        if (j == null) j = remotosForaDeVisao.get(nome);
        if (j != null) return j.classe;
        BookMenuUI.AmigoInfo a = bookMenu.amigo(nome);
        return a != null ? a.classe : null;
    }

    private void atualizarEstadoPainel() {
        String nome = painelJogador != null ? painelJogador.nomeAberto() : null;
        if (nome == null) return;
        BookMenuUI.AmigoInfo a = bookMenu.amigo(nome);
        painelJogador.definirEstado(a != null, ignorados.contains(nome),
            a != null && a.pk, a != null && a.guild, a != null && a.seller);
    }

    /** Lista de ignorados fica no aparelho (por personagem): esconde chat
     * Local, de idioma e privado desse player, e recusa convite de party. */
    private void carregarIgnorados() {
        ignorados.clear();
        try {
            String salvo = Gdx.app.getPreferences("inverted_realms_ignorados").getString(local.nome, "");
            for (String n : salvo.split(",")) if (!n.trim().isEmpty()) ignorados.add(n.trim());
        } catch (Exception ignorado) { }
    }

    private void salvarIgnorados() {
        try {
            com.badlogic.gdx.Preferences prefs = Gdx.app.getPreferences("inverted_realms_ignorados");
            prefs.putString(local.nome, String.join(",", ignorados));
            prefs.flush();
        } catch (Exception ignorado) { }
    }

    /** Sprite do alvo atual dentro do botao: o quadro de animacao de AGORA
     * (anda junto com ele), player com as camadas de skin e cores. */
    private class AtorAlvo extends com.badlogic.gdx.scenes.scene2d.Actor {
        private final List<TextureRegion> quadros = new ArrayList<>();
        private final List<Color> cores = new ArrayList<>();
        // null = segue o alvo atual (botao do topo); com nome = sempre esse
        // player (foto da janela do jogador).
        private final String nomeFixo;
        // Desenhado quando o player nao esta em lugar nenhum (offline/longe
        // demais pra ter skin): ex. o icone da classe na aba de PV.
        private final TextureRegion reserva;
        // Player que nao esta online nem por perto (amigo offline): boneco
        // parado de frente, com a skin que o servidor mandou na lista de amigos.
        private final Jogador parado;

        AtorAlvo() { this(null, null); }
        AtorAlvo(String nomeFixo) { this(nomeFixo, null); }
        AtorAlvo(String nomeFixo, TextureRegion reserva) {
            this.nomeFixo = nomeFixo;
            this.reserva = reserva;
            this.parado = nomeFixo != null ? jogadorDeAmigoOffline(nomeFixo) : null;
        }

        @Override
        public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
            quadros.clear();
            cores.clear();
            MobVisual mob = nomeFixo == null && alvoMob != null ? mobs.get(alvoMob) : null;
            String nomeJogador = nomeFixo != null ? nomeFixo : amigoMarcado;
            Jogador j = nomeJogador != null ? remotos.get(nomeJogador) : null;
            // Foto fixa (janela do jogador/aba de PV): vale tambem quem esta
            // fora da tela (a skin dele continua guardada).
            if (j == null && nomeFixo != null) j = remotosForaDeVisao.get(nomeFixo);
            if (j == null) j = parado;
            if (mob != null && !mob.morto) {
                quadros.add(quadroAtual(mob.animacao, mob.andandoVisual(), mob.direcao, mob.progresso));
                cores.add(Color.WHITE);
            } else if (j != null) {
                List<CamadaSkin> camadas = skinsJogadores.get(j.nome);
                if (camadas == null || camadas.isEmpty()) {
                    quadros.add(quadroAtual(animacaoBase, j));
                    cores.add(Color.WHITE);
                } else {
                    for (CamadaSkin camada : camadas) {
                        TextureRegion q = quadroAtual(camada.animacao, j);
                        if (q == null) continue;
                        quadros.add(q);
                        cores.add(camada.cor);
                    }
                }
            }
            if (quadros.isEmpty() && reserva != null) {
                quadros.add(reserva);
                cores.add(Color.WHITE);
            }
            if (quadros.isEmpty()) return;
            // Escala inteira que cabe no botao (pixel art sem distorcer).
            float alturaQuadro = 0f;
            for (TextureRegion q : quadros) alturaQuadro = Math.max(alturaQuadro, q.getRegionHeight());
            float escala = Math.min(getWidth() / FRAME_LARGURA, getHeight() / alturaQuadro);
            // Escala inteira (pixel art certinho) quando da' 2x ou mais; entre
            // 1x e 2x, de meio em meio (senao caia pra 1x e ficava minusculo).
            if (escala >= 2f) escala = (float) Math.floor(escala);
            else if (escala >= 1f) escala = (float) Math.floor(escala * 2f) / 2f;
            float w = FRAME_LARGURA * escala;
            float x = getX() + (getWidth() - w) / 2f;
            float y = getY() + (getHeight() - alturaQuadro * escala) / 2f;
            Color anterior = new Color(batch.getColor());
            for (int i = 0; i < quadros.size(); i++) {
                TextureRegion q = quadros.get(i);
                Color c = cores.get(i);
                batch.setColor(c.r, c.g, c.b, c.a * parentAlpha);
                batch.draw(q, x, y, w, q.getRegionHeight() * escala);
            }
            batch.setColor(anterior);
        }
    }

    /** Qual balaozinho (se algum) deve estar visivel agora - um por tela
     * aberta, nenhuma prioridade especial entre elas (na pratica so' uma fica
     * aberta por vez, ja que abrir settings fecha o chat e vice-versa não -
     * mas se as 2 abrissem ao mesmo tempo, settings venceria aqui). */
    private TextureRegion notificacaoAtual() {
        if (settingsAberta()) return notifConfig;
        if (chat.isVisivel()) return notifChat;
        if (bookMenu.isVisible()) return notifMenu;
        return null;
    }

    // ---- Balaozinho (chat/menu/settings) em cima dos OUTROS players ----
    // servidor.py::handle_update_status repassa essas chaves pra area.
    // is_in_skins = livro (BookMenu) aberto.
    private static final String[] CHAVES_BALAO = {"is_in_settings", "is_typing", "is_in_skins"};
    private final Map<String, java.util.Set<String>> baloesRemotos = new HashMap<>();
    private final boolean[] balaoEnviado = new boolean[CHAVES_BALAO.length];

    private void definirBalaoRemoto(String nome, String chave, boolean ligado) {
        java.util.Set<String> estado = baloesRemotos.computeIfAbsent(nome, k -> new java.util.HashSet<>());
        if (ligado) estado.add(chave); else estado.remove(chave);
    }

    /** Mesma prioridade do proprio jogador: settings > chat > menu. */
    private TextureRegion balaoRemoto(String nome) {
        java.util.Set<String> estado = baloesRemotos.get(nome);
        if (estado == null || estado.isEmpty()) return null;
        if (estado.contains("is_in_settings")) return notifConfig;
        if (estado.contains("is_typing")) return notifChat;
        if (estado.contains("is_in_skins")) return notifMenu;
        return null;
    }

    // ---- Coroa/escudo da party: na altura do balao, mas a ESQUERDA do player ----
    private static final float TAM_ICONE_PARTY = 20f;

    private void desenharIconeParty(Jogador j) {
        if (j == local ? localMorto : estaMorto(j)) return;
        String lider = bookMenu.liderParty();
        TextureRegion icone = null;
        if (lider != null && bookMenu.estaNaParty(j.nome)) icone = j.nome.equals(lider) ? iconeLiderParty : iconeMembroParty;
        else if (j != local && (convitesPartyEnviados.contains(j.nome) || bookMenu.temConviteParty(j.nome))) {
            icone = iconeConviteParty; // eu convidei ele, ou ele me convidou
        }
        if (icone == null) return;
        // Balao: x+2 a direita do centro; aqui o espelho dele.
        // Centro vertical igual o do balao (y+9.5, ~8 de altura).
        // Encostado no lado esquerdo do corpo (o desenho tem sobra transparente).
        float ix = Math.round(j.x / camera.zoom) * camera.zoom + 9f - TAM_ICONE_PARTY;
        float iy = Math.round(j.y / camera.zoom) * camera.zoom + 13.5f - TAM_ICONE_PARTY / 2f;
        batch.draw(icone, ix, iy, TAM_ICONE_PARTY, TAM_ICONE_PARTY);
    }

    /** Chamado todo frame: avisa o servidor so' quando algo abriu/fechou. */
    private void enviarEstadoBalao() {
        if (!socket.isConnected()) return;
        boolean[] agora = {settingsAberta(), chat.isVisivel(), bookMenu.isVisible()};
        for (int i = 0; i < CHAVES_BALAO.length; i++) {
            if (agora[i] == balaoEnviado[i]) continue;
            balaoEnviado[i] = agora[i];
            String chave = CHAVES_BALAO[i];
            boolean valor = agora[i];
            socket.emitRaw("update_status", GameSocket.obj(jw -> {
                jw.set("key", chave);
                jw.set("value", valor);
            }));
        }
    }

    /** Popup pequeno (220x260 no Godot) - 3 botoes empilhados sem titulo
     * separado: o botao de cima ja se chama "Settings" e abre a tela cheia
     * (CanvasLayer/SettingsMenu/MarginContainer/VBoxContainer: OptionsBtn/
     * BackBtn/ExitBtn, nessa ordem). */
    // 200x70 no mobile (bom la, achado testando); reduzido no PC (ficava
    // grande demais, a pedido do usuario) - mesma proporcao largura/altura.
    private final float LARGURA_BOTAO_SETTINGS = mobile ? 200f : 160f;
    private final float ALTURA_BOTAO_SETTINGS = mobile ? 70f : 56f;

    private Table conteudoSettingsNormal, conteudoConfirmarSaida;

    private void mostrarConteudoSettings(Table conteudo) {
        painelSettings.clearChildren();
        painelSettings.add(conteudo);
    }

    /** "Sair mesmo em battle?" - no lugar do popup de Settings. */
    private Table criarConfirmacaoSaidaEmBatalha() {
        Table conteudo = new Table();
        conteudo.setBackground(skin.getDrawable("popup-painel"));
        conteudo.pad(15, 14, 15, 14);
        Label aviso = new Label("Your soul may rest, but your body will remain in battle. Continue?", skin, "default");
        aviso.setWrap(true);
        aviso.setAlignment(Align.center);
        conteudo.add(aviso).width(LARGURA_BOTAO_SETTINGS * 1.8f).colspan(2).padBottom(14).row();
        TextButton sim = new TextButton("Yes", skin, "vermelho-popup");
        sim.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                sim.setDisabled(true);
                salvarPosicaoESair();
            }
        });
        TextButton nao = new TextButton("No", skin, "verde-popup");
        nao.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                mostrarConteudoSettings(conteudoSettingsNormal);
            }
        });
        float largura = LARGURA_BOTAO_SETTINGS * 0.8f;
        conteudo.add(nao).width(largura).height(ALTURA_BOTAO_SETTINGS).padRight(10);
        conteudo.add(sim).width(largura).height(ALTURA_BOTAO_SETTINGS);
        return conteudo;
    }

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
                // Em battle: avisa que o corpo fica no jogo ate' o battle acabar.
                if (hud.emBatalha()) {
                    mostrarConteudoSettings(conteudoConfirmarSaida);
                    return;
                }
                botaoExit.setDisabled(true);
                salvarPosicaoESair();
            }
        });
        conteudo.add(botaoExit).padBottom(0);
        return conteudo;
    }

    // ===================== CONTROLES (Settings) =====================
    // Moving style (4/8 direcoes) nos dois. PC: tecla de cada acao (hotbar
    // 1-9, Open Menu, Open Bag, Open Chat, Write) - clica no botao da tecla e
    // aperta a nova (ESC cancela). Celular: "Edit controls" abre o editor
    // (HotbarUI) do joystick e dos botoes juntos. Tudo salvo no aparelho (Controles).

    private final java.util.Map<String, TextButton> botoesTecla = new java.util.HashMap<>();
    private String acaoCapturando = null;
    private Label avisoControles;

    private TextButton.TextButtonStyle estiloTecla(boolean capturando) {
        TextButton.TextButtonStyle e = new TextButton.TextButtonStyle();
        e.font = skin.getFont("botao-pequeno-font");
        e.fontColor = capturando ? new Color(1f, 0.85f, 0.3f, 1f) : Color.WHITE;
        Color borda = capturando ? new Color(0.95f, 0.65f, 0.24f, 1f) : new Color(0.38f, 0.38f, 0.38f, 1f);
        e.up = UiSkin.retangulo(new Color(0.13f, 0.13f, 0.13f, 1f), borda, 2);
        e.over = UiSkin.retangulo(new Color(0.19f, 0.19f, 0.19f, 1f), capturando ? borda : new Color(0.55f, 0.55f, 0.55f, 1f), 2);
        e.down = UiSkin.retangulo(new Color(0.09f, 0.09f, 0.09f, 1f), borda, 2);
        return e;
    }

    private TextButton botaoPequeno(String texto, Runnable acao) {
        TextButton.TextButtonStyle e = new TextButton.TextButtonStyle(skin.get("default", TextButton.TextButtonStyle.class));
        e.font = skin.getFont("botao-pequeno-font");
        TextButton b = new TextButton(texto, e);
        b.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { acao.run(); }
        });
        return b;
    }

    private Table criarSecaoControles() {
        Table secao = new Table();
        secao.left().top();
        secao.add(new Label("Controls", skin, "secao")).left().row();
        // Moving style: 8 direcoes (com diagonal) ou 4 (sem) - PC e celular.
        Table linhaMov = new Table();
        linhaMov.add(new Label("Moving style", skin, "opcoes-label")).align(Align.right).width(200).padRight(10);
        SelectBox<String> estiloMov = new SelectBox<>(skin, "zoom-select");
        estiloMov.setItems("8 directions", "4 directions");
        estiloMov.setSelectedIndex(Controles.oitoDirecoes() ? 0 : 1);
        estiloMov.setAlignment(Align.center);
        estiloMov.getList().setAlignment(Align.center);
        estiloMov.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                Controles.definirOitoDirecoes(estiloMov.getSelectedIndex() == 0);
            }
        });
        linhaMov.add(estiloMov).width(180).height(36);
        secao.add(linhaMov).left().padTop(10).row();
        if (!mobile) {
            Table grade = new Table();
            int coluna = 0;
            for (java.util.Map.Entry<String, String> e : Controles.NOMES.entrySet()) {
                final String acao = e.getKey();
                Label nome = new Label(e.getValue(), skin, "opcoes-label");
                TextButton tecla = new TextButton(Controles.nomeTecla(Controles.tecla(acao)), estiloTecla(false));
                tecla.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                        comecarCaptura(acao);
                    }
                });
                botoesTecla.put(acao, tecla);
                grade.add(nome).align(Align.right).width(200).pad(5, 6, 5, 10);
                grade.add(tecla).width(96).height(36).pad(5, 0, 5, 30);
                if (++coluna % 2 == 0) grade.row();
            }
            secao.add(grade).left().padTop(14).row();
            avisoControles = new Label("", skin, "opcoes-label");
            avisoControles.setColor(1f, 0.85f, 0.3f, 1f);
            secao.add(avisoControles).left().padTop(6).row();
            secao.add(botaoPequeno("Reset to default", () -> {
                cancelarCaptura();
                Controles.restaurarPadrao();
                atualizarBotoesTecla();
                avisoControles.setText("Controls reset to default.");
            })).left().width(200).height(38).padTop(8).row();
            // Captura a proxima tecla (no uiStage: vem antes do jogo, entao a
            // tecla escolhida nao dispara a acao dela nesse mesmo aperto).
            uiStage.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                @Override public boolean keyDown(com.badlogic.gdx.scenes.scene2d.InputEvent event, int keycode) {
                    if (acaoCapturando == null) return false;
                    String acao = acaoCapturando;
                    cancelarCaptura();
                    if (keycode == Input.Keys.ESCAPE) return true;
                    String outra = Controles.definir(acao, keycode);
                    atualizarBotoesTecla();
                    if ("".equals(outra)) {
                        avisoControles.setText(Controles.nomeTecla(keycode) + " is reserved (movement, ESC or C).");
                        marcarConflito(acao, keycode, null);
                    } else if (outra != null) {
                        // Ja' usada: nao troca; as duas aparecem em vermelho.
                        avisoControles.setText("");
                        marcarConflito(acao, keycode, outra);
                    } else {
                        avisoControles.setText("");
                    }
                    return true;
                }
            });
        } else {
            secao.add(botaoPequeno("Edit controls", () -> {
                fecharSettings();
                hotbar.entrarEdicao(null);
            })).left().width(210).height(48).padTop(10).row();
        }
        return secao;
    }

    /** Tecla recusada: o botao da acao mostra a tecla tentada e ele (e o da
     * acao que ja' usa essa tecla) ficam em vermelho, ate' o proximo clique. */
    private void marcarConflito(String acao, int keycode, String outra) {
        Color vermelho = new Color(1f, 0.3f, 0.3f, 1f);
        TextButton b = botoesTecla.get(acao);
        if (b != null) {
            TextButton.TextButtonStyle e = estiloTecla(false);
            e.fontColor = vermelho;
            e.up = UiSkin.retangulo(new Color(0.13f, 0.13f, 0.13f, 1f), vermelho, 2);
            b.setStyle(e);
            b.setText(Controles.nomeTecla(keycode));
        }
        TextButton o = outra != null ? botoesTecla.get(outra) : null;
        if (o != null) {
            TextButton.TextButtonStyle e = estiloTecla(false);
            e.fontColor = vermelho;
            e.up = UiSkin.retangulo(new Color(0.13f, 0.13f, 0.13f, 1f), vermelho, 2);
            o.setStyle(e);
        }
    }

    private void comecarCaptura(String acao) {
        cancelarCaptura();
        acaoCapturando = acao;
        TextButton b = botoesTecla.get(acao);
        if (b != null) { b.setStyle(estiloTecla(true)); b.setText("Press..."); }
        if (avisoControles != null) avisoControles.setText("");
        uiStage.setKeyboardFocus(null); // a tecla chega na raiz do stage (listener acima)
    }

    private void cancelarCaptura() {
        acaoCapturando = null;
        atualizarBotoesTecla();
    }

    private void atualizarBotoesTecla() {
        for (java.util.Map.Entry<String, TextButton> e : botoesTecla.entrySet()) {
            e.getValue().setStyle(estiloTecla(e.getKey().equals(acaoCapturando)));
            e.getValue().setText(e.getKey().equals(acaoCapturando) ? "Press..." : Controles.nomeTecla(Controles.tecla(e.getKey())));
        }
        if (hotbar != null) hotbar.atualizar(); // o numero da tecla em cada slot
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
        botaoZoom.setAlignment(Align.center);
        botaoZoom.getList().setAlignment(Align.center);
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
        corpo.add(grid).left().row();
        corpo.add(criarSecaoControles()).left().padTop(26).row();

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
        if (abrindo) mostrarConteudoSettings(conteudoSettingsNormal);
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
        mostrarConteudoSettings(conteudoSettingsNormal);
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
                // Correcao do servidor no meio de um passo: o passo em
                // andamento nao pode continuar e levar o player de volta.
                local.movendo = false;
            }
            // Fumaca so' quando entra no jogo (sync completo), nao a cada
            // correcao de posicao (passo recusado pelo servidor).
            if (data.has("inventory")) {
                spawnSmokeX = local.x;
                spawnSmokeY = local.y;
                spawnSmokeTempo = 0f;
            }
            local.direcao = data.getString("direction", "down");
            // O servidor tambem manda sync_local_player PARCIAL (so' posicao,
            // ao esbarrar num NPC) - sem esse if, inventario/moedas/skills
            // eram zerados no client.
            if (data.has("inventory")) {
                if (socket.isConnected()) socket.emitRaw("get_friends_list", "{}");
                if (socket.isConnected()) socket.emitRaw("get_party_status", "{}");
                definirSkins(local.nome, data.get("skins"));
                bookMenu.carregarSkins(data.get("skin_db"), data.get("skins"));
                // HP/MP: -1 no banco = cheio.
                float maxHp = data.getFloat("max_hp", -1f), maxMp = data.getFloat("max_mp", -1f);
                float hpJoin = data.getFloat("current_hp", -1f), mpJoin = data.getFloat("current_mp", -1f);
                hud.definir(hpJoin < 0f ? maxHp : hpJoin, maxHp, mpJoin < 0f ? maxMp : mpJoin, maxMp);
                hud.definirXp(data.getInt("level", 1), data.getLong("exp", 0L));
                bookMenu.carregarItemDb(data.get("item_db"));
                bookMenu.atualizarInventario(data.get("inventory"));
                if (data.has("hotbar")) bookMenu.definirHotbar(data.get("hotbar"));
                bookMenu.atualizarMoedas(data.getLong("currency", 0L));
                bookMenu.atualizarEquipados(data.get("equipped_items"));
                atualizarMunicao(data.get("equipped_items"));
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
            // (Re)entrou no jogo: volta pros chats extras que estavam abertos
            // (o servidor esquece os canais de quem desconecta). item_db so' vem
            // no sync completo do join_game - nao na correcao de posicao.
            if (chat != null && data.has("item_db")) for (String canal : chat.canaisAbertos()) emitirCanalChat("chat_join", canal);
        });

        socket.on("current_players", (nomeEvt, data) -> {
            if (data == null) return;
            for (JsonValue p = data.child; p != null; p = p.next) {
                adicionarRemotoSeNovo(p);
            }
        });

        socket.on("player_joined", (nomeEvt, data) -> {
            adicionarRemotoSeNovo(data);
            if (data != null) {
                String nome = data.getString("name", "");
                if (bookMenu.amigo(nome) != null) hud.notificar(nome + " is online.", COR_NOTIF_ON, null);
                bookMenu.definirOnline(nome, true);
            }
        });

        // ---- Amigos (servidor.py::toggle_friend/get_friends_list/...) ----
        socket.on("friends_list", (nomeEvt, data) -> bookMenu.atualizarAmigos(data));
        socket.on("friend_status", (nomeEvt, data) -> {
            if (data == null) return;
            String nome = data.getString("friend_name", "");
            boolean amigo = data.getBoolean("is_friend", false);
            bookMenu.definirAmigo(nome, amigo);
            chat.adicionarMensagemSistema(amigo ? nome + " was added to your friends." : nome + " was removed from your friends.");
        });
        socket.on("friend_icon_updated", (nomeEvt, data) -> {
            if (data == null) return;
            bookMenu.definirIconeAmigo(data.getString("friend_name", ""), data.getString("icon", ""), data.getBoolean("value", false));
        });
        socket.on("friend_add_result", (nomeEvt, data) -> {
            if (data == null) return;
            bookMenu.resultadoAdicionarAmigo(data.getBoolean("success", false), data.getString("friend_name", ""),
                data.getString("reason", ""));
        });

        // ---- Mensagem privada: {from, to, msg, class, to_class} ----
        socket.on("pm", (nomeEvt, data) -> {
            if (data == null || chat == null) return;
            String de = data.getString("from", ""), para = data.getString("to", "");
            boolean minha = de.equals(local.nome);
            if (!minha && ignorados.contains(de)) return;
            String outro = minha ? para : de;
            String iconeOutro = BookMenuUI.iconeClasse(minha ? data.getString("to_class", "Knight") : data.getString("class", "Knight"));
            chat.adicionarMensagemPrivada(outro, nomeVisivel(outro), iconeOutro, nomeVisivel(de),
                ChatUI.corDaClasse(data.getString("class", "Knight")), data.getString("msg", ""));
            if (!minha) avisarMensagemNova(de);
        });

        // ---- Party ----
        socket.on("party_invite_received", (nomeEvt, data) -> {
            if (data == null) return;
            String quem = data.getString("inviter_name", "");
            if (ignorados.contains(quem)) {
                socket.emitRaw("decline_party_invite", GameSocket.obj(w -> w.set("inviter_name", quem)));
                return;
            }
            // Vira uma linha (Confirm/Negate) na aba Party; a notificacao leva pra la'.
            bookMenu.adicionarConviteParty(quem, data.getInt("inviter_level", 1));
            hud.notificar(quem + " invited you to a party.", COR_NOTIF_PARTY, this::abrirAbaParty);
        });
        socket.on("party_invite_result", (nomeEvt, data) -> {
            if (data == null) return;
            String alvo = data.getString("target_name", "");
            if (data.getBoolean("success", false)) {
                convitesPartyEnviados.add(alvo);
                hud.notificar("Party invite sent to " + alvo + ".");
                return;
            }
            String motivo = data.getString("reason", "");
            String texto = "party_full".equals(motivo) ? "Your party is full."
                : "offline".equals(motivo) || "not_found".equals(motivo) ? alvo + " is offline."
                : "already_in_your_party".equals(motivo) ? alvo + " is already in your party."
                : "already_in_party".equals(motivo) ? alvo + " is already in a party."
                : "not_leader".equals(motivo) ? "Only the party leader can invite."
                : "Could not invite " + alvo + ".";
            hud.notificar(texto, COR_NOTIF_AVISO, null);
        });
        socket.on("party_invite_declined", (nomeEvt, data) -> {
            if (data == null) return;
            convitesPartyEnviados.remove(data.getString("target_name", ""));
            hud.notificar(data.getString("target_name", "") + " declined the party invite.");
        });
        socket.on("party_update", (nomeEvt, data) -> {
            if (data == null) return;
            // Quem entrou/saiu (comparando com a party que eu ja' tinha).
            String antigoLider = bookMenu.liderParty();
            java.util.Set<String> antes = new java.util.HashSet<>();
            for (BookMenuUI.MembroParty m : bookMenu.membrosParty()) antes.add(m.nome);
            bookMenu.atualizarParty(data);
            java.util.Set<String> depois = new java.util.HashSet<>();
            for (BookMenuUI.MembroParty m : bookMenu.membrosParty()) depois.add(m.nome);
            convitesPartyEnviados.removeAll(depois); // aceitou: vira escudo
            if (antigoLider == null) {
                if (depois.size() > 1) hud.notificar("You joined " + bookMenu.liderParty() + "'s party.", COR_NOTIF_PARTY, this::abrirAbaParty);
                else hud.notificar("Party created.", COR_NOTIF_PARTY, null);
                return;
            }
            for (String n : depois) {
                if (!antes.contains(n) && !n.equals(local.nome)) hud.notificar(n + " joined the party.", COR_NOTIF_PARTY, null);
            }
            for (String n : antes) {
                if (!depois.contains(n)) hud.notificar(n + " left the party.");
            }
            String lider = bookMenu.liderParty();
            if (lider != null && !lider.equals(antigoLider)) {
                hud.notificar(lider.equals(local.nome) ? "You are now the party leader." : lider + " is now the party leader.",
                    COR_NOTIF_PARTY, null);
            }
        });
        socket.on("party_disbanded", (nomeEvt, data) -> {
            boolean estava = bookMenu.liderParty() != null;
            bookMenu.limparParty();
            convitesPartyEnviados.clear(); // convites da party antiga morrem junto
            if (!estava || data == null) return;
            String motivo = data.getString("reason", "");
            if ("kicked".equals(motivo)) hud.notificar("You were removed from the party.", COR_NOTIF_AVISO, null);
            else if ("left".equals(motivo)) hud.notificar("You left the party.");
            else if ("auto_disband".equals(motivo)) hud.notificar("Your party was disbanded.");
        });
        // ---- Trade (servidor.py, TRADE) ----
        socket.on("trade_invite_received", (nomeEvt, data) -> {
            if (data == null) return;
            String quem = data.getString("inviter_name", "");
            if (ignorados.contains(quem)) return;
            convitesTrade.add(quem); // balaozinho "Trade" em cima dele (so' pra mim)
            hud.notificar(quem + " wants to trade.", COR_NOTIF_PARTY, () -> {
                if (socket.isConnected() && convitesTrade.contains(quem)) {
                    socket.emitRaw("accept_trade_invite", GameSocket.obj(w -> w.set("inviter_name", quem)));
                }
            });
        });
        socket.on("trade_pending_status", (nomeEvt, data) -> {
            if (data == null) return;
            if (!data.getBoolean("pending", false)) convitesTrade.remove(data.getString("inviter_name", ""));
        });
        socket.on("trade_invite_result", (nomeEvt, data) -> {
            if (data == null) return;
            String alvo = data.getString("target_name", "");
            if (data.getBoolean("success", false)) {
                hud.notificar("Trade request sent to " + alvo + ".");
                return;
            }
            String motivo = data.getString("reason", "");
            String texto = "too_far".equals(motivo) ? alvo + " is too far away."
                : "target_busy".equals(motivo) ? alvo + " is busy."
                : "already_pending".equals(motivo) ? "You already sent a trade request to " + alvo + "."
                : "timeout".equals(motivo) ? "Trade request to " + alvo + " expired."
                : "offline".equals(motivo) ? alvo + " is offline."
                : "Could not trade with " + alvo + ".";
            hud.notificar(texto, COR_NOTIF_AVISO, null);
        });
        socket.on("trade_started", (nomeEvt, data) -> {
            if (data == null) return;
            String outro = data.getString("other_name", "");
            convitesTrade.remove(outro);
            if (painelJogador.isVisivel()) painelJogador.fechar();
            if (settingsAberta()) fecharSettings();
            if (chat.isVisivel()) chat.setVisivel(false);
            bookMenu.abrirTrade(outro);
            atualizarVisibilidadeJoystick();
        });
        socket.on("trade_offer_updated", (nomeEvt, data) -> bookMenu.atualizarOfertasTrade(data));
        socket.on("trade_advanced_to_confirm", (nomeEvt, data) -> bookMenu.mostrarConfirmacaoTrade(local.nome));
        socket.on("trade_lock_state_updated", (nomeEvt, data) -> {
            if (data != null) bookMenu.estadoAceiteTrade(data.getBoolean("self_locked", false), data.getBoolean("other_locked", false));
        });
        socket.on("trade_cancelled", (nomeEvt, data) -> {
            bookMenu.fecharTrade();
            atualizarVisibilidadeJoystick();
            String motivo = data != null ? data.getString("reason", "") : "";
            hud.notificar("invalid_offer".equals(motivo) ? "Trade failed: the offer is no longer valid."
                : "disconnected".equals(motivo) ? "Trade cancelled: the player left."
                : "Trade cancelled.", COR_NOTIF_AVISO, null);
        });
        socket.on("trade_executed", (nomeEvt, data) -> {
            bookMenu.fecharTrade();
            atualizarVisibilidadeJoystick();
            if (data != null && data.has("new_currency")) bookMenu.atualizarMoedas(data.getLong("new_currency", 0L));
            hud.notificar("Trade successful!");
        });

        // Chat Local: [nome, mensagem (ja censurada), classe] de quem esta na
        // area (inclusive o proprio jogador, que recebe a dele de volta).
        socket.on("c", (nomeEvt, data) -> {
            if (data == null || !data.isArray() || data.size < 2 || chat == null) return;
            String nome = data.get(0).asString();
            String texto = data.get(1).asString();
            Color cor = ChatUI.corDaClasse(data.size > 2 ? data.get(2).asString() : "Knight");
            if (nome == null || texto == null) return;
            if (ignorados.contains(nome)) return; // ignorado: nem chat nem balao
            chat.adicionarMensagemLocal(nomeVisivel(nome), cor, texto);
            avisarMensagemNova(nome);
            falas.put(nome, new Fala(nome, texto, cor));
        });
        // Chat da party: {name, msg, class}. Log na aba Party e balao de fala
        // amarelo em cima de quem falou (so' se ele estiver na minha tela).
        socket.on("pc", (nomeEvt, data) -> {
            if (data == null || chat == null) return;
            String nome = data.getString("name", "");
            String texto = data.getString("msg", "");
            if (ignorados.contains(nome)) return;
            Color cor = ChatUI.corDaClasse(data.getString("class", "Knight"));
            chat.adicionarMensagemParty(nome, cor, texto);
            avisarMensagemNova(nome);
            // Balao todo amarelo (nome e texto); no log fica igual o Local.
            Fala f = new Fala(nome, texto, ChatUI.COR_MSG_PARTY);
            f.corTexto = ChatUI.COR_MSG_PARTY;
            falas.put(nome, f);
        });
        // Chat de idioma: {channel, name, msg (ja censurada), class}.
        socket.on("cc", (nomeEvt, data) -> {
            if (data == null || chat == null) return;
            if (ignorados.contains(data.getString("name", ""))) return;
            chat.adicionarMensagemCanal(data.getString("channel", ""), nomeVisivel(data.getString("name", "")),
                ChatUI.corDaClasse(data.getString("class", "Knight")), data.getString("msg", ""));
            avisarMensagemNova(data.getString("name", ""));
        });
        // Alguem da area (ou eu) morreu pra um mob: aviso no chat Local.
        socket.on("player_killed", (nomeEvt, data) -> {
            if (data == null || chat == null) return;
            MobVisual mob = mobs.get(data.getString("mob_id", ""));
            String nomeMob = mob != null ? mob.nome : data.getString("mob_type", "monster");
            chat.adicionarMensagemSistema(nomeVisivel(data.getString("name", "")) + " was killed by " + nomeMob + ".",
                new Color(1f, 0.25f, 0.25f, 1f));
            // Eu morri: mostra quem matou na tela de morte.
            if (data.getString("name", "").equals(local.nome) && textoMortoPor != null) {
                textoMortoPor.setText("Slain by " + nomeMob);
            }
        });

        // Aviso do servidor so' pra esse jogador (anti-spam/mute). "red" =
        // mute de 1 hora (spam ou palavrao demais).
        socket.on("chat_system", (nomeEvt, data) -> {
            if (data == null || chat == null) return;
            String texto = data.getString("text", "");
            if ("red".equals(data.getString("color", ""))) chat.adicionarMensagemSistema(texto, ChatUI.COR_PUNICAO);
            else chat.adicionarMensagemSistema(texto);
        });

        // Membros de um chat extra (Portuguese/Spanish/...), igual grupo: o
        // servidor manda a lista toda vez que alguem entra/sai do canal.
        socket.on("chat_members", (nomeEvt, data) -> {
            if (data == null || chat == null) return;
            java.util.List<String> nomes = new java.util.ArrayList<>();
            JsonValue lista = data.get("names");
            if (lista != null) for (JsonValue n = lista.child; n != null; n = n.next) nomes.add(nomeVisivel(n.asString()));
            chat.setMembrosDoCanal(data.getString("channel", ""), nomes);
        });

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
            boolean errou = data.getBoolean("is_miss", false);
            NumeroDano numero = new NumeroDano(mob.x, mob.y, errou ? "Miss" : data.getInt("damage", 0) + (critico ? "!" : ""), critico);
            // Miss do player no mob: mesmo amarelo do "Miss" do mob no player.
            if (errou) numero.cor = new Color(1f, 0.9f, 0.1f, 1f);
            // Ranged colado num mob de outro player: dano cortado, numero amarelo.
            if (data.getBoolean("reduced", false)) numero.cor = new Color(1f, 0.9f, 0.1f, 1f);
            // Efeito de hit; a distancia, primeiro o projetil sai do atacante.
            String efeito = data.getString("hit_type", "physical_hit");
            Jogador atacante = jogadorPorNome(data.getString("attacker_id", ""));
            TextureRegion projetil = regiaoDoCaminho(data.getString("proj", ""));
            if ("Ranged".equals(data.getString("w_type", "")) && projetil != null && atacante != null) {
                Projetil pr = new Projetil(projetil, atacante.x, atacante.y + 8f, mob, efeito);
                pr.numero = numero;
                projeteis.add(pr);
            } else {
                tocarEfeitoNoMob(efeito, mob);
                numerosDano.add(numero);
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
                int dano = data.getInt("damage", 0);
                // Dano 0 = o player desviou (servidor.py: BLOCK_CHANCE, agora "Dodge"):
                // "Miss" em amarelo.
                NumeroDano nd = new NumeroDano(j.x, j.y + 4f, dano <= 0 ? "Miss" : String.valueOf(dano), false);
                if (dano <= 0) nd.cor = new Color(1f, 0.9f, 0.1f, 1f);
                numerosDano.add(nd);
                Efeito e = tocarEfeito(data.getString("hit_type", "physical_hit"), j.x, j.y);
                if (e != null) e.jogadorAlvo = j;
            }
            if (!alvo.equals(local.nome) && data.has("new_hp")) {
                vidaRemotos.put(alvo, new float[]{data.getFloat("new_hp", 1f), data.getFloat("max_hp", 1f)});
            }
            if (alvo.equals(local.nome)) {
                // Levar dano treina defense (o servidor so' conta se o player esta lutando).
                socket.emitRaw("register_skill_hit", GameSocket.obj(jw -> jw.set("skill", "defense")));
                float hpNovo = data.getFloat("new_hp", hud.hpAtual());
                hud.definir(hpNovo, data.getFloat("max_hp", -1f), -1f, -1f);
                if (hpNovo <= 0f) morrerLocal();
            }
        });
        socket.on("sync_vitals", (nomeEvt, data) -> {
            if (data == null) return;
            hud.definir(data.getFloat("current_hp", -1f), data.getFloat("max_hp", -1f),
                data.getFloat("current_mp", -1f), data.getFloat("max_mp", -1f));
            // Flecha gasta (o servidor manda a quantidade restante a cada tiro).
            // Varinha sem mana: o servidor recusou o golpe.
            if (data.getBoolean("no_mana", false)) avisarSemMana();
            if (data.has("ammo_qty") && caminhoMunicao != null) {
                int antes = quantidadeMunicao;
                quantidadeMunicao = data.getInt("ammo_qty", quantidadeMunicao);
                if (antes > 0 && quantidadeMunicao <= 0) {
                    hud.notificar("Your arrows ran out!", COR_NOTIF_AVISO, null);
                    avisouSemMunicao = true; // ja' avisou agora
                }
                hud.definirMunicao(bookMenu.iconeDoItem(caminhoMunicao), quantidadeMunicao);
                bookMenu.definirQuantidadeEquipada(SLOT_MUNICAO, quantidadeMunicao);
            }
        });
        // XP ganha ao matar um mob: sobe em branco em cima do player, igual o dano.
        socket.on("xp_gained", (nomeEvt, data) -> {
            if (data == null) return;
            int xp = data.getInt("amount", 0);
            if (xp <= 0) return;
            NumeroDano n = new NumeroDano(local.x, local.y + 4f, String.valueOf(xp), false);
            n.cor = Color.WHITE;
            numerosDano.add(n);
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
        // Mob vai renascer em ~6s nesse SQM (servidor ja' sorteou onde).
        socket.on("mob_spawn_warning", (nomeEvt, data) -> {
            if (data == null || quadrosAvisoSpawn == null) return;
            float mx = conversor.rawParaMundoX(data.getFloat("pos_x", 0f));
            float my = conversor.rawParaMundoY(data.getFloat("pos_y", 0f));
            avisosSpawn.add(new AvisoSpawn(mx, my, Math.max(1f, data.getFloat("seconds", 6f))));
            // O cadaver desse mob comeca a sumir (transparente) assim que o aviso aparece.
            MobVisual mob = mobs.get(data.getString("mob_id", ""));
            if (mob != null && mob.morto && mob.tempoCorpo > FADE_CORPO) mob.tempoCorpo = FADE_CORPO;
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
            remotosForaDeVisao.remove(data.getString("name", ""));
            baloesRemotos.remove(data.getString("name", ""));
            convitesTrade.remove(data.getString("name", ""));
            convitesPartyEnviados.remove(data.getString("name", ""));
            bookMenu.removerConviteParty(data.getString("name", ""));
            if (bookMenu.amigo(data.getString("name", "")) != null && !data.getString("name", "").equals(local.nome)) {
                hud.notificar(data.getString("name", "") + " is offline.", COR_NOTIF_OFF, null);
            }
            bookMenu.definirOnline(data.getString("name", ""), false);
            if (data.getString("name", "").equals(painelJogador.nomeAberto())) painelJogador.fechar();
            skinsJogadores.remove(data.getString("name", ""));
            vidaRemotos.remove(data.getString("name", ""));
            remotosMortos.remove(data.getString("name", ""));
            if (data.getString("name", "").equals(amigoMarcado)) amigoMarcado = null;
        });

        // Skins: confirmacao das minhas (depois do Equip na aba Vanity) e
        // troca de skin de outro jogador da area.
        socket.on("skins_synced", (nomeEvt, data) -> {
            if (data == null) return;
            definirSkins(local.nome, data.get("skins"));
            bookMenu.atualizarSkinsEquipadas(data.get("skins"));
        });
        // HP dos outros players (servidor.py::broadcast_hp) - so' pra cor do nome.
        socket.on("player_status_updated", (nomeEvt, data) -> {
            if (data == null) return;
            // Balaozinho em cima da cabeca: abriu/fechou chat, menu ou settings.
            for (String chave : CHAVES_BALAO) {
                if (data.has(chave)) definirBalaoRemoto(data.getString("name", ""), chave, data.getBoolean(chave, false));
            }
            // Outro player morreu / renasceu: troca pro quadro de morte (ou volta).
            if (data.has("is_dead")) {
                String nomeMorto = data.getString("name", "");
                if (data.getBoolean("is_dead", false)) {
                    if (remotosMortos.add(nomeMorto)) {
                        Jogador j = remotos.get(nomeMorto);
                        if (j != null) j.movendo = false;
                        float[] vida = vidaRemotos.get(nomeMorto);
                        if (vida != null) vida[0] = 0f;
                    }
                } else if (remotosMortos.remove(nomeMorto)) {
                    Jogador j = remotos.get(nomeMorto);
                    if (j != null) deixarCadaver(j);
                    // Fumaca de spawn onde ele renasceu (mesmo efeito do proprio respawn).
                    if (j != null) efeitos.add(new Efeito(quadrosFumaca(), j.x, j.y));
                }
            }
            if (!data.has("current_hp")) return;
            String nome = data.getString("name", "");
            float[] vida = vidaRemotos.get(nome);
            if (vida == null) vida = new float[]{1f, 1f};
            vida[0] = data.getFloat("current_hp", vida[0]);
            vida[1] = data.getFloat("max_hp", vida[1]);
            vidaRemotos.put(nome, vida);
        });
        // Subiu de level / de skill: texto subindo em cima do player (inclusive
        // de outros players da area - o servidor manda pra area toda).
        socket.on("player_leveled_up", (nomeEvt, data) -> {
            if (data == null) return;
            String nome = data.getString("name", "");
            Jogador j = jogadorPorNome(nome);
            if (j != null) textosFlutuantes.add(new TextoFlutuante(j, "Level " + data.getInt("level", 0) + "!",
                new Color(1f, 0.84f, 0.2f, 1f)));
            // Aviso no chat Local de todo mundo da area (o servidor ja manda pra area toda).
            if (chat != null) chat.adicionarMensagemSistema(nomeVisivel(nome) + " has reached Level " + data.getInt("level", 0) + "!");
        });
        // Textinho de acao em cima da cabeca (servidor.py::texto_de_acao): comer
        // ("Om Noom"), e depois magias/pocoes/quests. Vem pra area toda.
        socket.on("action_text", (nomeEvt, data) -> {
            if (data == null) return;
            Jogador j = jogadorPorNome(data.getString("name", ""));
            if (j == null) return;
            Color cor;
            try { cor = Color.valueOf(data.getString("color", "ff9a1f")); }
            catch (RuntimeException e) { cor = new Color(1f, 0.6f, 0.12f, 1f); }
            textosAcao.add(new TextoAcao(j, data.getString("text", ""), cor));
        });
        socket.on("player_skill_leveled_up", (nomeEvt, data) -> {
            if (data == null) return;
            String nome = data.getString("name", "");
            Jogador j = jogadorPorNome(nome);
            String skill = data.getString("skill_name", "");
            if (j != null) textosFlutuantes.add(new TextoFlutuante(j,
                nomeSkill(skill) + " " + data.getInt("new_level", 0) + "!", corSkill(skill)));
            if (chat != null) chat.adicionarMensagemSistema(nomeVisivel(nome) + " has reached " + nomeSkill(skill)
                + " level " + data.getInt("new_level", 0) + "!");
        });
        socket.on("player_skins_updated", (nomeEvt, data) -> {
            if (data == null) return;
            definirSkins(data.getString("name", ""), data.get("skins"));
        });

        socket.on("position_saved", (nomeEvt, data) -> finalizarSaidaAposSalvar());

        // Entrou/saiu de battle (servidor.py::marcar_batalha / battle_loop).
        socket.on("battle_state", (nomeEvt, data) -> {
            if (data == null) return;
            hud.definirBatalha(data.getBoolean("in_battle", false), data.getBoolean("counting", false),
                data.getFloat("seconds", 30f));
        });
        socket.on("food_result", (nomeEvt, data) -> {
            if (data == null || data.getBoolean("ok", false)) return;
            String motivo = data.getString("reason", "");
            if ("full".equals(motivo)) hud.notificar("You are full.", COR_NOTIF_AVISO, null);
            else if ("none".equals(motivo)) hud.notificar("You don't have this item.", COR_NOTIF_AVISO, null);
        });
        socket.on("hotbar_synced", (nomeEvt, data) -> {
            if (data != null) bookMenu.definirHotbar(data.get("hotbar"));
        });
        socket.on("inventory_synced", (nomeEvt, data) -> {
            if (data == null) return;
            bookMenu.atualizarInventario(data.get("inventory"));
            bookMenu.atualizarEquipados(data.get("equipped_items"));
            atualizarMunicao(data.get("equipped_items"));
        });

        socket.on("loot_collected", (nomeEvt, data) -> {
            if (data == null) return;
            bookMenu.atualizarMoedas(data.getLong("currency_total", 0L));
            bookMenu.adicionarItens(data.get("items"));
            if (!data.getBoolean("already_taken", false)) mostrarLootPego(data);
            if (data.getBoolean("bag_esvaziada", false) || data.getBoolean("already_taken", false)) {
                removerBag(data.getString("loot_id", ""));
            }
        });

        socket.on("sync_stats", (nomeEvt, data) -> {
            if (data == null) return;
            bookMenu.atualizarCapacidade(data.getFloat("cap_atual", 0f), data.getFloat("cap_maximo", 100f));
            bookMenu.atualizarSkills(data.get("skills"), data.getInt("level", 1), data.getInt("exp", 0), data.getInt("kills", 0));
            // Com fome (Fullness < 10, servidor.py::FOME_LIMITE): icone e 10% mais lento.
            JsonValue skillsFome = data.get("skills");
            boolean comFome = (skillsFome != null ? skillsFome.getFloat("fullness", BookMenuUI.FULLNESS_MAX) : BookMenuUI.FULLNESS_MAX) < FOME_LIMITE;
            hud.definirFome(comFome);
            multFome = comFome ? FOME_MULT_VELOCIDADE : 1f;
            hud.definir(data.getFloat("current_hp", -1f), data.getFloat("max_hp", -1f),
                data.getFloat("current_mp", -1f), data.getFloat("max_mp", -1f));
            if (data.has("level")) hud.definirXp(data.getInt("level", 1), data.getLong("exp", 0L));
        });

        // Troca de lugar aceita pelo servidor: [nomeA, xA, yA, dirA, nomeB, xB, yB, dirB].
        socket.on("swap_exec", (nomeEvt, data) -> {
            if (data == null || !data.isArray() || data.size < 8) return;
            aplicarTroca(data.get(0).asString(), data.get(1).asFloat(), data.get(2).asFloat(), direcaoDoInt(data.get(3).asInt()));
            aplicarTroca(data.get(4).asString(), data.get(5).asFloat(), data.get(6).asFloat(), direcaoDoInt(data.get(7).asInt()));
        });

        // Pacote de movimento (10x por segundo, so' de quem esta no raio da
        // tela): [[nome, x, y, dir, flags], ...]. flags: 1 = aparecer/
        // reposicionar direto (entrou no raio, respawn, teleporte), 2 = morto.
        socket.on("mb", (nomeEvt, data) -> {
            if (data == null || !data.isArray()) return;
            for (JsonValue e = data.child; e != null; e = e.next) {
                if (!e.isArray() || e.size < 5) continue;
                aplicarMovimentoRemoto(e.get(0).asString(), e.get(1).asFloat(), e.get(2).asFloat(),
                    direcaoDoInt(e.get(3).asInt()), e.get(4).asInt());
            }
        });
        // Sairam do raio da tela: somem (guardados ate' voltarem).
        socket.on("mh", (nomeEvt, data) -> {
            if (data == null || !data.isArray()) return;
            for (JsonValue e = data.child; e != null; e = e.next) {
                String nome = e.asString();
                Jogador j = remotos.remove(nome);
                if (j != null) remotosForaDeVisao.put(nome, j);
                if (nome.equals(amigoMarcado)) amigoMarcado = null;
            }
        });

        // Virou pro lado sem andar - formato antigo do servidor ([nome, dir,
        // x, y], direto pra area). O servidor novo manda isso dentro do 'mb'.
        socket.on("l", (nomeEvt, data) -> {
            if (data == null || !data.isArray() || data.size < 2) return;
            Jogador j = remotos.get(data.get(0).asString());
            if (j != null && !j.movendo) j.direcao = direcaoDoInt(data.get(1).asInt());
        });

        socket.on("m", (nomeEvt, data) -> {
            if (data == null || !data.isArray() || data.size < 4) return;
            Jogador j = remotos.get(data.get(0).asString());
            if (j != null) {
                // Morto nao anda: se mexeu, renasceu (o teleporte pro spawn
                // chega pra area onde ele morreu, o is_dead=false so' pra nova).
                if (remotosMortos.remove(j.nome)) deixarCadaver(j); // ainda no lugar da morte
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
            // Spawn do mapa (cru): o servidor guarda e usa pra (re)nascer -
            // o client nao escolhe mais onde renasce.
            w.array("spawn");
            w.value(conversor.mundoParaRawX(spawnX));
            w.value(conversor.mundoParaRawY(spawnY));
            w.pop();
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
        if (nome.isEmpty() || nome.equals(local.nome) || remotos.containsKey(nome) || remotosForaDeVisao.containsKey(nome)) return;
        String classe = p.has("class_name") ? p.getString("class_name") : "Knight";
        float rawX = p.getFloat("pos_x", -1f);
        float rawY = p.getFloat("pos_y", -1f);
        float mx = rawX != -1f ? conversor.rawParaMundoX(rawX) : spawnX;
        float my = rawY != -1f ? conversor.rawParaMundoY(rawY) : spawnY;
        // Comeca fora da visao: so' aparece quando o servidor mandar a
        // posicao atual dele no pacote de movimento ('mb').
        remotosForaDeVisao.put(nome, new Jogador(nome, classe, mx, my));
        definirSkins(nome, p.get("skins"));
        if (p.has("current_hp") && p.has("max_hp")) {
            vidaRemotos.put(nome, new float[]{p.getFloat("current_hp", 1f), p.getFloat("max_hp", 1f)});
        }
        // Ja' entrou morto (morreu antes de eu chegar na area).
        if (p.getBoolean("is_dead", false)) remotosMortos.add(nome);
        for (String chave : CHAVES_BALAO) {
            if (p.getBoolean(chave, false)) definirBalaoRemoto(nome, chave, true);
        }
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
        for (int i = lootsFlutuantes.size() - 1; i >= 0; i--) {
            LootFlutuante l = lootsFlutuantes.get(i);
            l.tempo += delta;
            if (l.tempo >= DURACAO_LOOT_FLUTUANTE) lootsFlutuantes.remove(i);
        }
        for (int i = avisosSpawn.size() - 1; i >= 0; i--) {
            AvisoSpawn a = avisosSpawn.get(i);
            a.tempo += delta;
            if (a.tempo >= a.duracao) avisosSpawn.remove(i);
        }
        for (int i = cadaveres.size() - 1; i >= 0; i--) {
            CadaverPlayer c = cadaveres.get(i);
            c.restante -= delta;
            if (c.restante <= 0f) cadaveres.remove(i);
        }
        for (int i = textosAcao.size() - 1; i >= 0; i--) {
            TextoAcao t = textosAcao.get(i);
            t.tempo += delta;
            if (t.tempo >= DURACAO_TEXTO_ACAO) textosAcao.remove(i);
        }
        java.util.Iterator<Fala> itFalas = falas.values().iterator();
        while (itFalas.hasNext()) {
            Fala f = itFalas.next();
            f.tempo += delta;
            if (f.tempo >= f.duracao) itFalas.remove();
        }
        for (int i = textosFlutuantes.size() - 1; i >= 0; i--) {
            TextoFlutuante t = textosFlutuantes.get(i);
            t.tempo += delta;
            if (t.tempo >= DURACAO_TEXTO_FLUTUANTE) textosFlutuantes.remove(i);
        }
        for (int i = numerosDano.size() - 1; i >= 0; i--) {
            NumeroDano n = numerosDano.get(i);
            n.tempo += delta;
            if (n.tempo >= DURACAO_NUMERO_DANO) numerosDano.remove(i);
        }
        dialogoNPC.atualizar();
        atualizarAreaNomeada();

        // Direto por polling (nao via InputProcessor/keyDown) - ver
        // comentario no keyDown(ENTER) do construtor pra entender o motivo.
        // chatDigitandoFrameAnterior: os eventos de input rodam ANTES do
        // render(), entao no frame em que o Enter envia a mensagem o
        // TextFieldListener do ChatUI ja tirou o foco do campo e
        // estaDigitando() volta false aqui - sem essa checagem o mesmo Enter
        // refocava o campo na hora (enviava e continuava digitando, bug
        // reportado pelo usuario).
        // Write (padrao Enter): comeca a digitar no chat (abre ele se estiver fechado).
        if (Gdx.input.isKeyJustPressed(Controles.tecla("write")) && acaoCapturando == null
                && !chat.estaDigitando() && !chatDigitandoFrameAnterior
                && !settingsAberta() && !bookMenu.isVisible()) {
            if (!chat.isVisivel()) alternarChat();
            chat.focarCampoTexto();
        }
        chatDigitandoFrameAnterior = chat.estaDigitando();

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
        // Mesma luz pros outros players (antes so' o jogador local tinha).
        // Lista separada a cada frame: os remotos entram/saem a qualquer hora.
        luzesDoFrame.clear();
        luzesDoFrame.addAll(todasAsLuzes);
        for (Jogador j : remotos.values()) {
            MapaPropriedades.Luz luz = luzesRemotos.get(j.nome);
            if (luz == null) {
                luz = new MapaPropriedades.Luz(j.x, j.y, luzJogador.cor, luzJogador.raio);
                luzesRemotos.put(j.nome, luz);
            }
            luz.x = j.x;
            luz.y = j.y + 11f;
            luzesDoFrame.add(luz);
        }
        luzesRemotos.keySet().retainAll(remotos.keySet());
        for (AvisoSpawn a : avisosSpawn) luzesDoFrame.add(a.luz);
        iluminacao.renderizar(batch, camera, luzesDoFrame);

        // Mundo inteiro desenhado DIRETO na tela real, num passo so' - igual
        // o Godot (Camera2D comum, sem viewport/buffer intermediario nenhum).
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        mapa.desenharMapa(camera);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        // Chao primeiro: cadaveres e bags ficam sempre por baixo de quem passa.
        for (MobVisual mob : mobs.values()) if (mob.morto) desenharMob(mob);
        desenharBags();
        desenharCadaveres();
        desenharAvisosSpawn();
        desenharEntidadesOrdenadas();
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
        desenharFalas();
        for (NPCVisual npc : npcs.values()) desenharNomeNPC(npc);
        for (MobVisual mob : mobs.values()) desenharNomeMob(mob);
        desenharNumerosDano();
        desenharTextosFlutuantes();
        desenharTextosAcao();
        desenharLootsFlutuantes();
        desenharBalaoInteracaoNPC(npcMaisProximoParaConversar());
        TextureRegion notifAtual = notificacaoAtual();
        if (notifAtual != null) {
            float nx = local.x + 2f;
            float ny = local.y + 9.5f;
            batch.draw(notifAtual, nx, ny, notifAtual.getRegionWidth(), notifAtual.getRegionHeight());
        }
        desenharIconeParty(local);
        for (Jogador j : remotos.values()) desenharIconeParty(j);
        for (Jogador j : remotos.values()) {
            TextureRegion balao = convitesTrade.contains(j.nome) && balaoTrade != null ? balaoTrade : balaoRemoto(j.nome);
            if (balao == null || estaMorto(j)) continue;
            float bx = Math.round(j.x / camera.zoom) * camera.zoom + 2f;
            float by = Math.round(j.y / camera.zoom) * camera.zoom + 9.5f;
            batch.draw(balao, bx, by, balao.getRegionWidth(), balao.getRegionHeight());
        }
        enviarEstadoBalao();
        atualizarBotoesPainel();
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
            // "remotos" ja' e' so' quem esta no raio da tela - os mesmos que
            // recebem as mensagens do chat Local (servidor.py::handle_c).
            for (Jogador j : remotos.values()) nomes.add(nomeVisivel(j.nome));
            chat.atualizarJogadores(nomes);
        }
        atualizarVisibilidadeJoystick();
        atualizarVisibilidadeBotoesTopo();
        if (chatComNovidade && chat.isVisivel()) {
            chatComNovidade = false;
            imagemIconeChat.setDrawable(new TextureRegionDrawable(iconeChat));
        }

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
        // Teclas (WASD/setas) e joystick combinados: duas direcoes juntas
        // (ex: W + D, em qualquer ordem) = diagonal. O passo na diagonal leva
        // √2 vezes mais (Jogador.FATOR_DIAGONAL), entao nao anda mais rapido.
        boolean cima = Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W) || joystick.isCima();
        boolean baixo = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S) || joystick.isBaixo();
        boolean esquerda = Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A) || joystick.isEsquerda();
        boolean direita = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D) || joystick.isDireita();
        int v = (cima ? 1 : 0) - (baixo ? 1 : 0);   // cima+baixo juntos se anulam
        int h = (direita ? 1 : 0) - (esquerda ? 1 : 0);
        dx = h * Jogador.TILE;
        dy = v * Jogador.TILE;
        // Moving style "4 directions": duas teclas juntas = so' a vertical (como era).
        if (h != 0 && v != 0 && !Controles.oitoDirecoes()) { h = 0; dx = 0; }
        if (h != 0 && v != 0) direcao = direcaoDiagonal(dx, dy);
        else if (v > 0) direcao = "up";
        else if (v < 0) direcao = "down";
        else if (h < 0) direcao = "left";
        else if (h > 0) direcao = "right";
        if (direcao == null) {
            tempoInsistindo = 0f;
            return;
        }

        float alvoX = local.x + dx;
        float alvoY = local.y + dy;
        boolean diagonal = dx != 0 && dy != 0;
        boolean livre = !colisao.ehParede(alvoX, alvoY)
            && (diagonal ? !diagonalBloqueada(local.x, local.y, dx, dy)
                         : !colisao.movimentoBloqueado(local.x, local.y, alvoX, alvoY))
            && !npcOcupaTile(alvoX, alvoY)
            && !mobOcupaTile(alvoX, alvoY);
        Jogador noCaminho = livre ? jogadorOcupaTile(alvoX, alvoY) : null;
        if (livre && noCaminho == null) {
            tempoInsistindo = 0f;
            // Speed_modifier muda no MEIO do passo: 1a metade na velocidade do
            // SQM de saida, 2a na do de chegada (servidor.py::fator_tempo_passo).
            local.multVelocidade = multFome * mapa.propriedades.velocidadeEm(local.x, local.y);
            local.multVelocidadeDestino = multFome * mapa.propriedades.velocidadeEm(alvoX, alvoY);
            local.iniciarPasso(dx, dy, direcao);
            ultimoPassoDx = Math.signum(dx);
            ultimoPassoDy = Math.signum(dy);
            enviarMove(alvoX, alvoY, direcao);
            return;
        }
        if (!direcao.equals(local.direcao)) enviarVirada(direcao);
        local.direcao = direcao;
        // Na diagonal guarda a direcao (senao o sprite ficaria alternando a
        // cada frame). A troca de lugar (abaixo) vale reto e na diagonal.
        if (diagonal) {
            ultimoPassoDx = Math.signum(dx);
            ultimoPassoDy = Math.signum(dy);
        }
        // Outro player parado no caminho: insistindo (segurando a direcao
        // contra ele) por TEMPO_INSISTIR_TROCA, pede pro servidor trocar de
        // lugar (ele so' aceita se o outro estiver parado/afk ha' um tempo).
        if (noCaminho == null || !noCaminho.nome.equals(alvoInsistindo)) {
            alvoInsistindo = noCaminho != null ? noCaminho.nome : null;
            tempoInsistindo = 0f;
            return;
        }
        tempoInsistindo += Gdx.graphics.getDeltaTime();
        if (tempoInsistindo >= TEMPO_INSISTIR_TROCA && socket.isConnected()) {
            tempoInsistindo = 0f;
            JsonValue lista = new JsonValue(JsonValue.ValueType.array);
            lista.addChild(new JsonValue(noCaminho.nome));
            lista.addChild(new JsonValue(intDaDirecao(direcao)));
            socket.emitRaw("swap_req", lista.toJson(JsonWriter.OutputType.json));
        }
    }

    /** Sprite na diagonal: ao ENTRAR na diagonal vira pro lado novo (o que
     * foi somado). Ex: andando pra direita (right) e indo pra baixo-direita
     * vira down; andando pra baixo (down) e indo pra baixo-direita vira right.
     * Continuando na mesma diagonal, mantem (nao fica trocando a cada passo). */
    private String direcaoDiagonal(float dx, float dy) {
        String horizontal = dx < 0 ? "left" : "right", vertical = dy > 0 ? "up" : "down";
        if (Math.signum(dx) == ultimoPassoDx && Math.signum(dy) == ultimoPassoDy) return local.direcao;
        if (local.direcao.equals(horizontal)) return vertical;
        if (local.direcao.equals(vertical)) return horizontal;
        return horizontal;
    }

    /** Direcao (sinal de x/y) do ultimo passo dado pelo player local. */
    private float ultimoPassoDx = 0f, ultimoPassoDy = 0f;

    /** Diagonal passa se pelo menos um dos dois caminhos em "L" nao cruza
     * cerca fina (parede solida na quina nao bloqueia, igual Tibia). Mesma
     * regra do servidor (servidor.py::diagonal_bloqueada). */
    private boolean diagonalBloqueada(float x, float y, float dx, float dy) {
        boolean caminhoX = colisao.movimentoBloqueado(x, y, x + dx, y)
            || colisao.movimentoBloqueado(x + dx, y, x + dx, y + dy);
        boolean caminhoY = colisao.movimentoBloqueado(x, y, x, y + dy)
            || colisao.movimentoBloqueado(x, y + dy, x + dx, y + dy);
        return caminhoX && caminhoY;
    }

    /** Virou pro lado sem andar (bloqueado): avisa o servidor pra os outros
     * verem a direcao nova (servidor.py::handle_l). */
    private void enviarVirada(String direcao) {
        if (!socket.isConnected()) return;
        JsonValue lista = new JsonValue(JsonValue.ValueType.array);
        lista.addChild(new JsonValue(local.nome));
        lista.addChild(new JsonValue(intDaDirecao(direcao)));
        lista.addChild(new JsonValue(conversor.mundoParaRawX(local.x)));
        lista.addChild(new JsonValue(conversor.mundoParaRawY(local.y)));
        socket.emitRaw("l", lista.toJson(JsonWriter.OutputType.json));
    }

    // Troca de lugar com player parado no caminho (ver processarEntrada).
    private static final float TEMPO_INSISTIR_TROCA = 2f;
    private float tempoInsistindo = 0f;
    private String alvoInsistindo = null;

    /** Outro player vivo no SQM (morto nao bloqueia: o corpo e' atravessavel). */
    private Jogador jogadorOcupaTile(float mundoX, float mundoY) {
        int tileX = (int) Math.floor(mundoX / Jogador.TILE);
        int tileY = (int) Math.floor((mundoY - 1f) / Jogador.TILE);
        for (Jogador j : remotos.values()) {
            if (remotosMortos.contains(j.nome)) continue;
            float jx = j.ultimoAlvoX(), jy = j.ultimoAlvoY(); // pra onde ele esta indo
            if ((int) Math.floor(jx / Jogador.TILE) == tileX && (int) Math.floor((jy - 1f) / Jogador.TILE) == tileY) return j;
        }
        return null;
    }

    private void aplicarMovimentoRemoto(String nome, float rawX, float rawY, String direcao, int flags) {
        Jogador j = remotos.get(nome);
        if (j == null) {
            j = remotosForaDeVisao.remove(nome);
            if (j == null) return; // ainda nao chegou o player_joined dele
            remotos.put(nome, j);
            flags |= 1; // estava fora da visao: posicao velha, vai direto
        }
        float mx = conversor.rawParaMundoX(rawX);
        float my = conversor.rawParaMundoY(rawY);
        boolean morto = (flags & 2) != 0;
        if ((flags & 1) != 0) {
            // Aparecer/reposicionar: sem animacao de passo nem cadaver.
            j.posicionar(mx, my, direcao);
            if (morto) remotosMortos.add(nome); else remotosMortos.remove(nome);
            return;
        }
        float alvoAtualX = j.ultimoAlvoX(), alvoAtualY = j.ultimoAlvoY();
        // Mesma posicao: so' virou pro lado (vai na fila, vira na hora certa).
        if (mx == alvoAtualX && my == alvoAtualY) {
            j.definirAlvo(mx, my, direcao);
            return;
        }
        // Passo normal. Morto nao anda: se mexeu, renasceu (cadaver fica).
        if (remotosMortos.remove(nome)) deixarCadaver(j);
        if (Math.abs(mx - alvoAtualX) + Math.abs(my - alvoAtualY) > Jogador.TILE * 2.5f) {
            j.posicionar(mx, my, direcao); // pulou passos (pacote perdido): vai direto
        } else {
            j.definirAlvo(mx, my, direcao);
        }
    }

    /** Aplica a posicao que o servidor mandou (troca de lugar): anda 1 SQM se
     * for do lado, senao teleporta. */
    private void aplicarTroca(String nome, float rawX, float rawY, String direcao) {
        float mx = conversor.rawParaMundoX(rawX);
        float my = conversor.rawParaMundoY(rawY);
        if (nome.equals(local.nome)) {
            float dx = mx - local.x, dy = my - local.y;
            // Do lado ou na diagonal: anda o passo (a diagonal ja' dura mais).
            if (!local.movendo && Math.max(Math.abs(dx), Math.abs(dy)) <= Jogador.TILE + 0.5f) {
                local.iniciarPasso(dx, dy, direcao);
            } else {
                local.x = mx;
                local.y = my;
                local.movendo = false;
                local.direcao = direcao;
            }
            return;
        }
        Jogador j = remotos.get(nome);
        if (j != null) j.definirAlvo(mx, my, direcao);
    }

    /** Mob vivo no SQM (onde esta ou pra onde esta indo) bloqueia o passo -
     * o servidor tambem recusa (servidor.py::handle_m). */
    private boolean mobOcupaTile(float mundoX, float mundoY) {
        int tileX = (int) Math.floor(mundoX / Jogador.TILE);
        int tileY = (int) Math.floor((mundoY - 1f) / Jogador.TILE);
        for (MobVisual mob : mobs.values()) {
            if (mob.morto || !mob.visivel) continue;
            if ((int) Math.floor(mob.destinoFinalX() / Jogador.TILE) == tileX
                && (int) Math.floor((mob.destinoFinalY() - 1f) / Jogador.TILE) == tileY) return true;
            if ((int) Math.floor(mob.x / Jogador.TILE) == tileX
                && (int) Math.floor((mob.y - 1f) / Jogador.TILE) == tileY) return true;
        }
        return false;
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
        if (tex.getRegionWidth() >= (FRAME_MORTE + 1) * FRAME_LARGURA) a.morte = regiao(tex, FRAME_MORTE);
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
        // Morto (eu ou outro player): quadro de morte da spritesheet. Camada de
        // skin sem esse quadro devolve null e nao e' desenhada (ver desenharJogador).
        if (estaMorto(j)) {
            if (a.morte != null) return a.morte;
            return a == animacaoBase ? a.idleBaixo : null;
        }
        return quadroAtual(a, j.movendo, j.direcao, j.progresso());
    }

    private boolean estaMorto(Jogador j) {
        // remotos.get(...) == j: NPC tambem e' um Jogador (npc.movimento) e nao
        // pode pegar o estado de um player que por acaso tenha o mesmo nome.
        if (j == local) return localMorto;
        return remotos.get(j.nome) == j && remotosMortos.contains(j.nome);
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
        // FriendTarget (18x18) em volta do SQM do player marcado - mesmo lugar do Target dos mobs.
        if (j != local && j.nome.equals(amigoMarcado) && regiaoAlvoAmigo != null) {
            batch.draw(regiaoAlvoAmigo, ancoraX - regiaoAlvoAmigo.getRegionWidth() / 2f, ancoraY - 1f);
        }
        List<CamadaSkin> camadas = skinsJogadores.get(j.nome);
        if (camadas == null || camadas.isEmpty()) {
            batch.draw(quadroBase, x, ancoraY, largura, altura);
            return;
        }
        // Camadas de skin (base, roupa, cabeca, acessorio) uma por cima da
        // outra, cada uma com a cor escolhida na aba Vanity.
        for (CamadaSkin camada : camadas) {
            TextureRegion quadro = quadroAtual(camada.animacao, j);
            if (quadro == null) continue; // camada sem quadro de morte
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
        if (nome.equals(local.nome)) atualizarRetrato(skins);
    }

    private void desenharNPC(NPCVisual npc) {
        TextureRegion quadro = quadroAtual(npc.animacao, npc.movimento);
        float largura = FRAME_LARGURA * ESCALA_SPRITE;
        float altura = quadro.getRegionHeight() * ESCALA_SPRITE;
        float ancoraX = Math.round(npc.movimento.x / camera.zoom) * camera.zoom;
        float ancoraY = Math.round(npc.movimento.y / camera.zoom) * camera.zoom;
        batch.draw(quadro, ancoraX - largura / 2f, ancoraY, largura, altura);
    }

    // Players, NPCs e mobs vivos ordenados pela profundidade (Y): quem esta
    // mais ao norte e' desenhado antes, quem esta mais ao sul fica na frente.
    private final List<Object> ordemDesenho = new ArrayList<>();

    private float yDe(Object o) {
        if (o instanceof Jogador) return ((Jogador) o).y;
        if (o instanceof NPCVisual) return ((NPCVisual) o).movimento.y;
        return ((MobVisual) o).y;
    }

    private void desenharEntidadesOrdenadas() {
        ordemDesenho.clear();
        ordemDesenho.add(local);
        ordemDesenho.addAll(remotos.values());
        ordemDesenho.addAll(npcs.values());
        for (MobVisual mob : mobs.values()) if (!mob.morto) ordemDesenho.add(mob);
        ordemDesenho.sort((a, b) -> Float.compare(yDe(b), yDe(a)));
        for (Object o : ordemDesenho) {
            if (o instanceof Jogador) desenharJogador((Jogador) o);
            else if (o instanceof NPCVisual) desenharNPC((NPCVisual) o);
            else desenharMob((MobVisual) o);
        }
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
            Gdx.app.log("WorldScreen", "Mob criado: " + id + " (spawn_range=" + spawn.spawnRange
                + ", respawn_time=" + spawn.respawnTime + ")");
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
        if (posicaoAbsoluta && mob.visivel && !mob.morto
                && mx == mob.destinoFinalX() && my == mob.destinoFinalY()) {
            // Sync de area com o mob ja indo pra esse SQM: deixa o passo terminar
            // (mob.gd::posicionar faz o mesmo), sem "tp" no meio do caminho.
            if (!mob.movendo) mob.direcao = direcao;
        } else if (posicaoAbsoluta || !mob.visivel) {
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
        if (mob.morto) {
            // Cadaver some aos poucos nos ultimos FADE_CORPO segundos (inclusive
            // quando o SpawnWarning encurta o tempo dele, ver mob_spawn_warning).
            batch.setColor(1f, 1f, 1f, Math.min(1f, mob.tempoCorpo / FADE_CORPO));
            batch.draw(quadro, x, ancoraY, largura, altura);
            batch.setColor(Color.WHITE);
        } else {
            batch.draw(quadro, x, ancoraY, largura, altura);
        }
        if (!mob.morto && mob.tempoTargetHit > 0f && regiaoTargetHit != null) {
            batch.draw(regiaoTargetHit, ancoraX - regiaoTargetHit.getRegionWidth() / 2f, ancoraY);
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

    private Efeito tocarEfeito(String nome, float x, float y) {
        TextureRegion[] quadros = quadrosEfeito(nome);
        if (quadros == null) return null;
        Efeito e = new Efeito(quadros, x, y);
        boolean rapido = !"Sword".equals(nome) && !"Mana".equals(nome) && !"Music1".equals(nome); // Arrow ou Physical
        e.duracaoQuadro = "Arrow".equals(nome) ? DURACAO_QUADRO_HIT_FLECHA
            : rapido ? DURACAO_QUADRO_HIT_RAPIDO : DURACAO_QUADRO_HIT;
        efeitos.add(e);
        return e;
    }

    /** Hit que acompanha o sprite do mob enquanto ele anda. */
    private void tocarEfeitoNoMob(String nome, MobVisual mob) {
        Efeito e = tocarEfeito(nome, mob.x, mob.y);
        if (e != null) e.mobAlvo = mob;
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

    // Municao equipada (Ranger): slot "Hand" (servidor.py::SLOT_MUNICAO).
    private static final String SLOT_MUNICAO = "Hand";
    private String caminhoMunicao = null;
    private int quantidadeMunicao = 0;
    private boolean avisouSemMunicao = false;
    private boolean avisouSemMana = false;

    /** Aviso (uma vez ate' a mana voltar) de que a varinha nao tem mana pra atacar. */
    private void avisarSemMana() {
        if (avisouSemMana) return;
        avisouSemMana = true;
        chat.adicionarMensagemSistema("You don't have enough mana.", new Color(1f, 0.25f, 0.25f, 1f));
        hud.notificar("You don't have enough mana.", COR_NOTIF_AVISO, null);
    }

    /** Le a flecha equipada (item + qty) e mostra/esconde a barrinha da HUD. */
    private void atualizarMunicao(JsonValue equipados) {
        caminhoMunicao = null;
        quantidadeMunicao = 0;
        if ("Ranger".equals(local.classe) && equipados != null && equipados.has(SLOT_MUNICAO)) {
            JsonValue inst = equipados.get(SLOT_MUNICAO);
            String caminho = inst.getString("item", "");
            if (!caminho.isEmpty()) {
                caminhoMunicao = caminho;
                quantidadeMunicao = Math.max(0, inst.getInt("qty", 1));
                avisouSemMunicao = false;
            }
        }
        hud.definirMunicao(caminhoMunicao != null ? bookMenu.iconeDoItem(caminhoMunicao) : null, quantidadeMunicao);
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
        // 1o o mob que esta no SQM clicado; so' depois o sprite (mais alto que
        // o SQM) de algum mob - senao o sprite de um mob de baixo "rouba" o
        // clique no SQM de cima.
        int cx = tileX(wx), cy = (int) Math.floor(wy / Jogador.TILE);
        for (MobVisual mob : mobs.values()) {
            if (mob.morto || !mob.visivel) continue;
            if (tileX(mob.x) == cx && tileY(mob.y) == cy) {
                mirarMob(mob);
                return true;
            }
        }
        for (MobVisual mob : mobs.values()) {
            if (mob.morto || !mob.visivel) continue;
            float largura = FRAME_LARGURA * ESCALA_SPRITE;
            float altura = mob.animacao.idleBaixo.getRegionHeight() * ESCALA_SPRITE;
            if (wx >= mob.x - largura / 2f && wx <= mob.x + largura / 2f && wy >= mob.y && wy <= mob.y + altura) {
                mirarMob(mob);
                return true;
            }
        }
        return false;
    }

    private void mirarMob(MobVisual mob) {
        alvoMob = mob.id.equals(alvoMob) ? null : mob.id;
        amigoMarcado = null; // um alvo por vez
        // NAO zera a espera: o cooldown e' do player, nao do alvo. Antes,
        // trocar de alvo logo depois de matar um mob mandava o golpe antes do
        // cooldown do servidor acabar - ele recusava, e o client ainda
        // esperava o intervalo inteiro de novo (parecia cooldown dobrado).
    }

    /** Clique em outro player: nao ataca, so' marca o SQM dele com o
     * FriendTarget (clicar de novo tira). */
    private boolean cliqueEmJogador(float wx, float wy) {
        int cx = tileX(wx), cy = (int) Math.floor(wy / Jogador.TILE);
        for (Jogador j : remotos.values()) {
            float altura = animacaoBase.idleBaixo.getRegionHeight() * ESCALA_SPRITE;
            boolean noSqm = tileX(j.x) == cx && tileY(j.y) == cy;
            boolean noSprite = wx >= j.x - FRAME_LARGURA / 2f && wx <= j.x + FRAME_LARGURA / 2f && wy >= j.y && wy <= j.y + altura;
            if (noSqm || noSprite) {
                amigoMarcado = j.nome.equals(amigoMarcado) ? null : j.nome;
                if (amigoMarcado != null) alvoMob = null;
                return true;
            }
        }
        return false;
    }

    /** Clique numa bag a ate ALCANCE_BAG_SQM SQMs: pega tudo direto (sem
     * janela) e o que veio aparece subindo em cima do player. */
    private boolean cliqueEmBag(float wx, float wy) {
        for (BagChao bag : bags.values()) {
            if (Math.abs(wx - bag.x) > 8f || wy < bag.y || wy > bag.y + 16f) continue;
            if (distanciaSqm(local.x, local.y, bag.x, bag.y) > ALCANCE_BAG_SQM) return true; // longe: so' consome o clique
            String id = bag.id;
            socket.emitRaw("collect_loot", GameSocket.obj(jw -> jw.set("loot_id", id)));
            return true;
        }
        return false;
    }

    private void removerBag(String lootId) {
        bags.remove(lootId);
    }

    /** Monta o "+ loot" em cima do player com a resposta do collect_loot. */
    private void mostrarLootPego(JsonValue data) {
        LootFlutuante loot = new LootFlutuante(local.x, local.y);
        // Itens iguais viram uma linha so' com a quantidade.
        Map<String, Integer> contagem = new LinkedHashMap<>();
        JsonValue itens = data.get("items");
        if (itens != null) {
            for (JsonValue item = itens.child; item != null; item = item.next) {
                String caminho = item.getString("item", "");
                contagem.merge(caminho, Math.max(1, item.getInt("qty", 1)), Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : contagem.entrySet()) {
            loot.adicionar(bookMenu.iconeDoItem(e.getKey()), e.getValue() > 1 ? String.valueOf(e.getValue()) : "", Color.WHITE);
        }
        long moedas = data.getLong("currency_gained", 0L);
        long[] valores = {moedas / 1_000_000L, (moedas % 1_000_000L) / 10_000L, (moedas % 10_000L) / 100L, moedas % 100L};
        String[] tipos = {"Platinum", "Gold", "Silver", "Copper"};
        for (int i = 0; i < tipos.length; i++) {
            if (valores[i] > 0) loot.adicionar(atlas.findRegion("ui/currency/" + tipos[i]), String.valueOf(valores[i]), corMoeda(tipos[i]));
        }
        if (data.getBoolean("cap_bloqueado", false)) loot.semCapacidade = true;
        if (!loot.textos.isEmpty() || loot.semCapacidade) lootsFlutuantes.add(loot);
    }

    /** Cor da quantidade por tipo de moeda. */
    private static Color corMoeda(String tipo) {
        switch (tipo) {
            case "Copper": return Color.valueOf("ff8a3d");   // laranja
            case "Silver": return Color.valueOf("a9bcd6");   // cinza azulado
            case "Gold": return Color.valueOf("ffd34e");     // amarelo
            case "Platinum": return Color.valueOf("e4e4e4"); // cinza claro
            default: return Color.WHITE;
        }
    }

    /** Icones lado a lado em cima do player, cada um com a quantidade no
     * canto inferior direito (na cor da moeda; branca pra item). */
    private void desenharLootsFlutuantes() {
        Color anterior = new Color(font.getColor());
        final float tamanho = 12f, espaco = 2f;
        for (LootFlutuante l : lootsFlutuantes) {
            float t = l.tempo / DURACAO_LOOT_FLUTUANTE;
            float subida = 14f * (1f - (1f - t) * (1f - t));
            float alfa = Math.max(0f, t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f);
            float ancoraX = Math.round(l.x / camera.zoom) * camera.zoom;
            float y = Math.round((l.y + 12f + subida) / camera.zoom) * camera.zoom;
            int n = l.icones.size();
            float larguraTotal = n * tamanho + Math.max(0, n - 1) * espaco;
            float x = Math.round((ancoraX - larguraTotal / 2f) / camera.zoom) * camera.zoom;
            font.getData().setScale(NOME_ESCALA_BASE * 0.8f);
            for (int i = 0; i < n; i++) {
                float xi = x + i * (tamanho + espaco);
                TextureRegion icone = l.icones.get(i);
                if (icone != null) {
                    batch.setColor(1f, 1f, 1f, alfa);
                    batch.draw(icone, xi, y, tamanho, tamanho);
                    batch.setColor(Color.WHITE);
                }
                String qtd = l.textos.get(i);
                if (!qtd.isEmpty()) {
                    layout.setText(font, qtd);
                    Color c = l.cores.get(i);
                    font.setColor(c.r, c.g, c.b, alfa);
                    // Canto inferior direito do icone.
                    font.draw(batch, qtd, xi + tamanho - layout.width + 1f, y + layout.height - 1f);
                }
            }
            font.getData().setScale(NOME_ESCALA_BASE);
            if (l.semCapacidade) {
                layout.setText(font, "Not enough capacity");
                font.setColor(1f, 0.3f, 0.3f, alfa);
                font.draw(batch, "Not enough capacity",
                    Math.round((ancoraX - layout.width / 2f) / camera.zoom) * camera.zoom, y + tamanho + 8f);
            }
        }
        font.setColor(anterior);
    }

    private void atualizarCombate(float delta) {
        for (int i = efeitos.size() - 1; i >= 0; i--) {
            Efeito e = efeitos.get(i);
            e.tempo += delta;
            if (e.tempo >= e.quadros.length * e.duracaoQuadro) efeitos.remove(i);
        }
        for (int i = projeteis.size() - 1; i >= 0; i--) {
            Projetil pr = projeteis.get(i);
            pr.tempo += delta;
            if (pr.tempo >= pr.duracao) {
                projeteis.remove(i);
                tocarEfeitoNoMob(pr.efeitoHit, pr.alvo);
                if (pr.numero != null) {
                    // Numero no lugar onde o mob esta agora (ele pode ter andado).
                    NumeroDano n = new NumeroDano(pr.alvo.x, pr.alvo.y, pr.numero.texto, pr.numero.critico);
                    n.cor = pr.numero.cor;
                    numerosDano.add(n);
                }
            }
        }
        java.util.Iterator<BagChao> it = bags.values().iterator();
        while (it.hasNext()) {
            BagChao bag = it.next();
            bag.restante -= delta;
            if (bag.restante <= 0f) {
                it.remove();
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
        // Se afastou demais: desfoca. Longe do jeito que o mob perde o player
        // (servidor.py: DETECCAO_SQM + PERSISTE_SQM), ou o mob ja' desistiu
        // (voltando pra casa) e ficou fora do alcance do meu ataque.
        int distAlvo = distanciaSqm(local.x, local.y, alvo.x, alvo.y);
        int alcanceAtaque = classeRanged() ? ALCANCE_RANGED_SQM : 1;
        if (distAlvo > DISTANCIA_PERDE_ALVO_SQM || (alvo.voltando && distAlvo > alcanceAtaque)) {
            alvoMob = null;
            return;
        }
        if (localMorto || esperaAtaque > 0f || !socket.isConnected()) return;
        // Arco sem flecha nao ataca (o servidor tambem recusa).
        if ("Ranger".equals(local.classe) && (caminhoMunicao == null || quantidadeMunicao <= 0)) {
            if (!avisouSemMunicao) {
                avisouSemMunicao = true;
                chat.adicionarMensagemSistema("You have no arrows equipped.", new Color(1f, 0.25f, 0.25f, 1f));
                hud.notificar("You have no arrows equipped.", COR_NOTIF_AVISO, null);
            }
            return;
        }
        // Arma que gasta mana (varinha) sem mana suficiente nao ataca.
        int custoMana = bookMenu.custoManaArmaEquipada();
        if (custoMana > 0 && hud.mpAtual() < custoMana) {
            avisarSemMana();
            return;
        }
        avisouSemMana = false;
        if (classeRanged()) {
            float dx = local.x - alvo.x, dy = local.y - alvo.y;
            if (dx * dx + dy * dy > (float) (ALCANCE_RANGED_SQM * Jogador.TILE) * (ALCANCE_RANGED_SQM * Jogador.TILE)) return;
        } else if (distanciaSqm(local.x, local.y, alvo.x, alvo.y) > 1) return;
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
        // Progresso da skill principal da classe (o servidor conta e avisa o level up).
        String skill = skillDaClasse();
        socket.emitRaw("register_skill_hit", GameSocket.obj(jw -> jw.set("skill", skill)));
    }

    /** Chave da skill principal (igual servidor.py / BookMenuUI.skillPrincipal). */
    private String skillDaClasse() {
        switch (local.classe) {
            case "Ranger": return "distance";
            case "Mage": return "magic";
            case "Bard": return "musicality";
            default: return "melee";
        }
    }

    private static String nomeSkill(String chave) {
        switch (chave) {
            case "distance": return "Focus"; // chave do servidor continua "distance"
            case "magic": return "Magic";
            case "musicality": return "Musicality";
            case "melee": return "Melee";
            case "defense": return "Defense";
            default: return chave.isEmpty() ? "Skill" : Character.toUpperCase(chave.charAt(0)) + chave.substring(1);
        }
    }

    /** Mesmas cores das skills no BookMenu. */
    private static Color corSkill(String chave) {
        switch (chave) {
            case "magic": return Color.valueOf("a474d4");
            case "distance": return Color.valueOf("00d084");
            case "musicality": return Color.valueOf("ffa24e");
            case "defense": return Color.valueOf("6ab7ff");
            default: return Color.valueOf("cccccc");
        }
    }

    /** Guarda o quadro de morte (corpo + camadas de skin com as cores) de
     * quem esta morto agora, onde ele esta. Chamar ANTES de tirar o estado
     * de morto. */
    private void deixarCadaver(Jogador j) {
        CadaverPlayer c = new CadaverPlayer(j.x, j.y);
        List<CamadaSkin> camadas = skinsJogadores.get(j.nome);
        if (camadas == null || camadas.isEmpty()) {
            c.quadros.add(animacaoBase.morte != null ? animacaoBase.morte : animacaoBase.idleBaixo);
            c.cores.add(Color.WHITE);
        } else {
            for (CamadaSkin camada : camadas) {
                if (camada.animacao.morte == null) continue;
                c.quadros.add(camada.animacao.morte);
                c.cores.add(camada.cor);
            }
        }
        if (!c.quadros.isEmpty()) cadaveres.add(c);
    }

    /** 3 ciclos do SpawnWarning espalhados no tempo ate o mob nascer. */
    private void desenharAvisosSpawn() {
        for (AvisoSpawn a : avisosSpawn) {
            int n = quadrosAvisoSpawn.length;
            float porQuadro = a.duracao / (REPETICOES_AVISO_SPAWN * n);
            TextureRegion q = quadrosAvisoSpawn[((int) (a.tempo / porQuadro)) % n];
            float ancoraX = Math.round(a.x / camera.zoom) * camera.zoom;
            float ancoraY = Math.round(a.y / camera.zoom) * camera.zoom;
            batch.draw(q, ancoraX - q.getRegionWidth() / 2f, ancoraY);
        }
    }

    private void desenharCadaveres() {
        for (CadaverPlayer c : cadaveres) {
            float ancoraX = Math.round(c.x / camera.zoom) * camera.zoom;
            float ancoraY = Math.round(c.y / camera.zoom) * camera.zoom;
            float largura = FRAME_LARGURA * ESCALA_SPRITE;
            for (int i = 0; i < c.quadros.size(); i++) {
                TextureRegion q = c.quadros.get(i);
                batch.setColor(c.cores.get(i));
                batch.draw(q, ancoraX - largura / 2f, ancoraY, largura, q.getRegionHeight() * ESCALA_SPRITE);
            }
        }
        batch.setColor(Color.WHITE);
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
            float x = pr.x0 + (pr.x1() - pr.x0) * t;
            float y = pr.y0 + (pr.y1() - pr.y0) * t;
            float angulo = (float) Math.toDegrees(Math.atan2(pr.y1() - pr.y0, pr.x1() - pr.x0));
            float w = pr.regiao.getRegionWidth(), h = pr.regiao.getRegionHeight();
            batch.draw(pr.regiao, x - w / 2f, y - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, angulo);
        }
        for (Efeito e : efeitos) {
            int quadro = Math.min(e.quadros.length - 1, (int) (e.tempo / e.duracaoQuadro));
            TextureRegion r = e.quadros[quadro];
            float ancoraX = Math.round(e.posX() / camera.zoom) * camera.zoom;
            float ancoraY = Math.round(e.posY() / camera.zoom) * camera.zoom;
            batch.draw(r, ancoraX - r.getRegionWidth() / 2f, ancoraY);
        }
    }

    // ---- Morte / renascer do player local ----

    private Table conteudoMorte;
    private Image fundoMorte;
    private Label textoMortoPor;

    /** Tela de morte: escurece a tela aos poucos (vinheta preta), e no meio
     * aparece a caveira, "YOU DIED" grande em vermelho, quem matou e o botao
     * de renascer - tudo entrando com fade. */
    private void criarPainelMorte() {
        painelMorte = new Table();
        painelMorte.setFillParent(true);

        fundoMorte = new Image(UiSkin.retangulo(new Color(0.04f, 0f, 0f, 1f), new Color(0.04f, 0f, 0f, 1f), 0));
        fundoMorte.setFillParent(true);

        conteudoMorte = new Table();
        TextureRegion caveira = atlas.findRegion("sheet/r83_c11");
        if (caveira != null) {
            Image icone = new Image(caveira);
            icone.setScaling(com.badlogic.gdx.utils.Scaling.fit);
            icone.setColor(new Color(0.85f, 0.15f, 0.15f, 1f));
            conteudoMorte.add(icone).size(mobile ? 72 : 56).padBottom(6).row();
        }
        Label titulo = new Label("YOU DIED", skin, "titulo");
        titulo.setFontScale(mobile ? 0.75f : 0.6f);
        titulo.setColor(new Color(0.82f, 0.08f, 0.08f, 1f));
        conteudoMorte.add(titulo).row();

        // Linha fina vermelha separando o titulo do resto.
        Image linha = new Image(UiSkin.retangulo(new Color(0.55f, 0.06f, 0.06f, 1f), new Color(0.55f, 0.06f, 0.06f, 1f), 0));
        conteudoMorte.add(linha).width(mobile ? 340 : 280).height(2).padTop(4).padBottom(10).row();

        textoMortoPor = new Label("", skin, "subtitulo");
        textoMortoPor.setColor(new Color(0.78f, 0.72f, 0.72f, 1f));
        textoMortoPor.setFontScale(mobile ? 0.8f : 0.65f);
        conteudoMorte.add(textoMortoPor).padBottom(22).row();

        TextButton renascer = new TextButton("Respawn", skin, "vermelho-popup");
        renascer.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                renascerLocal();
            }
        });
        conteudoMorte.add(renascer).width(LARGURA_BOTAO_SETTINGS).height(ALTURA_BOTAO_SETTINGS);

        Table centro = new Table();
        centro.setFillParent(true);
        centro.add(conteudoMorte);
        painelMorte.addActor(fundoMorte);
        painelMorte.addActor(centro);
        painelMorte.setVisible(false);
        uiStage.addActor(painelMorte);
    }

    private void morrerLocal() {
        if (localMorto) return;
        localMorto = true;
        alvoMob = null;
        if (textoMortoPor.getText().length() == 0) textoMortoPor.setText("You have been slain.");
        // Fundo escurece em ~1s; o texto/botao aparecem logo depois (fade).
        fundoMorte.clearActions();
        fundoMorte.getColor().a = 0f;
        fundoMorte.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(0.72f, 1.0f));
        conteudoMorte.clearActions();
        conteudoMorte.getColor().a = 0f;
        conteudoMorte.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
            com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(0.5f),
            com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.7f, com.badlogic.gdx.math.Interpolation.pow2Out)));
        painelMorte.setVisible(true);
    }

    /** Volta pro ponto de spawn com HP/MP cheios (o servidor devolve os vitais
     * quando recebe is_dead=false; ver servidor.py::handle_update_status). */
    private void renascerLocal() {
        if (!localMorto) return;
        deixarCadaver(local); // antes de sair do estado morto (usa o quadro de morte)
        localMorto = false;
        painelMorte.setVisible(false);
        textoMortoPor.setText("");
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
        // Bandeira enquanto volta pra casa (desistiu do alvo). Desenhada aqui
        // (depois do nome e da barra de vida) pra ficar por cima dos dois.
        if (mob.voltando && regiaoFlag != null) {
            batch.draw(regiaoFlag, ancoraX + 2f, ancoraY + altura - 10f, 12f, 12f);
        }
    }

    /** Cor do nome/barra pela vida: verde cheio, verde claro, amarelo,
     * vermelho e vermelho escuro conforme o HP cai. */
    private static final float[] VIDA_PONTOS = {1f, 0.75f, 0.5f, 0.25f, 0f};
    private static final Color[] VIDA_CORES = {
        new Color(0f, 0.85f, 0f, 1f),      // verde (cheio)
        new Color(0.95f, 0.9f, 0.1f, 1f),  // amarelo
        new Color(1f, 0.55f, 0f, 1f),      // laranja
        new Color(1f, 0f, 0f, 1f),         // vermelho puro
        new Color(0.75f, 0f, 0f, 1f),      // vermelho escuro (quase morto)
    };

    /** Cor do nome/barra pela vida, com transicao suave entre as faixas. */
    private static Color corDaVida(float pct) {
        pct = Math.max(0f, Math.min(1f, pct));
        for (int i = 0; i < VIDA_PONTOS.length - 1; i++) {
            if (pct >= VIDA_PONTOS[i + 1]) {
                float t = (VIDA_PONTOS[i] - pct) / (VIDA_PONTOS[i] - VIDA_PONTOS[i + 1]);
                return new Color(VIDA_CORES[i]).lerp(VIDA_CORES[i + 1], t);
            }
        }
        return new Color(VIDA_CORES[VIDA_CORES.length - 1]);
    }

    /** "Level 8!" / "Magic 19!" subindo devagar em cima da cabeca e sumindo no fim. */
    private void desenharTextosAcao() {
        if (textosAcao.isEmpty()) return;
        Color anterior = new Color(font.getColor());
        font.getData().setScale(NOME_ESCALA_BASE * ESCALA_TEXTO_ACAO);
        Map<Jogador, Integer> pilha = new HashMap<>();
        for (TextoAcao t : textosAcao) {
            int ordem = pilha.merge(t.alvo, 1, Integer::sum) - 1;
            float alfa = Math.min(1f, (DURACAO_TEXTO_ACAO - t.tempo) / SUMINDO_TEXTO_ACAO);
            float altura = quadroAtual(animacaoBase, t.alvo).getRegionHeight() * ESCALA_SPRITE;
            float ancoraX = Math.round(t.alvo.x / camera.zoom) * camera.zoom;
            float ancoraY = Math.round(t.alvo.y / camera.zoom) * camera.zoom;
            layout.setText(font, t.texto);
            float x = Math.round((ancoraX - layout.width / 2f) / camera.zoom) * camera.zoom;
            // Logo acima do nome (que fica em ancoraY + altura + 7).
            float y = Math.round((ancoraY + altura + 15f + ordem * 7f) / camera.zoom) * camera.zoom;
            font.setColor(t.cor.r, t.cor.g, t.cor.b, Math.max(0f, alfa));
            font.draw(batch, t.texto, x, y);
        }
        font.getData().setScale(NOME_ESCALA_BASE);
        font.setColor(anterior);
    }

    private void desenharTextosFlutuantes() {
        Color anterior = new Color(font.getColor());
        // Varios ao mesmo tempo no mesmo player (level + skill) empilham.
        Map<Jogador, Integer> pilha = new HashMap<>();
        for (TextoFlutuante t : textosFlutuantes) {
            int ordem = pilha.merge(t.alvo, 1, Integer::sum) - 1;
            float ps = Math.min(1f, t.tempo / TEXTO_SUBIDA);
            float subida = 16f * (1f - (1f - ps) * (1f - ps)); // ease-out e para no topo
            float fim = TEXTO_SUBIDA + TEXTO_PARADO;
            float alfa = t.tempo < fim ? 1f : 1f - (t.tempo - fim) / TEXTO_SUMINDO;
            float ancoraX = Math.round(t.x / camera.zoom) * camera.zoom;
            float ancoraY = Math.round(t.y / camera.zoom) * camera.zoom;
            layout.setText(fonteDestaque, t.texto);
            float x = Math.round((ancoraX - layout.width / 2f) / camera.zoom) * camera.zoom;
            float y = Math.round((ancoraY + 30f + subida + ordem * 12f) / camera.zoom) * camera.zoom;
            fonteDestaque.setColor(t.cor.r, t.cor.g, t.cor.b, Math.max(0f, alfa));
            fonteDestaque.draw(batch, t.texto, x, y);
        }
        font.setColor(anterior);
    }

    /** Fala do chat Local em cima do nome do jogador, quebrando linha em
     * LARGURA_FALA e crescendo pra cima. Markup so' ligado aqui (o "[" que o
     * jogador digitou vira "[[", escapado). */
    private void desenharFalas() {
        if (falas.isEmpty()) return;
        Color anterior = new Color(font.getColor());
        boolean markupAnterior = font.getData().markupEnabled;
        font.getData().markupEnabled = true;
        for (Fala f : falas.values()) {
            Jogador j = jogadorPorNome(f.nome);
            if (j == null) continue;
            float altura = quadroAtual(animacaoBase, j).getRegionHeight() * ESCALA_SPRITE;
            float ancoraX = Math.round(j.x / camera.zoom) * camera.zoom;
            float ancoraY = Math.round(j.y / camera.zoom) * camera.zoom;
            String texto = "[#" + f.corNome.toString() + "]" + nomeVisivel(f.nome).replace("[", "[[") + ":[] "
                + (f.corTexto != null ? "[#" + f.corTexto.toString() + "]" : "") + f.texto.replace("[", "[[")
                + (f.corTexto != null ? "[]" : "");
            layout.setText(font, texto, Color.WHITE, LARGURA_FALA, Align.center, true);
            float x = Math.round((ancoraX - LARGURA_FALA / 2f) / camera.zoom) * camera.zoom;
            // Logo acima do nome (que fica em ancoraY + altura + 7).
            float y = Math.round((ancoraY + altura + 14f + layout.height) / camera.zoom) * camera.zoom;
            font.draw(batch, layout, x, y);
        }
        font.getData().markupEnabled = markupAnterior;
        font.setColor(anterior);
    }

    /** Dano subindo ~10px e sumindo no fim (mob.gd::exibir_numero_dano). */
    private void desenharNumerosDano() {
        Color anterior = new Color(font.getColor());
        for (NumeroDano n : numerosDano) {
            float t = n.tempo / DURACAO_NUMERO_DANO;
            float subida = 10f * (1f - (1f - t) * (1f - t)); // ease-out, sobe pouco
            float alfa = t < 0.55f ? 1f : 1f - (t - 0.55f) / 0.45f;
            layout.setText(font, n.texto);
            float x = Math.round((n.x - layout.width / 2f) / camera.zoom) * camera.zoom;
            float y = Math.round((n.y + 20f + subida) / camera.zoom) * camera.zoom;
            Color cor = n.cor != null ? new Color(n.cor)
                : n.bloqueio ? new Color(0.55f, 0.8f, 1f, 1f) : new Color(1f, 0f, 0f, 1f); // vermelho puro
            cor.a = Math.max(0f, alfa);
            font.setColor(cor);
            // Critico: fonte ~2px maior (e o "!" no texto).
            if (n.critico) font.getData().setScale(NOME_ESCALA_BASE * 1.3f);
            font.draw(batch, n.texto, x, y);
            if (n.critico) font.getData().setScale(NOME_ESCALA_BASE);
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
        // Nome na cor da vida (igual os mobs).
        float pct = 1f;
        if (j == local) {
            pct = hud.hpMax() > 0f ? hud.hpAtual() / hud.hpMax() : 1f;
        } else {
            float[] vida = vidaRemotos.get(j.nome);
            if (vida != null && vida[1] > 0f) pct = vida[0] / vida[1];
        }
        Color anterior = new Color(font.getColor());
        font.setColor(corDaVida(pct));
        font.draw(batch, nomeVisivel, nomeX, nomeY);
        font.setColor(anterior);
    }

    private void emitirCanalChat(String evento, String canal) {
        socket.emitRaw(evento, GameSocket.obj(jw -> jw.set("channel", canal)));
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
    public void resume() {
        // Android pode recriar o contexto GL ao voltar: a textura do minimapa some.
        if (pinturaMiniMapa != null) pinturaMiniMapa.recriar();
    }

    @Override
    public void dispose() {
        mapa.dispose();
        batch.dispose();
        font.dispose();
        fonteDestaque.dispose();
        pixelColisao.dispose();
        pixelBranco.dispose();
        hud.dispose();
        if (miniMapa != null) miniMapa.dispose();
        if (pinturaMiniMapa != null) pinturaMiniMapa.dispose();
        if (mapaGrande != null) mapaGrande.dispose();
        if (texBolinha != null) texBolinha.dispose();
        uiStage.dispose();
        skin.dispose();
        atlas.dispose();
        if(iluminacao != null) iluminacao.dispose();
    }
}