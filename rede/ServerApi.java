package com.teste.game.rede;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

import java.io.StringWriter;

/**
 * Porta dos endpoints HTTP que start_screen.gd chama (ver PROJECT_ARCHITECTURE.md
 * secao 1). Mesmos payloads/JSON que o cliente Godot ja manda - o servidor nao
 * muda, so o cliente.
 */
public class ServerApi {

    public interface ApiCallback {
        void onResponse(int statusCode, JsonValue body);
        void onFailure(String motivo);
    }

    public static void ping(ApiCallback callback) {
        get("/ping", callback);
    }

    public static void checkVersion(ApiCallback callback) {
        String body = json(w -> w.set("version", ServerConfig.CLIENT_VERSION));
        post("/check_version", body, callback);
    }

    public static void login(String email, String password, ApiCallback callback) {
        String body = json(w -> {
            w.set("email", email);
            w.set("password", password);
        });
        post("/login", body, callback);
    }

    public static void register(String email, String password, ApiCallback callback) {
        String body = json(w -> {
            w.set("email", email);
            w.set("password", password);
        });
        post("/register", body, callback);
    }

    public static void forgotPassword(String email, ApiCallback callback) {
        String body = json(w -> w.set("email", email));
        post("/forgot-password", body, callback);
    }

    public static void resetPassword(String email, String code, String newPassword, ApiCallback callback) {
        String body = json(w -> {
            w.set("email", email);
            w.set("code", code);
            w.set("new_password", newPassword);
        });
        post("/reset-password", body, callback);
    }

    public static void getCharacters(int userId, ApiCallback callback) {
        String body = json(w -> {
            w.set("user_id", userId);
            w.set("token", Sessao.tokenSalvo());
        });
        post("/get_characters", body, callback);
    }

    public static void createCharacter(int userId, String name, String className, ApiCallback callback) {
        String body = json(w -> {
            w.set("user_id", userId);
            w.set("name", name);
            w.set("class_name", className);
            w.set("token", Sessao.tokenSalvo());
        });
        post("/create_character", body, callback);
    }

    public static void deleteCharacter(int userId, String name, String password, ApiCallback callback) {
        String body = json(w -> {
            w.set("user_id", userId);
            w.set("name", name);
            w.set("password", password);
            w.set("token", Sessao.tokenSalvo());
        });
        post("/delete_character", body, callback);
    }

    private interface BodyWriter {
        void write(JsonWriter w) throws java.io.IOException;
    }

    private static String json(BodyWriter writer) {
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

    private static void get(String path, ApiCallback callback) {
        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.GET);
        request.setUrl(ServerConfig.BASE_URL + path);
        request.setTimeOut(15000);
        send(request, callback);
    }

    private static void post(String path, String jsonBody, ApiCallback callback) {
        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(ServerConfig.BASE_URL + path);
        request.setHeader("Content-Type", "application/json");
        request.setContent(jsonBody);
        request.setTimeOut(15000);
        send(request, callback);
    }

    private static void send(Net.HttpRequest request, ApiCallback callback) {
        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                int status = httpResponse.getStatus().getStatusCode();
                String raw = httpResponse.getResultAsString();
                JsonValue parsed = null;
                if (raw != null && !raw.isEmpty()) {
                    try {
                        parsed = new JsonReader().parse(raw);
                    } catch (Exception ignored) {
                        // resposta nao-JSON (ex: /ping so retorna 200 sem corpo) - ok
                    }
                }
                JsonValue finalParsed = parsed;
                runOnAppThread(() -> callback.onResponse(status, finalParsed));
            }

            @Override
            public void failed(Throwable t) {
                runOnAppThread(() -> callback.onFailure("Server is offline or unreachable."));
            }

            @Override
            public void cancelled() {
                runOnAppThread(() -> callback.onFailure("Request cancelled."));
            }
        });
    }

    private static void runOnAppThread(Runnable r) {
        Application app = Gdx.app;
        if (app != null) {
            app.postRunnable(r);
        } else {
            r.run();
        }
    }
}
