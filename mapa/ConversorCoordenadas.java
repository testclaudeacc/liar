package com.teste.game.mapa;

/**
 * Converte entre o pixel "cru" do Godot (o que o servidor guarda/manda -
 * pos_x/pos_y do banco, SpawnPoint em world.tscn, etc - origem/eixo Y
 * originais da cena) e o pixel do mundo libGDX (origem normalizada por
 * godot_map_to_tiled.py, eixo Y pra cima). Mesma conta usada tanto pra mandar
 * a posicao do jogador local pro servidor quanto pra entender a posicao que
 * o servidor manda de volta (sync_local_player, current_players, "m"/"l").
 */
public class ConversorCoordenadas {

    private final int offsetX16;
    private final int offsetY16;
    private final float alturaMapaPx;

    public ConversorCoordenadas(int offsetX16, int offsetY16, float alturaMapaPx) {
        this.offsetX16 = offsetX16;
        this.offsetY16 = offsetY16;
        this.alturaMapaPx = alturaMapaPx;
    }

    public float rawParaMundoX(float rawX) {
        return rawX - offsetX16 * 16f;
    }

    public float rawParaMundoY(float rawY) {
        return alturaMapaPx - rawY + offsetY16 * 16f;
    }

    public float mundoParaRawX(float mundoX) {
        return mundoX + offsetX16 * 16f;
    }

    public float mundoParaRawY(float mundoY) {
        return (alturaMapaPx - mundoY) + offsetY16 * 16f;
    }
}
