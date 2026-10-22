package com.teste.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.teste.game.rede.CharacterData;
import com.teste.game.rede.GameSocket;
import com.teste.game.telas.AuthScreen;
import com.teste.game.telas.LoadingScreen;
import com.teste.game.telas.WorldScreen;

/**
 * Ponto de entrada real do jogo (fora dos testes de FPS/mapa isolados): liga
 * a tela de login/selecao de personagem (AuthScreen) na conexao de verdade
 * com o servidor (GameSocket) e entao no mundo (WorldScreen). Ver
 * network_manager.gd::connect_to_server()/_send_join_game() - mesmo payload
 * de join, so que ainda sem sincronizar posicao/skins de verdade (proximo
 * passo da migracao).
 */
public class InvertedRealmsGame extends Game {

    private GameSocket socket;
    // true so' quando NOS mesmos chamamos socket.disconnect() de proposito
    // (ex: botao Exit do Settings) - onClose() do WebSocket dispara
    // onDisconnected() em QUALQUER fechamento, incluindo esse intencional, e
    // sem essa flag a tela de erro "Server shutdown" apareceria tambem
    // depois de um Exit normal (ver onDisconnected abaixo).
    private boolean desconexaoEsperada = false;

    @Override
    public void create() {
        // 60 fixo em qualquer plataforma - tudo acima disso e' desnecessario
        // (a pedido do usuario). Graphics.setForegroundFPS() e' cross-platform
        // (desktop LWJGL3 e Android), nao precisa configurar em cada launcher.
        Gdx.graphics.setForegroundFPS(60);
        setScreen(new AuthScreen(this::entrarNoMundo));
    }

    @Override
    public void render() {
        long inicioFrameNs = System.nanoTime(); // logo depois do vsync anterior
        // F11 ou Alt+Enter alterna fullscreen exclusivo <-> janela, do jeito
        // que o usuario pediu - checado aqui (nao dentro de uma Screen
        // especifica) pra funcionar em qualquer tela do jogo. Gdx.input aqui
        // e' seguro fora do thread de render, sempre GL thread neste ponto.
        boolean altEnter = (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT))
            && Gdx.input.isKeyJustPressed(Input.Keys.ENTER);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11) || altEnter) {
            alternarFullscreen();
        }
        super.render();
        limitarFpsCelular(inicioFrameNs);
    }

    // No Android o setForegroundFPS(60) nao faz nada (o jogo roda no ritmo
    // da tela: 60/90/120 Hz). O limite antigo dormia ate' completar 16.7 ms
    // contado pelo relogio do jogo - so' que a troca de quadro espera o vsync
    // da tela, e qualquer desencontro entre os dois relogios fazia o frame
    // perder o vsync: 60 Hz virava ~50 fps (e com o frame um pouco mais pesado,
    // 30). Agora o limite segue o vsync: pula vsyncs inteiros so' quando a tela
    // e' de 120 Hz ou mais (120 -> 60, 144 -> 72); 60 e 90 Hz rodam no ritmo da
    // tela, sem dormir nada.
    private int hzTela = 60, framesAteLerHz = 0;

    private void limitarFpsCelular(long inicioFrameNs) {
        Application.ApplicationType tipo = Gdx.app.getType();
        if (tipo != Application.ApplicationType.Android && tipo != Application.ApplicationType.iOS) return;
        // Hz da tela: le de tempos em tempos (getDisplayMode cria objeto - nao
        // a cada frame; muda quando o celular troca 60 <-> 120 sozinho).
        if (--framesAteLerHz <= 0) {
            framesAteLerHz = 120;
            try { hzTela = Math.max(30, Gdx.graphics.getDisplayMode().refreshRate); } catch (RuntimeException ignorado) { }
        }
        int hz = hzTela;
        int pular = Math.max(1, hz / 60); // quantos vsyncs por frame
        if (pular <= 1) return;
        // O frame comecou logo depois de um vsync: dorme ate' passar do(s)
        // vsync(s) que vamos pular (+ meio vsync de folga) e a troca de quadro
        // encaixa sozinha no vsync certo. Frame pesado (ja' passou): nao dorme.
        long vsyncNs = 1_000_000_000L / hz;
        long acordar = inicioFrameNs + (pular - 1) * vsyncNs + vsyncNs / 2L;
        long falta = acordar - System.nanoTime();
        if (falta > 0L && falta < 50_000_000L) {
            try {
                Thread.sleep(falta / 1_000_000L, (int) (falta % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Mesmo toggle do F11/Alt+Enter, exposto pra tela poder chamar direto de
     * um botao (WorldScreen::painelOptions - "Fullscreen"). */
    public static void alternarFullscreen() {
        if (Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setWindowedMode(1280, 720);
        } else {
            Graphics.DisplayMode modo = Gdx.graphics.getDisplayMode();
            Gdx.graphics.setFullscreenMode(modo);
        }
    }

    private void entrarNoMundo(int userId, CharacterData personagem) {
        desconexaoEsperada = false;
        // Mostra "Loading..." JA' (antes de mandar conectar) - sem isso a
        // tela de selecao de personagem ficava parada/"congelada" durante o
        // tempo de resposta do servidor, exatamente o freeze que o usuario
        // reportou (queria uma transicao igual o fade+loading do Global.gd
        // do jogo Godot, ver LoadingScreen).
        // Ja' comeca a carregar mapa/atlas enquanto conecta (bolinhas animando).
        setScreen(new LoadingScreen(() -> {}, true));
        socket = new GameSocket();
        // Outra pessoa entrou nesta mesma conta: o servidor manda isso antes
        // de derrubar a conexao - volta pro menu com o aviso certo (em vez de
        // "Server shutdown").
        socket.on("force_disconnect", (nomeEvt, data) -> {
            desconexaoEsperada = true;
            socket.disconnect();
            String motivo = data != null ? data.getString("reason", "") : "";
            if (motivo.isEmpty()) motivo = "Someone logged in your account.";
            setScreen(new AuthScreen(InvertedRealmsGame.this::entrarNoMundo, motivo));
        });
        // /class do admin: o servidor ja' trocou e salvou a classe - entra de
        // novo no mesmo personagem (a classe e' fixa na WorldScreen). Espera
        // um pouco pro save da fila chegar no banco antes do join.
        socket.on("class_changed", (nomeEvt, data) -> {
            if (data == null) return;
            desconexaoEsperada = true;
            socket.disconnect();
            personagem.className = data.getString("class", personagem.className);
            setScreen(new LoadingScreen(() -> {}));
            com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task() {
                @Override public void run() { entrarNoMundo(userId, personagem); }
            }, 1.5f);
        });
        socket.setConnectionListener(new GameSocket.ConnectionListener() {
            @Override
            public void onConnected() {
                Gdx.app.log("InvertedRealmsGame", "conectado ao servidor, entrando no mundo");
                // 2a LoadingScreen: a construcao de WorldScreen em si
                // (texturas/fontes/TiledMap, tudo sincrono) e' o trecho mais
                // pesado - joga ela pro callback do LoadingScreen (so' roda
                // depois do "Loading..." realmente aparecer na tela, ver
                // LoadingScreen) em vez de chamar direto aqui.
                setScreen(new LoadingScreen(() -> {
                    WorldScreen mundo = new WorldScreen(socket, personagem.name, personagem.className, () -> {
                        desconexaoEsperada = true;
                        socket.disconnect();
                        // O botao "Exit" do Settings so' volta pra tela inicial -
                        // NAO desloga (sessao salva continua valida, AuthScreen
                        // usa Sessao.userIdSalvo() pra pular direto pro
                        // char-select sem pedir login de novo).
                        setScreen(new AuthScreen(InvertedRealmsGame.this::entrarNoMundo));
                    });
                    mundo.definirCallbackSincronizacaoInicial(() -> setScreen(mundo));
                    String payload = GameSocket.obj(w -> {
                        w.set("user_id", userId);
                        // Prova de login (servidor.py::token_valido) - sem
                        // ele o servidor recusa entrar no personagem.
                        w.set("token", com.teste.game.rede.Sessao.tokenSalvo());
                        w.set("name", personagem.name);
                        w.object("skins");
                        w.pop();
                    });
                    socket.emitRaw("join_game", payload);
                }, true)); // espera a Precarga (mapa/atlas) antes de montar o mundo
            }

            @Override
            public void onDisconnected() {
                Gdx.app.log("InvertedRealmsGame", "desconectado do servidor");
                // Ja' estavamos saindo por conta propria (Exit do Settings) -
                // a troca de tela ja' foi feita la, nao mostra erro nenhum.
                if (desconexaoEsperada) return;
                // Qualquer outra desconexao (servidor caiu, rede caiu, etc) -
                // chuta o jogador de volta pro menu com aviso, em vez de
                // deixar ele preso numa WorldScreen que nunca mais vai
                // receber update nenhum (o bug reportado: "atualmente ao
                // desligar nada acontece").
                setScreen(new AuthScreen(InvertedRealmsGame.this::entrarNoMundo, "Server shutdown"));
            }
        });

        socket.connect(null);
    }

    // Celular: o jogo em segundo plano por mais de 10 s (so' dentro do mundo)
    // derruba a conexao - ninguem fica parado no mapa com o app minimizado.
    // Na volta, a tela inicial mostra "Disconnected".
    private static final long SEGUNDO_PLANO_MAX_MS = 10_000L;
    private java.util.Timer timerSegundoPlano;
    private volatile boolean caiuEmSegundoPlano = false;

    private static boolean celular() {
        Application.ApplicationType tipo = Gdx.app.getType();
        return tipo == Application.ApplicationType.Android || tipo == Application.ApplicationType.iOS;
    }

    @Override
    public void pause() {
        super.pause();
        if (!celular() || !(getScreen() instanceof WorldScreen) || socket == null || !socket.isConnected()) return;
        cancelarTimerSegundoPlano();
        final GameSocket alvo = socket;
        timerSegundoPlano = new java.util.Timer("segundo-plano", true);
        timerSegundoPlano.schedule(new java.util.TimerTask() {
            @Override public void run() {
                if (alvo != socket || !alvo.isConnected()) return;
                caiuEmSegundoPlano = true;
                desconexaoEsperada = true;
                alvo.disconnect();
            }
        }, SEGUNDO_PLANO_MAX_MS);
    }

    @Override
    public void resume() {
        cancelarTimerSegundoPlano();
        super.resume();
        if (caiuEmSegundoPlano) {
            caiuEmSegundoPlano = false;
            setScreen(new AuthScreen(InvertedRealmsGame.this::entrarNoMundo, "Disconnected"));
        }
    }

    private void cancelarTimerSegundoPlano() {
        if (timerSegundoPlano != null) {
            timerSegundoPlano.cancel();
            timerSegundoPlano = null;
        }
    }

    @Override
    public void dispose() {
        cancelarTimerSegundoPlano();
        if (socket != null) socket.disconnect();
        super.dispose();
    }
}
