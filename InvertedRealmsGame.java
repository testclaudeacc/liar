package com.teste.game;

import com.badlogic.gdx.Game;
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
        setScreen(new LoadingScreen(() -> {}));
        socket = new GameSocket();
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
                        w.set("name", personagem.name);
                        w.object("skins");
                        w.pop();
                    });
                    socket.emitRaw("join_game", payload);
                }));
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

    @Override
    public void dispose() {
        if (socket != null) socket.disconnect();
        super.dispose();
    }
}
