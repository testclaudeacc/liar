package com.teste.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

/**
 * Simula a carga visual de mobs/outros players se movendo pela tela -
 * proposito unico: dar ao teste de FPS do chat uma carga de fundo parecida
 * com o jogo de verdade, em vez de uma tela vazia (que rodaria rapido em
 * qualquer motor e nao provaria nada sobre performance sob carga real).
 */
public class MundoSimulado {

    private static final int QUANTIDADE_ENTIDADES = 40;

    private final Texture textura;
    private final float[] x = new float[QUANTIDADE_ENTIDADES];
    private final float[] y = new float[QUANTIDADE_ENTIDADES];
    private final float[] velX = new float[QUANTIDADE_ENTIDADES];
    private final float[] velY = new float[QUANTIDADE_ENTIDADES];
    private final Color[] cores = new Color[QUANTIDADE_ENTIDADES];

    private final int largura;
    private final int altura;

    public MundoSimulado(Texture texturaEntidade, int largura, int altura) {
        this.textura = texturaEntidade;
        this.largura = largura;
        this.altura = altura;
        for (int i = 0; i < QUANTIDADE_ENTIDADES; i++) {
            x[i] = MathUtils.random(0, largura - 32);
            y[i] = MathUtils.random(0, altura - 32);
            velX[i] = MathUtils.random(-80f, 80f);
            velY[i] = MathUtils.random(-80f, 80f);
            cores[i] = new Color(MathUtils.random(0.3f, 1f), MathUtils.random(0.3f, 1f), MathUtils.random(0.3f, 1f), 1f);
        }
    }

    public void atualizar(float delta) {
        for (int i = 0; i < QUANTIDADE_ENTIDADES; i++) {
            x[i] += velX[i] * delta;
            y[i] += velY[i] * delta;
            if (x[i] < 0 || x[i] > largura - 32) velX[i] *= -1;
            if (y[i] < 0 || y[i] > altura - 32) velY[i] *= -1;
        }
    }

    public void desenhar(SpriteBatch batch) {
        for (int i = 0; i < QUANTIDADE_ENTIDADES; i++) {
            batch.setColor(cores[i]);
            batch.draw(textura, x[i], y[i], 32, 32);
        }
        batch.setColor(Color.WHITE);
    }
}
