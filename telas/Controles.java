package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuracao de controles, salva so' no aparelho (Preferences: arquivo local
 * no PC, SharedPreferences no Android) - nao vai pro servidor.
 *
 * PC: tecla de cada acao (atalhos 1-9 da hotbar, abrir bag, abrir chat,
 * escrever no chat). Duas acoes nunca ficam com a mesma tecla: escolher uma
 * tecla ja' usada nao deixa (as duas aparecem em vermelho). Movimento (WASD/setas), ESC e C
 * (overlay de colisao) sao fixos e nao podem ser escolhidos.
 *
 * Celular: tamanho e posicao do joystick, e o layout dos botoes da hotbar
 * (BotaoMobile: 1 a 9 botoes, cada um = 1 slot, em ordem; tamanho e posicao
 * de cada um personalizados).
 */
public final class Controles {

    private static final String ARQUIVO = "inverted_realms_controles";

    /** Acoes configuraveis, na ordem em que aparecem na tela de Settings. */
    public static final Map<String, String> NOMES = new LinkedHashMap<>();
    private static final Map<String, Integer> PADRAO = new LinkedHashMap<>();
    static {
        for (int i = 1; i <= 9; i++) {
            NOMES.put("hotbar" + i, "Hotbar slot " + i);
            PADRAO.put("hotbar" + i, Input.Keys.NUM_0 + i);
        }
        NOMES.put("menu", "Open Menu");
        PADRAO.put("menu", Input.Keys.E);
        NOMES.put("bag", "Open Bag");
        PADRAO.put("bag", Input.Keys.B);
        NOMES.put("chat", "Open Chat");
        PADRAO.put("chat", Input.Keys.SPACE);
        NOMES.put("write", "Write");
        PADRAO.put("write", Input.Keys.ENTER);
    }

    private static final int[] RESERVADAS = {
        Input.Keys.W, Input.Keys.A, Input.Keys.S, Input.Keys.D,
        Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT,
        Input.Keys.ESCAPE, Input.Keys.C,
    };

    private static final Map<String, Integer> teclas = new LinkedHashMap<>();
    private static boolean carregado = false;

    /** Moving style: 8 direcoes (com diagonal) ou 4 (sem). */
    private static boolean oitoDirecoes = true;

    // Joystick (celular)
    public static final float JOYSTICK_ESCALA_MIN = 0.6f, JOYSTICK_ESCALA_MAX = 1.6f;
    private static float joystickEscala = 1f;
    private static float joystickX = -1f, joystickY = -1f; // -1 = posicao padrao

    private Controles() {}

    private static Preferences prefs() { return Gdx.app.getPreferences(ARQUIVO); }

    private static void carregar() {
        if (carregado) return;
        carregado = true;
        Preferences p = prefs();
        for (Map.Entry<String, Integer> e : PADRAO.entrySet()) {
            teclas.put(e.getKey(), p.getInteger("tecla_" + e.getKey(), e.getValue()));
        }
        // Arquivo velho/editado com tecla repetida ou reservada: volta pro padrao.
        java.util.Set<Integer> vistas = new java.util.HashSet<>();
        for (Map.Entry<String, Integer> e : teclas.entrySet()) {
            if (reservada(e.getValue()) || !vistas.add(e.getValue())) {
                teclas.clear();
                teclas.putAll(PADRAO);
                break;
            }
        }
        oitoDirecoes = p.getBoolean("oito_direcoes", true);
        joystickEscala = Math.max(JOYSTICK_ESCALA_MIN, Math.min(JOYSTICK_ESCALA_MAX, p.getFloat("joystick_escala", 1f)));
        joystickX = p.getFloat("joystick_x", -1f);
        joystickY = p.getFloat("joystick_y", -1f);
    }

    private static void salvar() {
        Preferences p = prefs();
        for (Map.Entry<String, Integer> e : teclas.entrySet()) p.putInteger("tecla_" + e.getKey(), e.getValue());
        p.putBoolean("oito_direcoes", oitoDirecoes);
        p.putFloat("joystick_escala", joystickEscala);
        p.putFloat("joystick_x", joystickX);
        p.putFloat("joystick_y", joystickY);
        p.flush();
    }

    public static int tecla(String acao) {
        carregar();
        Integer t = teclas.get(acao);
        return t != null ? t : -1;
    }

    /** Acao ligada a essa tecla, ou null. */
    public static String acaoDaTecla(int keycode) {
        carregar();
        for (Map.Entry<String, Integer> e : teclas.entrySet()) if (e.getValue() == keycode) return e.getKey();
        return null;
    }

    /** Slot da hotbar (0-8) ligado a essa tecla, ou -1. */
    public static int slotDaTecla(int keycode) {
        String acao = acaoDaTecla(keycode);
        if (acao == null || !acao.startsWith("hotbar")) return -1;
        return Integer.parseInt(acao.substring(6)) - 1;
    }

    public static boolean reservada(int keycode) {
        for (int r : RESERVADAS) if (r == keycode) return true;
        return false;
    }

    /** Liga a tecla na acao. Se outra acao ja' usa essa tecla, NAO muda nada
     * e devolve essa outra acao (o chamador mostra as duas em vermelho).
     * Tecla reservada: nao muda nada e devolve "". Deu certo: null. */
    public static String definir(String acao, int keycode) {
        carregar();
        if (reservada(keycode)) return "";
        String outra = acaoDaTecla(keycode);
        if (acao.equals(outra)) return null;
        if (outra != null) return outra;
        teclas.put(acao, keycode);
        salvar();
        return null;
    }

    public static void restaurarPadrao() {
        carregar();
        teclas.clear();
        teclas.putAll(PADRAO);
        salvar();
    }

    /** Nome curto da tecla pra mostrar (hotbar e Settings). */
    public static String nomeTecla(int keycode) {
        if (keycode < 0) return "-";
        if (keycode >= Input.Keys.NUM_0 && keycode <= Input.Keys.NUM_9) return String.valueOf(keycode - Input.Keys.NUM_0);
        if (keycode >= Input.Keys.NUMPAD_0 && keycode <= Input.Keys.NUMPAD_9) return "N" + (keycode - Input.Keys.NUMPAD_0);
        String nome = Input.Keys.toString(keycode);
        return nome != null ? nome : "?";
    }

    // ---- Moving style ----
    public static boolean oitoDirecoes() { carregar(); return oitoDirecoes; }

    public static void definirOitoDirecoes(boolean oito) {
        carregar();
        oitoDirecoes = oito;
        salvar();
    }

    // ---- Joystick ----
    public static float joystickEscala() { carregar(); return joystickEscala; }
    public static float joystickX() { carregar(); return joystickX; }
    public static float joystickY() { carregar(); return joystickY; }

    public static void definirJoystickEscala(float escala) {
        carregar();
        joystickEscala = Math.max(JOYSTICK_ESCALA_MIN, Math.min(JOYSTICK_ESCALA_MAX, escala));
        salvar();
    }

    public static void definirJoystickPosicao(float x, float y) {
        carregar();
        joystickX = x;
        joystickY = y;
        salvar();
    }

    // ---- Layout dos botoes da hotbar no celular ----

    /** Um botao da hotbar no celular: 1 atalho, tocar usa. Posicao = centro
     * (distancia da borda DIREITA, altura a partir de BAIXO), em unidades do stage. */
    public static final class BotaoMobile {
        public float x, y, tamanho;

        public BotaoMobile(float x, float y, float tamanho) {
            this.x = x; this.y = y; this.tamanho = tamanho;
        }

        BotaoMobile copia() { return new BotaoMobile(x, y, tamanho); }
    }

    public static final int MAX_BOTOES_MOBILE = 9;
    /** Tamanho dos botoes em % de TAM_BOTAO_PADRAO, igual o joystick (60%-160%). */
    public static final float TAM_BOTAO_PADRAO = 76f;
    public static final float TAM_BOTAO_MIN = TAM_BOTAO_PADRAO * JOYSTICK_ESCALA_MIN, TAM_BOTAO_MAX = TAM_BOTAO_PADRAO * JOYSTICK_ESCALA_MAX;
    private static java.util.List<BotaoMobile> layoutMobile = null;

    /** Layout padrao: 4 botoes em arco em volta do canto de baixo a direita. */
    public static java.util.List<BotaoMobile> layoutPadrao() {
        java.util.List<BotaoMobile> l = new java.util.ArrayList<>();
        for (float a : new float[]{100f, 125f, 150f, 175f}) {
            float rad = a * com.badlogic.gdx.math.MathUtils.degreesToRadians;
            l.add(new BotaoMobile(16f - 190f * com.badlogic.gdx.math.MathUtils.cos(rad),
                24f + 190f * com.badlogic.gdx.math.MathUtils.sin(rad), 76f));
        }
        return l;
    }

    /** Layout salvo (copia: quem edita chama salvarLayoutMobile no fim). */
    public static java.util.List<BotaoMobile> layoutMobile() {
        carregar();
        if (layoutMobile == null) {
            layoutMobile = lerLayout(prefs().getString("layout_mobile_fixo", ""));
            if (layoutMobile == null) layoutMobile = layoutPadrao();
        }
        java.util.List<BotaoMobile> copia = new java.util.ArrayList<>();
        for (BotaoMobile b : layoutMobile) copia.add(b.copia());
        return copia;
    }

    public static void salvarLayoutMobile(java.util.List<BotaoMobile> layout) {
        layoutMobile = new java.util.ArrayList<>();
        for (BotaoMobile b : layout) layoutMobile.add(b.copia());
        StringBuilder sb = new StringBuilder();
        for (BotaoMobile b : layoutMobile) {
            if (sb.length() > 0) sb.append(';');
            sb.append(b.x).append('|').append(b.y).append('|').append(b.tamanho);
        }
        Preferences p = prefs();
        p.putString("layout_mobile_fixo", sb.toString());
        p.flush();
    }

    /** Formato: "x|y|tam;x|y|tam;..." - invalido = null (padrao). */
    private static java.util.List<BotaoMobile> lerLayout(String texto) {
        if (texto == null || texto.isEmpty()) return null;
        try {
            java.util.List<BotaoMobile> l = new java.util.ArrayList<>();
            for (String parte : texto.split(";")) {
                String[] c = parte.split("\\|");
                if (c.length != 3) return null;
                l.add(new BotaoMobile(Float.parseFloat(c[0]), Float.parseFloat(c[1]),
                    Math.max(TAM_BOTAO_MIN, Math.min(TAM_BOTAO_MAX, Float.parseFloat(c[2])))));
            }
            return l.size() <= MAX_BOTOES_MOBILE ? l : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** No celular cada botao e' um slot (1o botao = slot 1...): e' de comida/pocao. */
    public static boolean slotFixoMobile(int slot) {
        return slot >= 0 && slot < layoutMobile().size();
    }

    public static void restaurarJoystick() {
        carregar();
        joystickEscala = 1f;
        joystickX = joystickY = -1f;
        salvar();
    }
}
