package com.teste.game.rede;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/** Espelha os campos que /get_characters devolve (ver start_screen.gd::_on_auth_request_request_completed, caso GET_CHARS). */
public class CharacterData {
    public String name = "Unknown";
    public int level = 1;
    public String className = "Knight";
    public int floor = 1;
    public long exp = 0;
    public int timePlayed = 0;
    public JsonValue skills;
    public JsonValue skins;

    public static CharacterData deParsed(JsonValue c) {
        CharacterData d = new CharacterData();
        d.name = c.getString("name", "Unknown");
        d.level = c.getInt("level", 1);
        d.className = c.has("class_name") ? c.getString("class_name") : c.getString("class", "Knight");
        d.floor = c.getInt("floor", 1);
        d.exp = c.getLong("exp", 0);
        d.timePlayed = c.getInt("time_played", 0);
        d.skills = comoObjeto(c.get("skills"));
        d.skins = comoObjeto(c.get("skins"));
        return d;
    }

    // O servidor as vezes manda "skills"/"skins" como string JSON em vez de
    // objeto (mesma defesa que start_screen.gd faz do lado Godot).
    private static JsonValue comoObjeto(JsonValue valor) {
        if (valor == null) return null;
        if (valor.isObject()) return valor;
        if (valor.isString()) {
            try {
                return new JsonReader().parse(valor.asString());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public int skill(String key, int padrao) {
        if (skills == null || !skills.isObject()) return padrao;
        return skills.getInt(key, padrao);
    }
}
