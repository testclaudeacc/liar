package com.teste.game.entidades;

/**
 * Porta de player.gd/OtherPlayer.gd: movimento em grade de SQM, um passo por
 * vez, igual o Godot (ver player.gd::_move_in_direction - step_time =
 * 1/walkspeed_sqm_sec). A animacao de andar e' sincronizada ao PROGRESSO do
 * passo (ver progresso()), nao a um relogio proprio - e' exatamente o que
 * player.gd faz ao tocar a walk_* animation com anim_speed_scale =
 * anim_length/step_time e sempre dar seek(0.0, true) no inicio de cada passo:
 * o ciclo de frames sempre cabe perfeitamente dentro de 1 passo, nunca
 * desalinha nem "sobra" frame tocando depois que o passo termina.
 *
 * TILE = 16 (SQM = tile visual de verdade do World.tmx, 1:1) - mudanca
 * coordenada com server/servidor.py::TILE (so' esse client em migracao;
 * jogo/*.gd do Godot fica para tras de proposito, nao e' mais o alvo de
 * sincronia). Qualquer client (Godot OU libGDX) que ainda rodar contra esse
 * servidor com TILE=32 no proprio codigo vai dessincronizar.
 */
public class Jogador {

    public static final float TILE = 16f;
    private static final float WALKSPEED_SQM_SEC = 2.2f;
    private static final float TEMPO_PASSO = 1f / WALKSPEED_SQM_SEC;

    public final String nome;
    public final String classe;
    public float x, y;
    public String direcao = "down";
    public boolean movendo = false;

    private float origemX, origemY, destinoX, destinoY, progresso;
    private float duracao = TEMPO_PASSO;

    // Player remoto: os passos chegam do servidor em pacotes (10x/s), entao
    // chegam "tremidos" (ate' ~0.1s antes/depois). Tocar cada um na hora que
    // chega fazia o boneco dar uma paradinha quando o proximo atrasava. Agora
    // os passos entram numa fila e tocam colados; o 1o passo depois de parado
    // dura BUFFER_REMOTO a mais, o que cria uma folga pra absorver o atraso.
    private static final float BUFFER_REMOTO = 0.12f;
    private static final int FILA_MAX = 4; // atrasou demais: pula pro ultimo
    private final java.util.ArrayDeque<Object[]> fila = new java.util.ArrayDeque<>();

    public Jogador(String nome, String classe, float x, float y) {
        this.nome = nome;
        this.classe = classe;
        this.x = x;
        this.y = y;
    }

    /** Porta de player.gd::snap_to_tile_center - X centraliza no tile, Y ancora
     * no FUNDO do tile (pes do personagem, nao o centro) - e desse mesmo ponto
     * que o Godot dispara o raycast de colisao. Opera no espaco de coordenada
     * CRU do Godot (antes de converter pro mundo libGDX via ConversorCoordenadas). */
    public static float snapCentroXCru(float rawX) {
        return (float) Math.floor(rawX / TILE) * TILE + (TILE / 2f);
    }

    public static float snapBaseYCru(float rawY) {
        return (float) Math.floor((rawY - 1f) / TILE) * TILE + TILE;
    }

    /** Movimento local: da um passo de exatamente 1 tile na direcao dada. */
    public void iniciarPasso(float dx, float dy, String direcao) {
        this.direcao = direcao;
        origemX = x; origemY = y;
        destinoX = x + dx; destinoY = y + dy;
        progresso = 0f;
        duracao = TEMPO_PASSO;
        movendo = true;
    }

    /** Player remoto: o servidor manda a posicao alvo pronta (evento "m") -
     * tween ate la na mesma duracao de um passo, em vez de ficar teleportando. */
    public void definirAlvo(float alvoX, float alvoY, String direcao) {
        if (movendo || !fila.isEmpty()) {
            fila.add(new Object[]{alvoX, alvoY, direcao});
            if (fila.size() > FILA_MAX) {
                Object[] ultimo = fila.peekLast();
                posicionar((Float) ultimo[0], (Float) ultimo[1], (String) ultimo[2]);
            }
            return;
        }
        iniciarAlvo(alvoX, alvoY, direcao, TEMPO_PASSO + BUFFER_REMOTO);
    }

    /** Vai direto pra posicao (sem passo), descartando a fila. */
    public void posicionar(float nx, float ny, String direcao) {
        fila.clear();
        x = nx; y = ny;
        movendo = false;
        progresso = 1f;
        this.direcao = direcao;
    }

    private void iniciarAlvo(float alvoX, float alvoY, String direcao, float dur) {
        this.direcao = direcao;
        if (alvoX == x && alvoY == y) { // so' virou pro lado
            movendo = false;
            return;
        }
        origemX = x; origemY = y;
        destinoX = alvoX; destinoY = alvoY;
        progresso = 0f;
        duracao = dur;
        movendo = true;
    }

    /** Ultima posicao pedida (fim da fila, ou o passo atual). */
    public float ultimoAlvoX() { return !fila.isEmpty() ? (Float) fila.peekLast()[0] : movendo ? destinoX : x; }

    public float ultimoAlvoY() { return !fila.isEmpty() ? (Float) fila.peekLast()[1] : movendo ? destinoY : y; }

    /** Avanca o passo em andamento. Devolve o tempo (em segundos) que sobrou
     * de "delta" depois que o passo terminou dentro desta mesma chamada - o
     * chamador deve reaplicar essa sobra num passo seguinte (se houver) na
     * MESMA iteracao de frame, senao esse tempo simplesmente desaparece e o
     * andar acumula atraso/gagueira a cada passo (1 frame de folga a cada SQM
     * cruzado, perceptivel andando continuo - achado comparando com o Tween
     * do Godot, que e' continuo e nunca perde tempo entre passos). */
    public float atualizar(float delta) {
        float restante = delta;
        while (true) {
            if (!movendo) {
                if (fila.isEmpty()) return restante;
                // Proximo passo da fila, colado no anterior. Com a fila
                // acumulando (atraso), anda um pouco mais rapido pra alcancar.
                Object[] prox = fila.poll();
                float dur = fila.size() >= 1 ? TEMPO_PASSO * 0.85f : TEMPO_PASSO;
                iniciarAlvo((Float) prox[0], (Float) prox[1], (String) prox[2], dur);
                continue;
            }
            progresso += restante / duracao;
            if (progresso >= 1f) {
                restante = (progresso - 1f) * duracao;
                x = destinoX;
                y = destinoY;
                progresso = 1f;
                movendo = false;
                if (fila.isEmpty()) return restante;
                continue;
            }
            x = origemX + (destinoX - origemX) * progresso;
            y = origemY + (destinoY - origemY) * progresso;
            return 0f;
        }
    }

    /** Fracao (0..1) do passo atual - usada pra escolher o frame de andar em
     * vez de um relogio proprio (ver comentario da classe). Fora de um passo
     * (parado) vale 1.0 - sem uso nesse caso, ja' que a animacao parada nao
     * depende disso. */
    public float progresso() { return progresso; }

    public float posicaoSalvarX() { return movendo ? destinoX : x; }

    public float posicaoSalvarY() { return movendo ? destinoY : y; }
}