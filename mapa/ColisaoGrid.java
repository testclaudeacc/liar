package com.teste.game.mapa;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Base64Coder;
import com.teste.game.entidades.Jogador;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Porta de servidor.py::eh_parede()/borda_bloqueada() - mesma grade de
 * colisao que o servidor usa (bit=1 significa parede), agora RASTERIZADA a
 * partir dos hitboxes lidos do .tsx/Tile Collision Editor (MapaPropriedades)
 * em vez de um colisao.json pre-calculado do Godot. A grade guarda
 * coordenadas em SQM (Jogador.TILE, mesmo valor que server/servidor.py::TILE)
 * no espaco "cru" (ConversorCoordenadas.mundoParaRawX/Y) - por isso todo
 * pixel do mundo libGDX precisa ser convertido de volta pra essa origem
 * antes de consultar a grade (ver indiceX/indiceY).
 *
 * bitsLeste/bitsBaixo (barreira fina de 1 borda, sem tornar a celula inteira
 * solida - ex: grade/corrimao) vem das BordaFina classificadas em
 * MapaPropriedades (forma fina do Tile Collision Editor - um lado <=30% do
 * tile, o oposto cobrindo >=60%) - ver marcarBordaFina.
 */
public class ColisaoGrid {

    private static final int SQM = (int) Jogador.TILE;
    private static final int PADDING_SQM = 2;

    private final int x0, y0, w, h;
    private final byte[] bits, bitsLeste, bitsBaixo;
    private final ConversorCoordenadas conversor;

    public ColisaoGrid(List<Rectangle> hitboxesMundo, List<MapaPropriedades.BordaFina> bordasFinas, ConversorCoordenadas conversor) {
        this.conversor = conversor;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        // Hitbox em espaco de mundo -> espaco "cru": o flip de Y inverte qual
        // canto vira min/max, por isso testa os 2 cantos verticais em vez de
        // assumir que y continua sendo o menor.
        Rectangle[] cru = new Rectangle[hitboxesMundo.size()];
        for (int i = 0; i < hitboxesMundo.size(); i++) {
            Rectangle r = hitboxesMundo.get(i);
            float rawX0 = conversor.mundoParaRawX(r.x);
            float rawX1 = conversor.mundoParaRawX(r.x + r.width);
            float rawY0 = conversor.mundoParaRawY(r.y);
            float rawY1 = conversor.mundoParaRawY(r.y + r.height);
            float rawXMin = Math.min(rawX0, rawX1), rawXMax = Math.max(rawX0, rawX1);
            float rawYMin = Math.min(rawY0, rawY1), rawYMax = Math.max(rawY0, rawY1);
            cru[i] = new Rectangle(rawXMin, rawYMin, rawXMax - rawXMin, rawYMax - rawYMin);
            minX = Math.min(minX, rawXMin);
            minY = Math.min(minY, rawYMin);
            maxX = Math.max(maxX, rawXMax);
            maxY = Math.max(maxY, rawYMax);
        }
        for (MapaPropriedades.BordaFina bf : bordasFinas) {
            float rawX0 = conversor.mundoParaRawX(bf.worldCellX);
            float rawX1 = conversor.mundoParaRawX(bf.worldCellX + SQM);
            float rawY0 = conversor.mundoParaRawY(bf.worldCellY);
            float rawY1 = conversor.mundoParaRawY(bf.worldCellY + SQM);
            minX = Math.min(minX, Math.min(rawX0, rawX1));
            minY = Math.min(minY, Math.min(rawY0, rawY1));
            maxX = Math.max(maxX, Math.max(rawX0, rawX1));
            maxY = Math.max(maxY, Math.max(rawY0, rawY1));
        }

        if (cru.length == 0 && bordasFinas.isEmpty()) {
            x0 = 0; y0 = 0; w = 0; h = 0;
            bits = new byte[0]; bitsLeste = new byte[0]; bitsBaixo = new byte[0];
            return;
        }

        minX -= PADDING_SQM * SQM; minY -= PADDING_SQM * SQM;
        maxX += PADDING_SQM * SQM; maxY += PADDING_SQM * SQM;
        x0 = (int) Math.floor(minX / SQM);
        y0 = (int) Math.floor(minY / SQM);
        w = (int) Math.ceil(maxX / SQM) - x0 + 1;
        h = (int) Math.ceil(maxY / SQM) - y0 + 1;

        int nBytes = (w * h + 7) / 8;
        bits = new byte[nBytes];
        bitsLeste = new byte[nBytes];
        bitsBaixo = new byte[nBytes];

        for (Rectangle r : cru) {
            // Intervalo [x, x+width) e' meio-aberto - usar floor() no fim
            // tambem (igual no inicio) faz um hitbox com borda EXATAMENTE em
            // cima de um multiplo de SQM (ex: tile inteiro 16x16 alinhado ao
            // grid) vazar 1 celula a mais em cada eixo (floor((x+SQM)/SQM) =
            // cxIni+1, nao cxIni). ceil(...)-1 fecha o intervalo certo.
            int cxIni = (int) Math.floor(r.x / SQM) - x0;
            int cxFim = (int) Math.ceil((r.x + r.width) / SQM) - 1 - x0;
            int cyIni = (int) Math.floor(r.y / SQM) - y0;
            int cyFim = (int) Math.ceil((r.y + r.height) / SQM) - 1 - y0;
            for (int cy = cyIni; cy <= cyFim; cy++) {
                if (cy < 0 || cy >= h) continue;
                for (int cx = cxIni; cx <= cxFim; cx++) {
                    if (cx < 0 || cx >= w) continue;
                    int i = cy * w + cx;
                    bits[i >> 3] |= (1 << (i & 7));
                }
            }
        }

        for (MapaPropriedades.BordaFina bf : bordasFinas) {
            marcarBordaFina(bf);
        }
    }

    /** Converte uma BordaFina (lado de 1 celula, em espaco de mundo) pro bit
     * de borda certo, sondando 2 pontos (1px pra dentro / 1px pra fora do
     * lado indicado) e comparando em que celula "crua" cada um cai -
     * reaproveita a MESMA conversao (conversor + floor/SQM) que
     * indiceX/indiceY ja usam pra consulta, em vez de derivar formula de
     * indice na mao (o flip de Y entre mundo e cru inverte qual lado vira
     * "leste"/"baixo" - sondar e' bem menos propenso a erro que deduzir). */
    private void marcarBordaFina(MapaPropriedades.BordaFina bf) {
        float meioX = bf.worldCellX + SQM / 2f;
        float meioY = bf.worldCellY + SQM / 2f;
        float dentroX = meioX, foraX = meioX, dentroY = meioY, foraY = meioY;
        switch (bf.lado) {
            case DIREITA:
                dentroX = bf.worldCellX + SQM - 1f;
                foraX = bf.worldCellX + SQM + 1f;
                break;
            case ESQUERDA:
                dentroX = bf.worldCellX + 1f;
                foraX = bf.worldCellX - 1f;
                break;
            case CIMA:
                dentroY = bf.worldCellY + SQM - 1f;
                foraY = bf.worldCellY + SQM + 1f;
                break;
            case BAIXO:
                dentroY = bf.worldCellY + 1f;
                foraY = bf.worldCellY - 1f;
                break;
        }
        int ixDentro = celulaCruaX(dentroX), ixFora = celulaCruaX(foraX);
        int iyDentro = celulaCruaY(dentroY), iyFora = celulaCruaY(foraY);
        if (ixDentro != ixFora) {
            marcarBit(bitsLeste, Math.min(ixDentro, ixFora) - x0, iyDentro - y0);
        } else if (iyDentro != iyFora) {
            marcarBit(bitsBaixo, ixDentro - x0, Math.min(iyDentro, iyFora) - y0);
        }
        // ixDentro==ixFora e iyDentro==iyFora simultaneamente nao deveria
        // acontecer (sonda de 2px sempre cruza 1 fronteira de SQM) - se
        // acontecer, e' uma celula de 1x1 praticamente, ignora silenciosamente.
    }

    private int celulaCruaX(float mundoX) {
        return (int) Math.floor(conversor.mundoParaRawX(mundoX) / (double) SQM);
    }

    private int celulaCruaY(float mundoY) {
        return (int) Math.floor(conversor.mundoParaRawY(mundoY) / (double) SQM);
    }

    private void marcarBit(byte[] plano, int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        int i = y * w + x;
        plano[i >> 3] |= (1 << (i & 7));
    }

    /** true = colide/bloqueado, igual servidor.py::eh_parede(). Fora da grade conta como parede. */
    public boolean ehParede(float mundoX, float mundoY) {
        int x = indiceX(mundoX);
        int y = indiceY(mundoY);
        return ehParedeIndice(x, y);
    }

    /** Mesma checagem, mas direto pelo indice da grade (0..w-1, 0..h-1) - usado
     * pelo overlay de debug pra varrer a grade inteira sem converter coordenada. */
    public boolean ehParedeIndice(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return true;
        int i = y * w + x;
        return (bits[i >> 3] & (1 << (i & 7))) != 0;
    }

    /** Igual servidor.py::borda_bloqueada() - bloqueia atravessar a borda
     * especifica entre 2 SQMs adjacentes (dx/dy = +-1 em UM eixo so), sem
     * exigir que nenhum dos dois seja solido. Nao inclui a checagem de
     * ehParede do destino - quem chama (processarEntrada) ja faz as duas. */
    public boolean movimentoBloqueado(float origemX, float origemY, float destinoX, float destinoY) {
        int ox = indiceX(origemX), oy = indiceY(origemY);
        int dx = indiceX(destinoX), dy = indiceY(destinoY);
        int deltaX = dx - ox, deltaY = dy - oy;
        if (deltaX == 1) return bordaLesteIndice(ox, oy);
        if (deltaX == -1) return bordaLesteIndice(dx, dy);
        if (deltaY == 1) return bordaBaixoIndice(ox, oy);
        if (deltaY == -1) return bordaBaixoIndice(dx, dy);
        return false; // mesma celula (nao deveria acontecer - move e' sempre 1 SQM)
    }

    /** Direto pelo indice da grade - usado pelo overlay de debug. */
    public boolean bordaLesteIndice(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        int i = y * w + x;
        return (bitsLeste[i >> 3] & (1 << (i & 7))) != 0;
    }

    /** Direto pelo indice da grade - usado pelo overlay de debug. */
    public boolean bordaBaixoIndice(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        int i = y * w + x;
        return (bitsBaixo[i >> 3] & (1 << (i & 7))) != 0;
    }

    private int indiceX(float mundoX) {
        float rawX = conversor.mundoParaRawX(mundoX);
        return (int) Math.floor(rawX / (double) SQM) - x0;
    }

    private int indiceY(float mundoY) {
        float rawY = conversor.mundoParaRawY(mundoY);
        // mundoY chega aqui como a ancora dos PES (fundo do tile, ver
        // Jogador.snapBaseYCru) - ou seja, sempre cai EXATAMENTE num multiplo
        // de SQM (fronteira entre 2 tiles). Sem o -1 (mesma sacada de
        // player.gd::snap_to_tile_center) o floor jogaria pro tile de baixo
        // por engano.
        return (int) Math.floor((rawY - 1) / (double) SQM) - y0;
    }

    public int x0() { return x0; }
    public int y0() { return y0; }
    public int w() { return w; }
    public int h() { return h; }

    /** Payload pro evento de socket "map_grid" (servidor.py::handle_map_grid) -
     * mesma grade que o Godot manda hoje, so' que calculada a partir do .tsx. */
    public String bitsBase64() { return new String(Base64Coder.encode(bits)); }
    public String bitsLesteBase64() { return new String(Base64Coder.encode(bitsLeste)); }
    public String bitsBaixoBase64() { return new String(Base64Coder.encode(bitsBaixo)); }

    /** Identificador de versao dessa grade (servidor.py::handle_register_map
     * compara com o que ja tem cacheado e so' pede a grade de novo via
     * "need_map_grid" quando esse valor muda) - MD5 de x0/y0/w/h + os 3
     * planos de bits. Nao precisa bater com o hash que o Godot calcula (cada
     * client tem sua propria nocao de versao do MESMO map id) - so' precisa
     * ser estavel enquanto a colisao nao mudar. */
    public String fingerprint() {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            ByteBuffer cabecalho = ByteBuffer.allocate(16);
            cabecalho.putInt(x0).putInt(y0).putInt(w).putInt(h);
            md.update(cabecalho.array());
            md.update(bits);
            md.update(bitsLeste);
            md.update(bitsBaixo);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
