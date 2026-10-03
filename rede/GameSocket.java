package com.teste.game.rede;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.StringWriter;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Porta de network_manager.gd::connect_to_server()/_handle_network_message()
 * - o servidor fala Engine.IO v4 + Socket.IO v4 direto sobre um WebSocket cru
 * em `/socket.io/?EIO=4&transport=websocket` (sem handshake HTTP polling
 * antes, o Godot já conecta direto no transporte websocket e o server
 * aceita). Framing (ver protocolo Engine.IO):
 *   "0..."  server abriu a sessao -> respondemos "40" (Socket.IO CONNECT) e
 *           já mandamos join_game, igual o Godot faz
 *   "2"     ping do server -> respondemos "3" (pong)
 *   "42[...]" evento Socket.IO: array JSON [nome_evento, payload]
 */
public class GameSocket {

    public interface EventListener {
        void onEvent(String eventName, JsonValue data);
    }

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
    }

    private WebSocketClient client;
    private volatile boolean handshakeDone = false;
    private final Map<String, EventListener> listeners = new HashMap<>();
    private ConnectionListener connectionListener;
    private Runnable pendingJoin;

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void on(String eventName, EventListener listener) {
        listeners.put(eventName, listener);
    }

    public boolean isConnected() {
        return client != null && client.isOpen() && handshakeDone;
    }

    public void connect(Runnable sendJoinGame) {
        if (client != null && client.isOpen()) return;
        this.pendingJoin = sendJoinGame;
        handshakeDone = false;

        String wsUrl = ServerConfig.BASE_URL.replace("https://", "wss://").replace("http://", "ws://")
            + "/socket.io/?EIO=4&transport=websocket";

        client = new WebSocketClient(URI.create(wsUrl)) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                // conexao TCP/websocket abriu, mas o handshake Engine.IO
                // (pacote "0") ainda nao chegou - so tratamos como "pronto"
                // quando ele chegar, em onMessage.
            }

            @Override
            public void onMessage(String message) {
                handleMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                boolean estavaConectado = handshakeDone;
                handshakeDone = false;
                if (estavaConectado) {
                    Gdx.app.postRunnable(() -> {
                        if (connectionListener != null) connectionListener.onDisconnected();
                    });
                }
            }

            @Override
            public void onError(Exception ex) {
                Gdx.app.error("GameSocket", "erro no websocket", ex);
            }
        };
        client.connect();
    }

    public void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    private void handleMessage(String msg) {
        if (msg.startsWith("0")) {
            client.send("40");
            handshakeDone = true;
            Gdx.app.postRunnable(() -> {
                if (connectionListener != null) connectionListener.onConnected();
                if (pendingJoin != null) pendingJoin.run();
            });
        } else if (msg.equals("2") || msg.startsWith("2")) {
            client.send("3");
        } else if (msg.startsWith("42")) {
            String jsonStr = msg.substring(2);
            JsonValue parsed;
            try {
                parsed = new JsonReader().parse(jsonStr);
            } catch (Exception e) {
                return;
            }
            if (parsed == null || !parsed.isArray() || parsed.size == 0) return;
            String eventName = parsed.get(0).asString();
            JsonValue eventData = parsed.size > 1 ? parsed.get(1) : null;
            EventListener listener = listeners.get(eventName);
            if (listener != null) {
                Gdx.app.postRunnable(() -> listener.onEvent(eventName, eventData));
            }
        }
    }

    /** Emite um evento Socket.IO: "42" + JSON.stringify([nome, payload_json_cru]). */
    public void emitRaw(String eventName, String payloadJson) {
        if (!isConnected()) return;
        client.send("42[\"" + eventName + "\"," + payloadJson + "]");
    }

    public interface PayloadWriter {
        void write(JsonWriter w) throws java.io.IOException;
    }

    public static String obj(PayloadWriter writer) {
        StringWriter sw = new StringWriter();
        JsonWriter jw = new JsonWriter(sw);
        try {
            jw.object();
            writer.write(jw);
            jw.pop();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return sw.toString();
    }
}
