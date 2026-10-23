package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traducao do jogo (Options > Language): English (padrao), Portugues, Espanol.
 *
 * A CHAVE e' o proprio texto em ingles: tr("Respawn") devolve "Renascer" em
 * portugues e "Respawn" se faltar traducao (nunca quebra). Valores no meio do
 * texto vao como {0}, {1}...: tr("Wait {0}s to use another scroll.", 12).
 *
 * Mensagens do servidor (que chegam prontas, em ingles) passam por
 * trServidor(): procura a frase exata e, se nao achar, os modelos com {0}
 * (viram uma expressao regular) - assim o servidor nao precisa mudar.
 *
 * Fica em ingles de proposito (universal, igual o Tibia): nomes de itens,
 * mobs, magias, palavras das magias e comandos do chat.
 *
 * Os textos ficam em i18n/textos.tsv (ingles, portugues, espanhol separados
 * por TAB); tools/gerar_idioma.py gera IdiomaTextos.java a partir dele.
 */
public final class Idioma {
    public static final String[] NOMES = {"English", "Portugu\u00eas", "Espa\u00f1ol"};
    private static final String[] CODIGOS = {"en", "pt", "es"};

    private static int atual = -1;
    private static Map<String, String>[] mapas;
    private static List<Object[]>[] modelos; // {Pattern, traducao}

    private Idioma() {}

    private static Preferences prefs() { return Gdx.app.getPreferences("inverted_realms_opcoes"); }

    /** 0 English, 1 Portugues, 2 Espanol. Primeira vez: o idioma do aparelho. */
    public static int atual() {
        if (atual < 0) {
            int salvo = -1;
            try { salvo = prefs().getInteger("language", -1); } catch (RuntimeException ignorado) { }
            if (salvo < 0) {
                String l = java.util.Locale.getDefault().getLanguage();
                salvo = "pt".equals(l) ? 1 : "es".equals(l) ? 2 : 0;
            }
            atual = Math.max(0, Math.min(2, salvo));
        }
        return atual;
    }

    public static void definir(int idioma) {
        atual = Math.max(0, Math.min(2, idioma));
        try { Preferences p = prefs(); p.putInteger("language", atual); p.flush(); } catch (RuntimeException ignorado) { }
    }

    public static String codigo() { return CODIGOS[atual()]; }

    @SuppressWarnings("unchecked")
    private static void carregar() {
        if (mapas != null) return;
        mapas = new Map[3];
        modelos = new List[3];
        for (int i = 0; i < 3; i++) { mapas[i] = new HashMap<>(); modelos[i] = new ArrayList<>(); }
        for (String bloco : IdiomaTextos.BLOCOS) {
            for (String linha : bloco.split("\n")) {
                if (linha.isEmpty() || linha.startsWith("#")) continue;
                String[] c = linha.split("\t", -1);
                if (c.length < 3 || c[0].isEmpty()) continue;
                c[0] = c[0].replace("\\n", "\n"); // "\n" no TSV = quebra de linha
                for (int i = 1; i <= 2; i++) {
                    c[i] = c[i].replace("\\n", "\n");
                    if (c[i].isEmpty()) continue;
                    mapas[i].put(c[0], c[i]);
                    if (c[0].contains("{0}")) modelos[i].add(new Object[]{modeloRegex(c[0]), c[i], indicesModelo(c[0])});
                }
            }
        }
    }

    /** Qual {n} e' cada grupo da regex, na ordem em que aparecem no ingles. */
    private static int[] indicesModelo(String en) {
        Matcher m = Pattern.compile("\\{(\\d)\\}").matcher(en);
        List<Integer> l = new ArrayList<>();
        while (m.find()) l.add(Integer.parseInt(m.group(1)));
        int[] r = new int[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    /** "Wait {0}s to use another scroll." -> ^\QWait \E(.+?)\Qs to use...\E$ */
    private static Pattern modeloRegex(String en) {
        StringBuilder sb = new StringBuilder("^");
        Matcher m = Pattern.compile("\\{\\d\\}").matcher(en);
        int fim = 0;
        while (m.find()) {
            if (m.start() > fim) sb.append(Pattern.quote(en.substring(fim, m.start())));
            sb.append("(.+?)");
            fim = m.end();
        }
        if (fim < en.length()) sb.append(Pattern.quote(en.substring(fim)));
        return Pattern.compile(sb.append('$').toString(), Pattern.DOTALL);
    }

    /** Texto do jogo no idioma escolhido (ingles se faltar traducao). */
    public static String tr(String en, Object... args) {
        if (en == null) return "";
        String t = en;
        if (atual() != 0) {
            carregar();
            String achado = mapas[atual].get(en);
            if (achado != null) t = achado;
        }
        return args.length == 0 ? t : preencher(t, args);
    }

    /** Mesma palavra em ingles com sentidos diferentes ("Back" = voltar ou
     * costas): chave propria no TSV, e em ingles mostra o texto en. */
    public static String trc(String chave, String en) {
        if (atual() == 0) return en;
        carregar();
        String achado = mapas[atual].get(chave);
        return achado != null ? achado : en;
    }

    private static String preencher(String t, Object... args) {
        for (int i = 0; i < args.length; i++) t = t.replace("{" + i + "}", String.valueOf(args[i]));
        return t;
    }

    /** Mensagem pronta do servidor (em ingles): traduz pela frase exata ou por
     * um modelo com {0}; sem traducao, devolve igual. */
    public static String trServidor(String texto) {
        if (texto == null || texto.isEmpty() || atual() == 0) return texto == null ? "" : texto;
        carregar();
        String exato = mapas[atual].get(texto);
        if (exato != null) return exato;
        for (Object[] mod : modelos[atual]) {
            Matcher m = ((Pattern) mod[0]).matcher(texto);
            if (!m.matches()) continue;
            int[] indices = (int[]) mod[2];
            int maior = 0;
            for (int ix : indices) maior = Math.max(maior, ix + 1);
            Object[] args = new Object[maior];
            for (int g = 0; g < indices.length && g < m.groupCount(); g++) args[indices[g]] = m.group(g + 1);
            return preencher((String) mod[1], args);
        }
        return texto;
    }
}
