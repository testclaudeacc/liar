package com.teste.game.rede;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Porta de start_screen.gd::save_session()/load_session() - usa Preferences
 * (SharedPreferences no Android, arquivo local no desktop). Guarda o TOKEN de
 * sessao que o servidor devolve no login (nunca mais a senha em texto puro:
 * qualquer um com acesso ao arquivo/aparelho lia a senha da conta). */
public class Sessao {

    private static final String NOME = "inverted_realms_session";

    public static void salvar(int userId, String email, String token) {
        Preferences prefs = Gdx.app.getPreferences(NOME);
        prefs.putInteger("user_id", userId);
        prefs.putString("email", email);
        prefs.putString("token", token == null ? "" : token);
        prefs.remove("password"); // versoes antigas guardavam a senha aqui
        prefs.flush();
    }

    public static void limpar() {
        Gdx.app.getPreferences(NOME).clear();
        Gdx.app.getPreferences(NOME).flush();
    }

    public static int userIdSalvo() {
        return Gdx.app.getPreferences(NOME).getInteger("user_id", -1);
    }

    public static String emailSalvo() {
        return Gdx.app.getPreferences(NOME).getString("email", "");
    }

    public static String tokenSalvo() {
        return Gdx.app.getPreferences(NOME).getString("token", "");
    }
}
